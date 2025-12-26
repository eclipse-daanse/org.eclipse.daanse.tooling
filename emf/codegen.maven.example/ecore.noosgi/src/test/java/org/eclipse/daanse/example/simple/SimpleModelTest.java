/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SimpleModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(SimplePackage.eINSTANCE);
        assertNotNull(SimpleFactory.eINSTANCE);
    }

    @Test
    void testCreateItem() {
        Item item = SimpleFactory.eINSTANCE.createItem();
        item.setName("Test Item");
        assertEquals("Test Item", item.getName());
    }

    @Test
    void testCreateContainer() {
        Container container = SimpleFactory.eINSTANCE.createContainer();
        container.setLabel("Box");
        assertEquals("Box", container.getLabel());
    }
}
