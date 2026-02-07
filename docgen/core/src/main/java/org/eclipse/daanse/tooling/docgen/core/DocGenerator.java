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
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.tooling.docgen.api.model.DocExampleModel;
import org.eclipse.daanse.tooling.docgen.api.model.DocGroupModel;
import org.eclipse.daanse.tooling.docgen.api.model.DocSiteModel;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.DelegatingLoader;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.loader.Loader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class DocGenerator {

    private final PebbleEngine engine;

    public DocGenerator() {
        this(null);
    }

    public DocGenerator(Path customTemplateDir) {
        List<Loader<?>> loaders = new ArrayList<>();

        if (customTemplateDir != null && Files.isDirectory(customTemplateDir)) {
            FileLoader fileLoader = new FileLoader(customTemplateDir.toAbsolutePath().toString());
            loaders.add(fileLoader);
        }

        ClasspathLoader classpathLoader = new ClasspathLoader();
        loaders.add(classpathLoader);

        DelegatingLoader delegatingLoader = new DelegatingLoader(loaders);

        engine = new PebbleEngine.Builder().loader(delegatingLoader).autoEscaping(false).newLineTrimming(false).build();
    }

    public DocSiteModel buildSiteModel(String projectName, String projectDescription, List<DocExampleModel> examples) {
        Map<String, List<DocExampleModel>> groupMap = new LinkedHashMap<>();
        for (DocExampleModel example : examples) {
            if (example.group() != null && !example.group().isEmpty()) {
                groupMap.computeIfAbsent(example.group(), k -> new ArrayList<>()).add(example);
            }
        }
        List<DocGroupModel> groups = groupMap.entrySet().stream().map(e -> new DocGroupModel(e.getKey(), e.getValue()))
                .toList();

        Map<String, List<DocExampleModel>> byTag = new LinkedHashMap<>();
        for (DocExampleModel example : examples) {
            for (String tag : example.tags()) {
                byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(example);
            }
        }

        return new DocSiteModel(projectName, projectDescription, examples, groups, byTag);
    }

    public String render(String templateName, DocSiteModel site) throws IOException {
        PebbleTemplate template = engine.getTemplate(templateName);

        Map<String, Object> context = new HashMap<>();
        context.put("site", site);
        context.put("examples", site.examples());
        context.put("groups", site.groups());
        context.put("byTag", site.byTag());

        StringWriter writer = new StringWriter();
        template.evaluate(writer, context);
        return writer.toString();
    }

    public String renderExample(String templateName, DocExampleModel example) throws IOException {
        PebbleTemplate template = engine.getTemplate(templateName);

        Map<String, Object> context = new HashMap<>();
        context.put("example", example);

        StringWriter writer = new StringWriter();
        template.evaluate(writer, context);
        return writer.toString();
    }

    public void renderToFile(String templateName, DocSiteModel site, Path outputFile) throws IOException {
        String output = render(templateName, site);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, output);
    }

    public void renderExampleToFile(String templateName, DocExampleModel example, Path outputFile) throws IOException {
        String output = renderExample(templateName, example);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, output);
    }
}
