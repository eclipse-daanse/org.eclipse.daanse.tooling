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
package org.eclipse.daanse.tooling.emf.codegen;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Progress monitor implementation that logs to Maven's Log.
 */
public class MavenProgressMonitor implements IProgressMonitor {

    private final Log log;
    private String taskName;
    private boolean canceled = false;

    public MavenProgressMonitor(Log log) {
        this.log = log;
    }

    @Override
    public void beginTask(String name, int totalWork) {
        this.taskName = name;
        log.info("Starting: " + name);
    }

    @Override
    public void done() {
        log.info("Completed: " + taskName);
    }

    @Override
    public void internalWorked(double work) {
        log.debug("Internal work: " + work + " on " + taskName);
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean value) {
        this.canceled = value;
        if (value) {
            log.warn("Canceled: " + taskName);
        }
    }

    @Override
    public void setTaskName(String name) {
        this.taskName = name;
    }

    @Override
    public void subTask(String name) {
        log.debug("Subtask: " + name);
    }

    @Override
    public void worked(int work) {
        log.debug("Worked: " + work + " on " + taskName);
    }
}
