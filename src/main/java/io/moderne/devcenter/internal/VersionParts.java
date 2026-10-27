/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.devcenter.internal;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a version on {@code .}, {@code _}, {@code -} and {@code +}, and wherever digits meet non-digits, so that
 * {@code 1.2.3-RC1} yields {@code [1, 2, 3, null, 1]}. Parts that aren't numbers are {@code null}.
 */
public class VersionParts {
    private VersionParts() {
    }

    public static @Nullable Long[] numeric(String version) {
        List<String> parts = split(version);
        @Nullable Long[] numericParts = new Long[parts.size()];
        for (int i = 0; i < numericParts.length; i++) {
            try {
                numericParts[i] = Long.parseLong(parts.get(i));
            } catch (NumberFormatException ignored) {
            }
        }
        return numericParts;
    }

    private static List<String> split(String version) {
        List<String> parts = new ArrayList<>();
        boolean digit = false;
        int startPart = 0;
        int pos = 0;
        for (; pos < version.length(); pos++) {
            char ch = version.charAt(pos);
            if (ch == '.' || ch == '_' || ch == '-' || ch == '+') {
                parts.add(version.substring(startPart, pos));
                startPart = pos + 1;
                digit = false;
            } else if (ch >= '0' && ch <= '9') {
                if (!digit && pos > startPart) {
                    parts.add(version.substring(startPart, pos));
                    startPart = pos;
                }
                digit = true;
            } else {
                if (digit) {
                    parts.add(version.substring(startPart, pos));
                    startPart = pos;
                }
                digit = false;
            }
        }
        if (pos > startPart) {
            parts.add(version.substring(startPart, pos));
        }
        return parts;
    }
}
