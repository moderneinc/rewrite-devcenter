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
import org.openrewrite.yaml.marker.OmitColon;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

/**
 * Counts lines in a YAML LST by walking it in the order {@code YamlPrinter} emits text, without printing it.
 */
final class YamlLineCounter extends PrintOrderLineCounter {

    private YamlLineCounter() {
    }

    static long count(Yaml.Documents documents) {
        YamlLineCounter c = new YamlLineCounter();
        for (Yaml.Document document : documents.getDocuments()) {
            c.document(document);
        }
        c.text(documents.getSuffix());
        return c.lineCount();
    }

    private void document(Yaml.Document document) {
        text(document.getPrefix());
        List<Yaml.Directive> directives = document.getDirectives();
        if (directives != null) {
            for (Yaml.Directive directive : directives) {
                text(directive.getPrefix());
                syntax();
                text(directive.getValue());
                text(directive.getSuffix());
            }
        }
        if (document.isExplicit()) {
            syntax();
        }
        block(document.getBlock());
        Yaml.Document.End end = document.getEnd();
        if (end != null) {
            text(end.getPrefix());
            if (end.isExplicit()) {
                syntax();
            }
        }
    }

    private void block(@Nullable Yaml yaml) {
        if (yaml instanceof Yaml.Scalar) {
            Yaml.Scalar scalar = (Yaml.Scalar) yaml;
            text(scalar.getPrefix());
            anchor(scalar.getAnchor());
            tag(scalar.getTag());
            switch (scalar.getStyle()) {
                case DOUBLE_QUOTED:
                case SINGLE_QUOTED:
                    syntax();
                    text(scalar.getValue());
                    syntax();
                    break;
                case LITERAL:
                case FOLDED:
                    syntax();
                    text(scalar.getValue());
                    break;
                default:
                    text(scalar.getValue());
            }
        } else if (yaml instanceof Yaml.Mapping) {
            Yaml.Mapping mapping = (Yaml.Mapping) yaml;
            anchor(mapping.getAnchor());
            bracket(mapping.getOpeningBracePrefix());
            tag(mapping.getTag());
            for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                text(entry.getPrefix());
                block(entry.getKey());
                text(entry.getBeforeMappingValueIndicator());
                if (!entry.getMarkers().findFirst(OmitColon.class).isPresent()) {
                    syntax();
                }
                block(entry.getValue());
            }
            bracket(mapping.getClosingBracePrefix());
        } else if (yaml instanceof Yaml.Sequence) {
            Yaml.Sequence sequence = (Yaml.Sequence) yaml;
            anchor(sequence.getAnchor());
            bracket(sequence.getOpeningBracketPrefix());
            tag(sequence.getTag());
            for (Yaml.Sequence.Entry entry : sequence.getEntries()) {
                text(entry.getPrefix());
                if (entry.isDash()) {
                    syntax();
                }
                block(entry.getBlock());
                bracket(entry.getTrailingCommaPrefix());
            }
            bracket(sequence.getClosingBracketPrefix());
        } else if (yaml instanceof Yaml.Alias) {
            Yaml.Alias alias = (Yaml.Alias) yaml;
            text(alias.getPrefix());
            syntax();
            if (alias.getAnchor() != null) {
                text(alias.getAnchor().getKey());
            }
        }
    }

    private void anchor(Yaml.@Nullable Anchor anchor) {
        if (anchor != null) {
            text(anchor.getPrefix());
            syntax();
            text(anchor.getKey());
            text(anchor.getPostfix());
        }
    }

    private void tag(Yaml.@Nullable Tag tag) {
        if (tag != null) {
            text(tag.getPrefix());
            text(tag.getKind().print(tag.getName()));
            text(tag.getSuffix());
        }
    }

    // A brace, bracket or comma, printed after its prefix when present
    private void bracket(@Nullable String prefix) {
        if (prefix != null) {
            text(prefix);
            syntax();
        }
    }
}
