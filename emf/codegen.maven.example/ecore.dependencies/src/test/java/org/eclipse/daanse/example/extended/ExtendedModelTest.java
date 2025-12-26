/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.extended;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ExtendedModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(ExtendedPackage.eINSTANCE);
        assertNotNull(ExtendedFactory.eINSTANCE);
    }

    @Test
    void testCreateEmployee() {
        Employee employee = ExtendedFactory.eINSTANCE.createEmployee();
        employee.setName("Jane");
        employee.setEmployeeId("E123");
        assertEquals("Jane", employee.getName());
        assertEquals("E123", employee.getEmployeeId());
    }

    @Test
    void testCreateCompany() {
        Company company = ExtendedFactory.eINSTANCE.createCompany();
        company.setName("Acme Corp");
        assertEquals("Acme Corp", company.getName());
    }
}
