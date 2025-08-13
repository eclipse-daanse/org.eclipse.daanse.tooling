/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.tooling.dockerjava;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

public class Test {

    @org.junit.jupiter.api.Test
    void testName() throws Exception {

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient client = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig()).maxConnections(100).connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45)).build();

        DockerClient dc = DockerClientImpl.getInstance(config, client);
        dc.pingCmd().exec();

        Info i = dc.infoCmd().exec();

        assertThat(i.getMemTotal()).isNotNull();
        System.out.println(i);
    }
}
