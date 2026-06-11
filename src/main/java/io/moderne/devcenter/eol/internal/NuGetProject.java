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
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads an MSBuild project ({@code *.csproj}, an {@link Xml.Document}) for its NuGet
 * {@code <PackageReference>} dependencies and its {@code <TargetFramework(s)>} runtime monikers.
 * There is no resolved-NuGet LST marker, so this is a static read of the declared values.
 */
public final class NuGetProject {

    private NuGetProject() {
    }

    public static boolean isCsproj(SourceFile source) {
        return source instanceof Xml.Document && source.getSourcePath().toString().endsWith(".csproj");
    }

    public static Set<Detected> dependencies(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        for (Xml.Tag tag : tags(source)) {
            if (!"PackageReference".equals(tag.getName())) {
                continue;
            }
            String name = attribute(tag, "Include");
            if (name == null) {
                continue;
            }
            String version = attribute(tag, "Version");
            if (version == null) {
                version = tag.getChild("Version").flatMap(Xml.Tag::getValue).orElse(null);
            }
            if (version != null && !version.trim().isEmpty()) {
                detected.add(new Detected(Purl.nuget(name), version.trim(), name, "NuGet", Kind.DEPENDENCY));
            }
        }
        return detected;
    }

    public static Set<Detected> runtimes(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        for (Xml.Tag tag : tags(source)) {
            List<String> monikers = new ArrayList<>();
            if ("TargetFramework".equals(tag.getName())) {
                tag.getValue().ifPresent(monikers::add);
            } else if ("TargetFrameworks".equals(tag.getName())) {
                tag.getValue().ifPresent(v -> {
                    for (String m : v.split(";")) {
                        if (!m.trim().isEmpty()) {
                            monikers.add(m.trim());
                        }
                    }
                });
            }
            for (String moniker : monikers) {
                Versions.Runtime runtime = Versions.targetFrameworkRuntime(moniker);
                if (runtime != null) {
                    detected.add(new Detected(runtime.purl, runtime.version,
                            runtime.ecosystem, runtime.ecosystem, Kind.RUNTIME));
                }
            }
        }
        return detected;
    }

    private static List<Xml.Tag> tags(SourceFile source) {
        List<Xml.Tag> all = new ArrayList<>();
        if (source instanceof Xml.Document) {
            collect(((Xml.Document) source).getRoot(), all);
        }
        return all;
    }

    private static void collect(Xml.@Nullable Tag tag, List<Xml.Tag> all) {
        if (tag == null) {
            return;
        }
        all.add(tag);
        for (Xml.Tag child : tag.getChildren()) {
            collect(child, all);
        }
    }

    private static @Nullable String attribute(Xml.Tag tag, String key) {
        for (Xml.Attribute attribute : tag.getAttributes()) {
            if (key.equals(attribute.getKeyAsString())) {
                return attribute.getValueAsString();
            }
        }
        return null;
    }
}
