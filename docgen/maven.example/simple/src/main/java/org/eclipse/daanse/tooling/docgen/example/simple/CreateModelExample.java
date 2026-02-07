/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.tooling.docgen.example.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.tooling.docgen.api.annotation.DocExample;
import org.eclipse.daanse.tooling.docgen.api.annotation.DocOutput;
import org.eclipse.daanse.tooling.docgen.api.annotation.DocStep;

@DocExample(
    title = "Working with Collections",
    description = "Shows how to create, populate and transform a list using the Java Collections API.",
    tags = {"getting-started", "collections"},
    order = 1,
    group = "Quick Start"
)
public class CreateModelExample {

    private final List<String> names = new ArrayList<>();

    @DocStep(order = 1, title = "Create a List",
             description = "Initialize an `ArrayList` and add some elements.")
    public void createList() {
        names.add("Alice");
        names.add("Bob");
        names.add("Charlie");
    }

    @DocStep(order = 2, title = "Sort and Print",
             description = "Sort the list alphabetically and print each element.")
    @DocOutput("Alice\nBob\nCharlie")
    public void sortAndPrint() {
        names.sort(String::compareTo);
        names.forEach(System.out::println);
    }

    @DocStep(order = 3, title = "Transform to a Map",
             description = "Convert the list into a `Map` keyed by the first letter.")
    @DocOutput("{A=Alice, B=Bob, C=Charlie}")
    public void transformToMap() {
        Map<String, String> byInitial = new java.util.TreeMap<>();
        for (String name : names) {
            byInitial.put(name.substring(0, 1), name);
        }
        System.out.println(byInitial);
    }
}
