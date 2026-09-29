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

import org.openrewrite.text.PlainText;

/**
 * Counts lines in plain text from its text followed by each snippet's text. Text that markers would print
 * isn't source and isn't counted.
 */
final class PlainTextLineCounter {

    private PlainTextLineCounter() {
    }

    static long count(PlainText text) {
        long newlines = Newlines.countNewlines(text.getText());
        String last = text.getText();
        for (PlainText.Snippet snippet : text.getSnippets()) {
            String snippetText = snippet.getText();
            if (snippetText != null && !snippetText.isEmpty()) {
                newlines += Newlines.countNewlines(snippetText);
                last = snippetText;
            }
        }
        if (last == null || last.isEmpty()) {
            return 0;
        }
        return newlines + (last.charAt(last.length() - 1) == '\n' ? 0 : 1);
    }
}
