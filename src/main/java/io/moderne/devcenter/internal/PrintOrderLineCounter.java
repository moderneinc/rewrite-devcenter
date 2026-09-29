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

import io.moderne.devcenter.internal.Newlines.Counter;
import org.jspecify.annotations.Nullable;

/**
 * Tallies text in the order a printer emits it, so it knows whether that text ends with a newline. Counters that
 * walk a tree themselves extend it; visitor-based ones pass one along as their parameter.
 */
class PrintOrderLineCounter {
    private final Counter counter = new Counter();
    private boolean endsWithNewline;

    final void text(@Nullable String text) {
        if (text != null && !text.isEmpty()) {
            Newlines.addText(counter, text);
            endsWithNewline = text.charAt(text.length() - 1) == '\n';
        }
    }

    // Printed syntax, like a delimiter, which holds no newline
    final void syntax() {
        counter.sawText = true;
        endsWithNewline = false;
    }

    final long lineCount() {
        return Newlines.lineCount(counter, endsWithNewline);
    }
}
