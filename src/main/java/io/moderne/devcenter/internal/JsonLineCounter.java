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
import org.openrewrite.json.tree.Comment;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.json.tree.Space;

import java.util.List;

/**
 * Counts lines in a JSON document from the text {@code JsonPrinter} emits, walking the tree in print order
 * without a visitor. Markers aren't source and aren't counted.
 */
final class JsonLineCounter extends PrintOrderLineCounter {

    private JsonLineCounter() {
    }

    static long count(Json.Document document) {
        JsonLineCounter counter = new JsonLineCounter();
        counter.space(document.getPrefix());
        counter.node(document.getValue());
        counter.space(document.getEof());
        return counter.lineCount();
    }

    private void node(@Nullable Json json) {
        if (json == null) {
            return;
        }
        space(json.getPrefix());
        if (json instanceof Json.Literal) {
            text(((Json.Literal) json).getSource());
        } else if (json instanceof Json.Member) {
            Json.Member member = (Json.Member) json;
            JsonRightPadded<?> key = member.getPadding().getKey();
            node(key.getElement());
            space(key.getAfter());
            syntax();
            node(member.getValue());
        } else if (json instanceof Json.JsonObject) {
            syntax();
            elements(((Json.JsonObject) json).getPadding().getMembers());
            syntax();
        } else if (json instanceof Json.Array) {
            syntax();
            elements(((Json.Array) json).getPadding().getValues());
            syntax();
        } else if (json instanceof Json.Identifier) {
            text(((Json.Identifier) json).getName());
        }
    }

    private void elements(List<? extends JsonRightPadded<? extends Json>> elements) {
        for (int i = 0; i < elements.size(); i++) {
            JsonRightPadded<? extends Json> element = elements.get(i);
            node(element.getElement());
            space(element.getAfter());
            if (i < elements.size() - 1) {
                syntax();
            }
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
            if (comment.isMultiline()) {
                syntax();
            }
            text(comment.getSuffix());
        }
    }
}
