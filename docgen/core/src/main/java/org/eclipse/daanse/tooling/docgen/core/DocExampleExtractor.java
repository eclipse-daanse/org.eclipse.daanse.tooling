/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.tooling.docgen.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.tooling.docgen.api.model.DocExampleModel;
import org.eclipse.daanse.tooling.docgen.api.model.DocStepModel;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

public class DocExampleExtractor {

    private final List<Path> sourceDirectories;

    public DocExampleExtractor(List<Path> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    public List<DocExampleModel> extract() throws IOException {
        List<DocExampleModel> examples = new ArrayList<>();

        for (Path sourceDir : sourceDirectories) {
            if (!Files.isDirectory(sourceDir)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();

                for (Path javaFile : javaFiles) {
                    extractFromFile(javaFile).ifPresent(examples::add);
                }
            }
        }

        examples.sort(Comparator.comparingInt(DocExampleModel::order).thenComparing(DocExampleModel::title));
        return examples;
    }

    private Optional<DocExampleModel> extractFromFile(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        CompilationUnit cu = StaticJavaParser.parse(source);

        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration classDecl : classes) {
            Optional<AnnotationExpr> docExampleAnnotation = classDecl.getAnnotationByName("DocExample");
            if (docExampleAnnotation.isPresent()) {
                return Optional.of(extractExample(cu, classDecl, docExampleAnnotation.get()));
            }
        }
        return Optional.empty();
    }

    private DocExampleModel extractExample(CompilationUnit cu, ClassOrInterfaceDeclaration classDecl,
            AnnotationExpr annotation) {
        String id = getStringValue(annotation, "id", classDecl.getNameAsString());
        String title = getStringValue(annotation, "title", classDecl.getNameAsString());
        String description = getStringValue(annotation, "description", "");
        List<String> tags = getStringArrayValue(annotation, "tags");
        int order = getIntValue(annotation, "order", Integer.MAX_VALUE);
        String group = getStringValue(annotation, "group", "");
        boolean includeImports = getBooleanValue(annotation, "includeImports", false);

        String sourceClassName = classDecl.getNameAsString();
        String sourcePackage = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

        String imports = "";
        if (includeImports) {
            StringBuilder sb = new StringBuilder();
            for (ImportDeclaration imp : cu.getImports()) {
                sb.append(imp.toString().trim()).append('\n');
            }
            imports = sb.toString().stripTrailing();
        }

        List<DocStepModel> steps = extractSteps(classDecl);
        String classCode = CodeFormatter.dedent(extractClassBody(classDecl));

        if (id.isEmpty()) {
            id = sourceClassName;
        }

        return new DocExampleModel(id, title, description, tags, order, group, sourceClassName, sourcePackage, imports,
                classCode, steps);
    }

    private List<DocStepModel> extractSteps(ClassOrInterfaceDeclaration classDecl) {
        List<DocStepModel> steps = new ArrayList<>();

        for (MethodDeclaration method : classDecl.getMethods()) {
            Optional<AnnotationExpr> stepAnnotation = method.getAnnotationByName("DocStep");
            if (stepAnnotation.isPresent()) {
                steps.add(extractStep(method, stepAnnotation.get()));
            }
        }

        steps.sort(Comparator.comparingInt(DocStepModel::order));
        return steps;
    }

    private DocStepModel extractStep(MethodDeclaration method, AnnotationExpr stepAnnotation) {
        int order = getIntValue(stepAnnotation, "order", 0);
        String title = getStringValue(stepAnnotation, "title", method.getNameAsString());
        String description = getStringValue(stepAnnotation, "description", "");
        boolean includeDeclaration = getBooleanValue(stepAnnotation, "includeDeclaration", false);

        String code;
        if (includeDeclaration) {
            code = CodeFormatter.dedent(method.toString());
        } else {
            code = extractMethodBody(method);
        }

        String expectedOutput = "";
        Optional<AnnotationExpr> outputAnnotation = method.getAnnotationByName("DocOutput");
        if (outputAnnotation.isPresent()) {
            expectedOutput = getAnnotationSingleValue(outputAnnotation.get());
        }

        return new DocStepModel(order, title, description, code, expectedOutput);
    }

    private String extractMethodBody(MethodDeclaration method) {
        Optional<BlockStmt> body = method.getBody();
        if (body.isEmpty()) {
            return "";
        }

        String bodyStr = body.get().toString();
        if (bodyStr.startsWith("{") && bodyStr.endsWith("}")) {
            bodyStr = bodyStr.substring(1, bodyStr.length() - 1);
        }
        return CodeFormatter.dedent(bodyStr).strip();
    }

    private String extractClassBody(ClassOrInterfaceDeclaration classDecl) {
        String classStr = classDecl.toString();
        int braceStart = classStr.indexOf('{');
        int braceEnd = classStr.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return classStr.substring(braceStart + 1, braceEnd).strip();
        }
        return classStr;
    }

    private String getStringValue(AnnotationExpr annotation, String key, String defaultValue) {
        Expression expr = getMemberValue(annotation, key);
        if (expr == null) {
            return defaultValue;
        }
        return switch (expr) {
        case StringLiteralExpr s -> s.asString();
        default -> defaultValue;
        };
    }

    private int getIntValue(AnnotationExpr annotation, String key, int defaultValue) {
        Expression expr = getMemberValue(annotation, key);
        if (expr == null) {
            return defaultValue;
        }
        return switch (expr) {
        case IntegerLiteralExpr i -> i.asNumber().intValue();
        default -> defaultValue;
        };
    }

    private boolean getBooleanValue(AnnotationExpr annotation, String key, boolean defaultValue) {
        Expression expr = getMemberValue(annotation, key);
        if (expr == null) {
            return defaultValue;
        }
        return switch (expr) {
        case BooleanLiteralExpr b -> b.getValue();
        default -> defaultValue;
        };
    }

    private List<String> getStringArrayValue(AnnotationExpr annotation, String key) {
        Expression expr = getMemberValue(annotation, key);
        if (expr == null) {
            return List.of();
        }
        return switch (expr) {
        case ArrayInitializerExpr a -> a.getValues().stream().filter(v -> v instanceof StringLiteralExpr)
                .map(v -> ((StringLiteralExpr) v).asString()).toList();
        case StringLiteralExpr s -> List.of(s.asString());
        default -> List.of();
        };
    }

    private Expression getMemberValue(AnnotationExpr annotation, String key) {
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(key)) {
                    return pair.getValue();
                }
            }
        }
        return null;
    }

    private String getAnnotationSingleValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            Expression expr = single.getMemberValue();
            return switch (expr) {
            case StringLiteralExpr s -> s.asString();
            default -> "";
            };
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            Expression expr = getMemberValue(normal, "value");
            if (expr instanceof StringLiteralExpr s) {
                return s.asString();
            }
        }
        return "";
    }
}
