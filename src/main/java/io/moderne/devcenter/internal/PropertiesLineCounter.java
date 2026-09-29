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

import org.openrewrite.properties.tree.Properties;

/**
 * Counts lines in a properties file from the text {@code PropertiesPrinter} emits, including continuation
 * lines in keys and values. Markers aren't source and aren't counted.
 */
final class PropertiesLineCounter extends PrintOrderLineCounter {

    private PropertiesLineCounter() {
    }

    static long count(Properties.File file) {
        PropertiesLineCounter counter = new PropertiesLineCounter();
        counter.text(file.getPrefix());
        for (Properties.Content content : file.getContent()) {
            if (content instanceof Properties.Entry) {
                counter.entry((Properties.Entry) content);
            } else if (content instanceof Properties.Comment) {
                Properties.Comment comment = (Properties.Comment) content;
                counter.text(comment.getPrefix());
                counter.syntax();
                counter.text(comment.getMessage());
            }
        }
        counter.text(file.getEof());
        return counter.lineCount();
    }

    private void entry(Properties.Entry entry) {
        text(entry.getPrefix());
        text(entry.getKeySource());
        text(entry.getBeforeEquals());
        if (entry.getDelimiter() != Properties.Entry.Delimiter.NONE) {
            syntax();
        }
        text(entry.getValue().getPrefix());
        text(entry.getValue().getSource());
    }
}
