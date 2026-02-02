/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.example.runtime.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.eclipse.daanse.example.runtime.RuntimePackage;
import org.eclipse.daanse.example.runtime.impl.RuntimePackageImpl;
import org.junit.jupiter.api.Test;

class PackageFilenameTest {

    @Test
    void packageFilenameIsRelative() throws Exception {
        Class<?> implClass = RuntimePackageImpl.class;
        Field field = implClass.getDeclaredField("packageFilename");
        field.setAccessible(true);
        Object instance = RuntimePackage.eINSTANCE;
        String value = (String) field.get(instance);
        // EMF generates relative path - ecore is in same directory as impl class
        assertEquals("runtime.ecore", value);
    }
}
