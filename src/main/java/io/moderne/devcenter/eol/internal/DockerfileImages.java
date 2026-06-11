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
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.text.PlainText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the base images from a Dockerfile's {@code FROM} instructions. Dockerfiles are matched
 * whether they were parsed into a {@link Docker.File} LST (the usual case on the Moderne platform)
 * or fell back to {@link PlainText}. Each image is keyed as {@code pkg:docker/<namespace>/<name>}
 * (official images use the {@code library} namespace) and matched on the leading version of its
 * tag; non-version tags (codenames like {@code bullseye}, or {@code latest}) are skipped.
 */
public final class DockerfileImages {

    private static final Logger logger = LoggerFactory.getLogger(DockerfileImages.class);

    private DockerfileImages() {
    }

    public static boolean isDockerfile(SourceFile source) {
        if (source instanceof Docker.File) {
            return true;
        }
        if (!(source instanceof PlainText)) {
            return false;
        }
        String name = fileName(source.getSourcePath().toString());
        return name.equals("Dockerfile") ||
               name.startsWith("Dockerfile.") ||
               name.toLowerCase(Locale.ROOT).endsWith(".dockerfile");
    }

    public static Set<Detected> find(SourceFile source) {
        if (source instanceof Docker.File) {
            return fromLst((Docker.File) source);
        }
        if (isDockerfile(source)) {
            return fromText(((PlainText) source).getText());
        }
        return new LinkedHashSet<>();
    }

    private static Set<Detected> fromLst(Docker.File file) {
        Set<Detected> detected = new LinkedHashSet<>();
        Set<String> stageAliases = new LinkedHashSet<>();
        // rewrite-docker is still maturing: a successfully-parsed Docker.File can carry partially
        // populated nodes (e.g. an instruction whose child list is null instead of empty). Guard the
        // traversal so a malformed node skips its image rather than throwing out of the recipe
        // visitor — a report-only card must degrade gracefully, not abort detection for the repo.
        List<Docker.Stage> stages = file.getStages();
        if (stages == null) {
            return detected;
        }
        for (Docker.Stage stage : stages) {
            try {
                if (stage == null) {
                    continue;
                }
                Docker.From from = stage.getFrom();
                if (from == null) {
                    continue;
                }
                Detected image = image(argumentText(from.getImageName()), argumentText(from.getTag()), stageAliases);
                if (image != null) {
                    detected.add(image);
                }
                if (from.getAs() != null && from.getAs().getName() != null) {
                    stageAliases.add(from.getAs().getName().getText().toLowerCase(Locale.ROOT));
                }
            } catch (RuntimeException e) {
                logger.debug("Skipping a malformed Docker stage in {}", file.getSourcePath(), e);
            }
        }
        return detected;
    }

    private static Set<Detected> fromText(String text) {
        Set<Detected> detected = new LinkedHashSet<>();
        Set<String> stageAliases = new LinkedHashSet<>();
        for (String raw : text.split("\n")) {
            String[] tokens = raw.trim().split("\\s+");
            if (tokens.length < 2 || !"FROM".equalsIgnoreCase(tokens[0])) {
                continue;
            }
            int i = 1;
            while (i < tokens.length && tokens[i].startsWith("--")) {
                i++; // skip flags like --platform=linux/amd64
            }
            if (i >= tokens.length) {
                continue;
            }
            String ref = tokens[i];
            int at = ref.indexOf('@');
            if (at >= 0) {
                ref = ref.substring(0, at); // drop @sha256:... digest
            }
            int lastSlash = ref.lastIndexOf('/');
            int colon = ref.indexOf(':', lastSlash + 1);
            String tag = colon >= 0 ? ref.substring(colon + 1) : null;
            String namePath = colon >= 0 ? ref.substring(0, colon) : ref;
            Detected image = image(namePath, tag, stageAliases);
            if (image != null) {
                detected.add(image);
            }
            if (i + 2 < tokens.length && "AS".equalsIgnoreCase(tokens[i + 1])) {
                stageAliases.add(tokens[i + 2].toLowerCase(Locale.ROOT));
            }
        }
        return detected;
    }

    /** Build a detected image from a (namePath, tag), or {@code null} if it cannot be version-matched. */
    private static @Nullable Detected image(@Nullable String namePath, @Nullable String tag, Set<String> stageAliases) {
        if (namePath == null || namePath.startsWith("$") ||
            stageAliases.contains(namePath.toLowerCase(Locale.ROOT))) {
            return null; // unresolvable, ARG-templated, or a reference to an earlier build stage
        }
        String version = Versions.leadingVersion(tag);
        if (version == null) {
            return null; // untagged, "latest", or a codename tag we cannot version-match
        }
        String[] segments = namePath.split("/");
        int start = 0;
        // Drop a registry segment such as "mcr.microsoft.com" or "host:5000".
        if (segments.length > 1 && (segments[0].contains(".") || segments[0].contains(":"))) {
            start = 1;
        }
        String namespace;
        String name;
        if (segments.length - start <= 1) {
            namespace = "library";
            name = segments[segments.length - 1];
        } else {
            namespace = segments[start];
            name = segments[start + 1];
        }
        return new Detected(
                Purl.docker(namespace + '/' + name),
                version,
                name + ':' + tag,
                "Docker",
                Kind.IMAGE);
    }

    /** Concatenate an argument's literal contents, or {@code null} if it is empty or templated. */
    private static @Nullable String argumentText(Docker.@Nullable Argument argument) {
        if (argument == null) {
            return null;
        }
        List<Docker.ArgumentContent> contents = argument.getContents();
        if (contents == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Docker.ArgumentContent content : contents) {
            if (!(content instanceof Docker.Literal)) {
                return null; // an environment-variable / templated segment we cannot resolve
            }
            sb.append(((Docker.Literal) content).getText());
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
