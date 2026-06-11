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
import org.openrewrite.json.tree.Json;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a {@code package.json} ({@link Json.Document}) for the declared npm dependencies and the
 * {@code engines.node} runtime. npm has no resolved-dependency LST marker, so declared version
 * ranges are normalized to their leading concrete version (see {@link Versions#normalizeNpmRange}).
 */
public final class NpmManifest {

    private static final List<String> DEPENDENCY_BLOCKS = Arrays.asList(
            "dependencies", "devDependencies", "optionalDependencies", "peerDependencies");

    private NpmManifest() {
    }

    public static boolean isPackageJson(SourceFile source) {
        String path = source.getSourcePath().toString();
        return path.equals("package.json") || path.endsWith("/package.json");
    }

    public static Set<Detected> dependencies(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        Json.JsonObject root = rootObject(source);
        if (root == null) {
            return detected;
        }
        for (String block : DEPENDENCY_BLOCKS) {
            Json.JsonObject deps = asObject(memberValue(root, block));
            if (deps == null) {
                continue;
            }
            for (Json member : deps.getMembers()) {
                if (!(member instanceof Json.Member)) {
                    continue;
                }
                Json.Member m = (Json.Member) member;
                String name = keyName(m.getKey());
                String version = Versions.normalizeNpmRange(stringValue(m.getValue()));
                if (name != null && version != null) {
                    detected.add(new Detected(Purl.npm(name), version, name, "npm", Kind.DEPENDENCY));
                }
            }
        }
        return detected;
    }

    public static Set<Detected> runtimes(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        Json.JsonObject root = rootObject(source);
        if (root == null) {
            return detected;
        }
        Json.JsonObject engines = asObject(memberValue(root, "engines"));
        if (engines != null) {
            String node = Versions.normalizeNodeEngine(stringValue(memberValue(engines, "node")));
            if (node != null) {
                detected.add(new Detected(Purl.runtime("node"), node, "Node.js", "Node.js", Kind.RUNTIME));
            }
        }
        return detected;
    }

    private static Json.@Nullable JsonObject rootObject(SourceFile source) {
        if (!(source instanceof Json.Document) || !isPackageJson(source)) {
            return null;
        }
        return asObject(((Json.Document) source).getValue());
    }

    private static @Nullable Json memberValue(Json.JsonObject object, String key) {
        for (Json member : object.getMembers()) {
            if (member instanceof Json.Member && key.equals(keyName(((Json.Member) member).getKey()))) {
                return ((Json.Member) member).getValue();
            }
        }
        return null;
    }

    private static Json.@Nullable JsonObject asObject(@Nullable Json value) {
        return value instanceof Json.JsonObject ? (Json.JsonObject) value : null;
    }

    private static @Nullable String keyName(@Nullable Json key) {
        return key instanceof Json.Literal ? unquote((Json.Literal) key) : null;
    }

    private static @Nullable String stringValue(@Nullable Json value) {
        return value instanceof Json.Literal ? unquote((Json.Literal) value) : null;
    }

    private static String unquote(Json.Literal literal) {
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
}
