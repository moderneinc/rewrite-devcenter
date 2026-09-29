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
import org.openrewrite.toml.marker.InlineTable;
import org.openrewrite.toml.tree.Comment;
import org.openrewrite.toml.tree.Space;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.toml.tree.TomlRightPadded;
import org.openrewrite.toml.tree.TomlValue;

import java.util.List;

/**
 * Counts lines in a TOML LST by walking it in the order {@code TomlPrinter} emits text, without printing it.
 */
final class TomlLineCounter extends PrintOrderLineCounter {

    private TomlLineCounter() {
    }

    static long count(Toml.Document document) {
        TomlLineCounter c = new TomlLineCounter();
        c.space(document.getPrefix());
        for (TomlValue value : document.getValues()) {
            c.toml(value);
        }
        c.space(document.getEof());
        return c.lineCount();
    }

    private void toml(@Nullable Toml toml) {
        if (toml == null) {
            return;
        }
        space(toml.getPrefix());
        if (toml instanceof Toml.KeyValue) {
            Toml.KeyValue keyValue = (Toml.KeyValue) toml;
            rightPadded(keyValue.getPadding().getKey());
            syntax();
            toml(keyValue.getValue());
        } else if (toml instanceof Toml.Literal) {
            text(((Toml.Literal) toml).getSource());
        } else if (toml instanceof Toml.Identifier) {
            text(((Toml.Identifier) toml).getSource());
        } else if (toml instanceof Toml.Array) {
            syntax();
            rightPadded(((Toml.Array) toml).getPadding().getValues(), true);
            syntax();
        } else if (toml instanceof Toml.Table) {
            Toml.Table table = (Toml.Table) toml;
            syntax();
            if (table.getMarkers().findFirst(InlineTable.class).isPresent()) {
                rightPadded(table.getPadding().getValues(), true);
                syntax();
            } else {
                // "[name]" or "[[name]]", then the table's values
                rightPadded(table.getPadding().getName());
                syntax();
                rightPadded(table.getPadding().getValues(), false);
            }
        }
    }

    private void rightPadded(List<? extends TomlRightPadded<? extends Toml>> nodes, boolean commaSeparated) {
        for (int i = 0; i < nodes.size(); i++) {
            rightPadded(nodes.get(i));
            if (commaSeparated && i < nodes.size() - 1) {
                syntax();
            }
        }
    }

    private void rightPadded(@Nullable TomlRightPadded<? extends Toml> node) {
        if (node != null) {
            toml(node.getElement());
            space(node.getAfter());
        }
    }

    private void space(@Nullable Space space) {
        if (space == null) {
            return;
        }
        text(space.getWhitespace());
        for (Comment comment : space.getComments()) {
            syntax();
            text(comment.getText());
            text(comment.getSuffix());
        }
    }
}
