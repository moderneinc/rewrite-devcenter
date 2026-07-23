/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;

/**
 * Drives a source file's own printer but discards the output, counting newlines as they stream by.
 * This avoids reconstructing the full source string (potentially tens of MB) and the second O(chars)
 * pass that {@code printAll().chars().filter(...).count()} would incur, while producing a line count
 * identical to {@link SourceFile#printAll()}.
 */
public class LineCountingOutputCapture extends PrintOutputCapture<Integer> {
    private long newlines;
    private boolean nonEmpty;
    private char last;

    public LineCountingOutputCapture() {
        super(0);
    }

    @Override
    public PrintOutputCapture<Integer> append(@Nullable String text) {
        if (text != null && !text.isEmpty()) {
            nonEmpty = true;
            for (int i = 0, len = text.length(); i < len; i++) {
                if (text.charAt(i) == '\n') {
                    newlines++;
                }
            }
            last = text.charAt(text.length() - 1);
        }
        return this;
    }

    @Override
    public PrintOutputCapture<Integer> append(char c) {
        nonEmpty = true;
        if (c == '\n') {
            newlines++;
        }
        last = c;
        return this;
    }

    /**
     * @return the line count, matching {@code FindOrganizationStatistics}: newline count plus one for a
     * final line not terminated by a newline, or zero for empty output.
     */
    public long lineCount() {
        if (!nonEmpty) {
            return 0;
        }
        return newlines + (last == '\n' ? 0 : 1);
    }
}
