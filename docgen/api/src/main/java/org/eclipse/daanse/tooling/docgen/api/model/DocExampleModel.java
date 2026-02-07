/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.tooling.docgen.api.model;

import java.util.List;

public record DocExampleModel(String id, String title, String description, List<String> tags, int order, String group,
        String sourceClassName, String sourcePackage, String imports, String classCode, List<DocStepModel> steps)
        implements DocSnippet {
}
