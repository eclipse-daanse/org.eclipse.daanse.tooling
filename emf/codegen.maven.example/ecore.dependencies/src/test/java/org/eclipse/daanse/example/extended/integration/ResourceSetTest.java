/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.example.extended.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.example.extended.ExtendedFactory;
import org.eclipse.daanse.example.extended.ExtendedPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.fennec.emf.osgi.annotation.require.RequireEMF;
import org.eclipse.fennec.emf.osgi.constants.EMFNamespaces;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.BundleContext;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.cm.ConfigurationExtension;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@ExtendWith(ConfigurationExtension.class)
@RequireEMF
public class ResourceSetTest {

    private static String BASE_DIR = System.getProperty("basePath");

    @Test
    public void epackageExistsTest(
            @InjectBundleContext BundleContext bc, @InjectService(cardinality = 1, filter = "(" + EMFNamespaces.EMF_NAME
                    + "=" + ExtendedPackage.eNAME + ")", timeout = 1000) ServiceAware<EPackage> saEPackage)
            throws InterruptedException, IOException {
        assertThat(saEPackage.getServices()).hasSize(1);

        EPackage ePackage = saEPackage.getService();
        assertThat(ePackage).isNotNull();
        assertThat(ePackage.getNsURI()).isEqualTo(ExtendedPackage.eNS_URI);
        System.out.println("EPackage: " + ePackage.getName() + " - " + ePackage.getNsURI());
    }

    @Test
    public void resourceSetReadTest(@InjectBundleContext BundleContext bc,
            @InjectService(cardinality = 1, filter = "(" + EMFNamespaces.EMF_MODEL_NSURI + "=" + ExtendedPackage.eNS_URI
                    + ")", timeout = 1000) ServiceAware<ResourceSet> saResourceSet)
            throws InterruptedException, IOException {
        assertThat(saResourceSet.getServices()).hasSize(1);

        ResourceSet rs = saResourceSet.getService();

        URI uri = URI.createURI(BASE_DIR + "/src/test/resources/test-model.xmi");
        Resource resource = rs.getResource(uri, true);
        resource.load(Map.of());
        EObject root = resource.getContents().get(0);
        assertThat(root).isNotNull();
        System.out.println(root);
    }

    @TempDir
    Path tempDir;

    @Test
    public void writeTest(@InjectBundleContext BundleContext bc,
            @InjectService(cardinality = 1, filter = "(" + EMFNamespaces.EMF_MODEL_NSURI + "=" + ExtendedPackage.eNS_URI
                    + ")", timeout = 1000) ServiceAware<ResourceSet> saResourceSet)
            throws InterruptedException, IOException {
        assertThat(saResourceSet.getServices()).hasSize(1);

        ResourceSet rs = saResourceSet.getService();

        Path file = Files.createTempFile(tempDir, "out", ".xmi");
        URI uri = URI.createFileURI(file.toAbsolutePath().toString());
        Resource resource = rs.createResource(uri);
        resource.getContents().add(ExtendedFactory.eINSTANCE.createCompany());
        resource.getContents().add(ExtendedFactory.eINSTANCE.createEmployee());

        resource.save(Map.of());
        String content = Files.readString(file);
        assertThat(content).contains("Company").contains("Employee");
        System.out.println(content);
    }
}
