/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.tooling.docgen.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.eclipse.daanse.tooling.docgen.api.model.DocExampleModel;
import org.eclipse.daanse.tooling.docgen.api.model.DocSiteModel;
import org.eclipse.daanse.tooling.docgen.core.DocExampleExtractor;
import org.eclipse.daanse.tooling.docgen.core.DocGenerator;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class DocGenMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "docgen.sourceDirectories")
    private List<File> sourceDirectories;

    @Parameter(property = "docgen.outputDirectory", defaultValue = "${project.build.directory}/generated-docs")
    private File outputDirectory;

    @Parameter(property = "docgen.indexTemplate", defaultValue = "templates/markdown/index.md.peb")
    private String indexTemplate;

    @Parameter(property = "docgen.exampleTemplate", defaultValue = "templates/markdown/example.md.peb")
    private String exampleTemplate;

    @Parameter(property = "docgen.customTemplateDir")
    private File customTemplateDir;

    @Parameter(property = "docgen.projectName", defaultValue = "${project.name}")
    private String projectName;

    @Parameter(property = "docgen.projectDescription", defaultValue = "${project.description}")
    private String projectDescription;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<Path> sourcePaths = resolveSourceDirectories();

        if (sourcePaths.isEmpty()) {
            getLog().warn("No source directories found. Skipping documentation generation.");
            return;
        }

        getLog().info("DocGen: Extracting examples from " + sourcePaths.size() + " source directories");

        try {
            DocExampleExtractor extractor = new DocExampleExtractor(sourcePaths);
            List<DocExampleModel> examples = extractor.extract();

            if (examples.isEmpty()) {
                getLog().warn("No @DocExample annotations found. Skipping documentation generation.");
                return;
            }

            getLog().info("DocGen: Found " + examples.size() + " examples");

            Path customTemplatePath = customTemplateDir != null ? customTemplateDir.toPath() : null;
            DocGenerator generator = new DocGenerator(customTemplatePath);
            DocSiteModel site = generator.buildSiteModel(projectName != null ? projectName : project.getName(),
                    projectDescription != null ? projectDescription : "", examples);

            Path outDir = outputDirectory.toPath();

            Path indexFile = outDir.resolve("index.md");
            generator.renderToFile(indexTemplate, site, indexFile);
            getLog().info("DocGen: Generated index at " + indexFile);

            for (DocExampleModel example : examples) {
                Path exampleFile = outDir.resolve(example.id() + ".md");
                generator.renderExampleToFile(exampleTemplate, example, exampleFile);
                getLog().info("DocGen: Generated " + exampleFile);
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate documentation", e);
        }
    }

    private List<Path> resolveSourceDirectories() {
        if (sourceDirectories != null && !sourceDirectories.isEmpty()) {
            return sourceDirectories.stream().map(File::toPath).toList();
        }
        return project.getCompileSourceRoots().stream().map(Path::of).toList();
    }
}
