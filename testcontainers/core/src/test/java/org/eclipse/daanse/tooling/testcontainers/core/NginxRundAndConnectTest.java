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
package org.eclipse.daanse.tooling.testcontainers.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;

public class NginxRundAndConnectTest {

//    @Test
    void testName() throws Exception {

        ClassLoader clOriginal = Thread.currentThread().getContextClassLoader();
        ClassLoader testconCL = DockerClientProviderStrategy.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(testconCL);

        System.out.println("------------");
        ServiceLoader.load(DockerClientProviderStrategy.class).forEach(s -> System.out.println(s));
        Thread.currentThread().setContextClassLoader(clOriginal);

        System.out.println("------------");

        DockerClientProviderStrategy s1 = new org.testcontainers.dockerclient.TestcontainersHostPropertyClientProviderStrategy();
        DockerClientProviderStrategy s2 = new org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy();
        DockerClientProviderStrategy s3 = new org.testcontainers.dockerclient.UnixSocketClientProviderStrategy();
        DockerClientProviderStrategy s4 = new org.testcontainers.dockerclient.DockerMachineClientProviderStrategy();
        DockerClientProviderStrategy s5 = new org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy();
        DockerClientProviderStrategy s6 = new org.testcontainers.dockerclient.RootlessDockerClientProviderStrategy();
        DockerClientProviderStrategy s7 = new org.testcontainers.dockerclient.DockerDesktopClientProviderStrategy();

        List<DockerClientProviderStrategy> strategies = List.of(s1, s2, s3, s4, s5, s6, s7);
        DockerClientProviderStrategy s = DockerClientProviderStrategy.getFirstValidStrategy(strategies);

    }

    @org.junit.jupiter.api.Test
    void startandConnectPortNginX() throws Exception {

        try (GenericContainer<?> nginx = new GenericContainer("nginx:alpine-slim").withExposedPorts(80)

        ) {
            // Starte den Container
            nginx.start();

            // get external port mapped
            Integer mappedPort = nginx.getMappedPort(80);
            String url = "http://localhost:" + mappedPort;
            System.out.println("Container Port: " + mappedPort);

            // Create a HTTP-Client
            HttpClient client = HttpClient.newHttpClient();

            // BuildHTTP GET-Request
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();

            // Send Request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check Statuscode is 200 OK
            assertTrue(response.statusCode() == 200, "Expected 200 OK, but got: " + response.statusCode());

            // Answer
            System.out.println("Response body: " + response.body());

            nginx.stop();

            System.out.println(1113);
        } finally {
        }
    }
}
