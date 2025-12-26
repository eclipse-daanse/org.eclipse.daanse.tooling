/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BaseModelTest {

    @Test
    void testPackageInitialized() {
        assertNotNull(BasePackage.eINSTANCE);
        assertNotNull(BaseFactory.eINSTANCE);
    }

    @Test
    void testCreatePerson() {
        Person person = BaseFactory.eINSTANCE.createPerson();
        person.setName("John");
        person.setAge(30);
        assertEquals("John", person.getName());
        assertEquals(30, person.getAge());
    }

    @Test
    void testCreateAddress() {
        Address address = BaseFactory.eINSTANCE.createAddress();
        address.setStreet("Main St");
        address.setCity("Berlin");
        assertEquals("Main St", address.getStreet());
        assertEquals("Berlin", address.getCity());
    }
}
