/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.tooling.docgen.core;

public final class CodeFormatter {

    private CodeFormatter() {
    }

    public static String dedent(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String[] lines = code.split("\n", -1);

        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.isBlank()) {
                int indent = 0;
                while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
                    indent++;
                }
                minIndent = Math.min(minIndent, indent);
            }
        }

        if (minIndent == 0 || minIndent == Integer.MAX_VALUE) {
            return code;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (!lines[i].isBlank()) {
                sb.append(lines[i].substring(minIndent));
            }
        }

        return sb.toString();
    }
}
