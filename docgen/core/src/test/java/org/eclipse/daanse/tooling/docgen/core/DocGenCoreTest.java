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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.daanse.tooling.docgen.api.model.DocExampleModel;
import org.eclipse.daanse.tooling.docgen.api.model.DocSiteModel;
import org.eclipse.daanse.tooling.docgen.api.model.DocStepModel;
import org.junit.jupiter.api.Test;

class DocGenCoreTest {

    @Test
    void testExtractFromTestResources() throws Exception {
        Path resourceDir = Path.of("src/test/resources");
        DocExampleExtractor extractor = new DocExampleExtractor(List.of(resourceDir));

        List<DocExampleModel> examples = extractor.extract();
        assertThat(examples).isNotEmpty();

        DocExampleModel example = examples.getFirst();
        assertThat(example.title()).isEqualTo("Sample Example");
        assertThat(example.description()).isEqualTo("A sample example for testing extraction.");
        assertThat(example.tags()).containsExactly("test", "sample");
        assertThat(example.order()).isEqualTo(1);
        assertThat(example.group()).isEqualTo("Testing");
        assertThat(example.sourceClassName()).isEqualTo("SampleDocExample");

        assertThat(example.steps()).hasSize(2);

        DocStepModel step1 = example.steps().get(0);
        assertThat(step1.order()).isEqualTo(1);
        assertThat(step1.title()).isEqualTo("First Step");
        assertThat(step1.description()).isEqualTo("This is the first step.");
        assertThat(step1.code()).isNotEmpty();
        assertThat(step1.expectedOutput()).isEmpty();

        DocStepModel step2 = example.steps().get(1);
        assertThat(step2.order()).isEqualTo(2);
        assertThat(step2.title()).isEqualTo("Second Step");
        assertThat(step2.expectedOutput()).isEqualTo("Result: 42");
    }

    @Test
    void testCodeFormatterDedent() {
        String indented = "        line1\n        line2\n            indented";
        String dedented = CodeFormatter.dedent(indented);
        assertThat(dedented).isEqualTo("line1\nline2\n    indented");
    }

    @Test
    void testCodeFormatterDedentEmpty() {
        assertThat(CodeFormatter.dedent("")).isEmpty();
        assertThat(CodeFormatter.dedent(null)).isNull();
    }

    @Test
    void testRenderExamplePageWithSteps() throws Exception {
        DocStepModel step1 = new DocStepModel(1, "Initialize", "Set up the factory.",
                "Factory f = Factory.getInstance();", "");
        DocStepModel step2 = new DocStepModel(2, "Create Object", "Create a new object.", "Object obj = f.create();",
                "Created: obj1");

        DocExampleModel example = new DocExampleModel("create-model", "Create a Model", "Shows how to create a model.",
                List.of("getting-started"), 1, "Quick Start", "CreateModelExample", "com.example", "", "",
                List.of(step1, step2));

        DocGenerator generator = new DocGenerator();
        String output = generator.renderExample("templates/markdown/example.md.peb", example);

        assertThat(output).contains("# Create a Model").contains("## Initialize")
                .contains("Factory f = Factory.getInstance();").contains("## Create Object").contains("Created: obj1");
    }

    @Test
    void testRenderExamplePageWithClassCode() throws Exception {
        DocExampleModel example = new DocExampleModel("simple", "Simple Example", "A simple example.", List.of(), 1, "",
                "SimpleExample", "com.example", "", "public void run() {\n    System.out.println(\"Hello\");\n}",
                List.of());

        DocGenerator generator = new DocGenerator();
        String output = generator.renderExample("templates/markdown/example.md.peb", example);

        assertThat(output).contains("# Simple Example").contains("System.out.println(\"Hello\")");
    }

    @Test
    void testRenderIndexPage() throws Exception {
        DocExampleModel ex1 = new DocExampleModel("ex1", "Example One", "First example.", List.of("tag1"), 1, "Group A",
                "Ex1", "com.example", "", "", List.of());
        DocExampleModel ex2 = new DocExampleModel("ex2", "Example Two", "Second example.", List.of("tag1"), 2,
                "Group A", "Ex2", "com.example", "", "", List.of());

        DocGenerator generator = new DocGenerator();
        DocSiteModel site = generator.buildSiteModel("My Project", "Project description.", List.of(ex1, ex2));
        String output = generator.render("templates/markdown/index.md.peb", site);

        assertThat(output).contains("# My Project").contains("Project description.").contains("[Example One](ex1.md)")
                .contains("First example.").contains("[Example Two](ex2.md)").contains("Second example.");
    }

    @Test
    void testBuildSiteModel() {
        DocExampleModel ex1 = new DocExampleModel("ex1", "Example 1", "", List.of("tag1", "tag2"), 1, "Group A", "Ex1",
                "com.example", "", "", List.of());
        DocExampleModel ex2 = new DocExampleModel("ex2", "Example 2", "", List.of("tag1"), 2, "Group A", "Ex2",
                "com.example", "", "", List.of());

        DocGenerator generator = new DocGenerator();
        DocSiteModel site = generator.buildSiteModel("Project", "Desc", List.of(ex1, ex2));

        assertThat(site.groups()).hasSize(1);
        assertThat(site.groups().getFirst().name()).isEqualTo("Group A");
        assertThat(site.groups().getFirst().examples()).hasSize(2);
        assertThat(site.byTag().get("tag1")).hasSize(2);
        assertThat(site.byTag().get("tag2")).hasSize(1);
    }
}
