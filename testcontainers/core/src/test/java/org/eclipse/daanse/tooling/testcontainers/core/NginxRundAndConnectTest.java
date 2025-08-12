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

import org.junit.jupiter.api.Disabled;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

public class NginxRundAndConnectTest {

    @Disabled
    @org.junit.jupiter.api.Test
    void startandConnectPortNginX() throws Exception {

        DockerClientFactory.lazyClient().pingCmd();

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
