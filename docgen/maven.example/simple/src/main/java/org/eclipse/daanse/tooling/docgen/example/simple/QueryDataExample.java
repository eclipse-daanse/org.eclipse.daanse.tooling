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

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.daanse.tooling.docgen.api.annotation.DocExample;
import org.eclipse.daanse.tooling.docgen.api.annotation.DocOutput;
import org.eclipse.daanse.tooling.docgen.api.annotation.DocStep;

@DocExample(
    title = "Stream Filtering and Mapping",
    description = "Demonstrates how to filter and transform data using the Java Stream API.",
    tags = {"streams", "collections"},
    order = 2,
    group = "Quick Start"
)
public class QueryDataExample {

    record Person(String name, int age) {}

    @DocStep(order = 1, title = "Create Sample Data",
             description = "Build a list of `Person` records to work with.")
    public List<Person> createData() {
        return List.of(
            new Person("Alice", 32),
            new Person("Bob", 17),
            new Person("Charlie", 25),
            new Person("Diana", 14)
        );
    }

    @DocStep(order = 2, title = "Filter Adults",
             description = "Use `stream().filter()` to keep only persons aged 18 or older.")
    @DocOutput("[Alice, Charlie]")
    public void filterAdults() {
        List<Person> people = createData();
        List<String> adultNames = people.stream()
            .filter(p -> p.age() >= 18)
            .map(Person::name)
            .collect(Collectors.toList());
        System.out.println(adultNames);
    }

    @DocStep(order = 3, title = "Compute Average Age",
             description = "Use `stream().mapToInt().average()` to compute the mean age.")
    @DocOutput("Average age: 22.0")
    public void computeAverage() {
        List<Person> people = createData();
        double avg = people.stream()
            .mapToInt(Person::age)
            .average()
            .orElse(0.0);
        System.out.println("Average age: " + avg);
    }
}
