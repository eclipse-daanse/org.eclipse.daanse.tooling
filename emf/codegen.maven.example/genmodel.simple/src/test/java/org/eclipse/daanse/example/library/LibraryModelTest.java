/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class LibraryModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(LibraryPackage.eINSTANCE);
        assertNotNull(LibraryFactory.eINSTANCE);
    }

    @Test
    void testCreateLibrary() {
        Library library = LibraryFactory.eINSTANCE.createLibrary();
        library.setName("City Library");
        assertEquals("City Library", library.getName());
    }

    @Test
    void testCreateBook() {
        Book book = LibraryFactory.eINSTANCE.createBook();
        book.setTitle("EMF Guide");
        assertEquals("EMF Guide", book.getTitle());
    }

    @Test
    void testCreateAuthor() {
        Author author = LibraryFactory.eINSTANCE.createAuthor();
        author.setName("John Doe");
        assertEquals("John Doe", author.getName());
    }
}
