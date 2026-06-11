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
package io.moderne.devcenter.eol.internal;

import io.moderne.devcenter.eol.internal.Detected.Kind;
import org.jspecify.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.text.PlainText;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.toml.tree.TomlKey;
import org.openrewrite.toml.tree.TomlValue;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads the Python runtime version from common version declarations: {@code .python-version}
 * (pyenv) and {@code runtime.txt} (buildpacks), both plain text, and {@code requires-python} in the
 * {@code [project]} table of {@code pyproject.toml}. Keyed as {@code pkg:generic/python}.
 */
public final class PythonRuntime {

    private PythonRuntime() {
    }

    public static Set<Detected> find(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        String path = source.getSourcePath().toString();
        if (source instanceof PlainText) {
            String text = ((PlainText) source).getText();
            if (endsWith(path, ".python-version")) {
                add(detected, Versions.leadingVersion(firstLine(text)));
            } else if (endsWith(path, "runtime.txt")) {
                add(detected, Versions.leadingVersion(stripPrefix(firstLine(text))));
            }
        } else if (source instanceof Toml.Document && endsWith(path, "pyproject.toml")) {
            add(detected, Versions.normalizeNpmRange(requiresPython((Toml.Document) source)));
        }
        return detected;
    }

    private static void add(Set<Detected> detected, @Nullable String version) {
        if (version != null && !version.isEmpty()) {
            detected.add(new Detected(Purl.runtime("python"), version, "Python", "Python", Kind.RUNTIME));
        }
    }

    private static @Nullable String requiresPython(Toml.Document document) {
        for (TomlValue value : document.getValues()) {
            if (!(value instanceof Toml.Table)) {
                continue;
            }
            Toml.Table table = (Toml.Table) value;
            if (!"project".equals(table.getName().getName())) {
                continue;
            }
            for (Toml member : table.getValues()) {
                if (member instanceof Toml.KeyValue) {
                    Toml.KeyValue kv = (Toml.KeyValue) member;
                    if ("requires-python".equals(keyName(kv.getKey()))) {
                        return literalString(kv.getValue());
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable String keyName(TomlKey key) {
        return key instanceof Toml.Identifier ? ((Toml.Identifier) key).getName() : null;
    }

    private static @Nullable String literalString(Toml value) {
        return value instanceof Toml.Literal ? unquote((Toml.Literal) value) : null;
    }

    private static String unquote(Toml.Literal literal) {
        Object value = literal.getValue();
        if (value instanceof String) {
            return (String) value;
        }
        String source = literal.getSource();
        if (source.length() >= 2 && (source.charAt(0) == '"' || source.charAt(0) == '\'')) {
            return source.substring(1, source.length() - 1);
        }
        return source;
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }

    private static String stripPrefix(String line) {
        // runtime.txt is typically "python-3.11.4".
        int dash = line.indexOf('-');
        return dash >= 0 ? line.substring(dash + 1) : line;
    }

    private static boolean endsWith(String path, String name) {
        return path.equals(name) || path.endsWith("/" + name);
    }
}
