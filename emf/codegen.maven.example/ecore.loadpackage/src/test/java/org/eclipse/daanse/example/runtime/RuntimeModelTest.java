/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RuntimeModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(RuntimePackage.eINSTANCE);
        assertNotNull(RuntimeFactory.eINSTANCE);
    }

    @Test
    void testCreateDocument() {
        Document document = RuntimeFactory.eINSTANCE.createDocument();
        document.setName("Test Document");
        document.setContent("Test Content");
        assertEquals("Test Document", document.getName());
        assertEquals("Test Content", document.getContent());
    }
}
