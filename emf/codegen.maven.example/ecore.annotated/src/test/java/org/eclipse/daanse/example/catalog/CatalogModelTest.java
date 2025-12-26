/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CatalogModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(CatalogPackage.eINSTANCE);
        assertNotNull(CatalogFactory.eINSTANCE);
    }

    @Test
    void testCreateCatalog() {
        Catalog catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName("Main Catalog");
        assertEquals("Main Catalog", catalog.getName());
    }

    @Test
    void testCreateProduct() {
        Product product = CatalogFactory.eINSTANCE.createProduct();
        product.setName("Widget");
        product.setPrice(new BigDecimal("9.99"));
        assertEquals("Widget", product.getName());
        assertEquals(new BigDecimal("9.99"), product.getPrice());
    }
}
