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

import lombok.Value;

/**
 * A single artifact or runtime detected on a source file, reduced to the {@link Purl} used to
 * match it against an {@link EolFeed} plus the metadata each card records in its detail report.
 */
@Value
public class Detected {

    /** Kind of thing detected, recorded in the report's {@code Kind} column. */
    public enum Kind {
        DEPENDENCY("dependency"),
        RUNTIME("runtime"),
        IMAGE("image");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** The purl used to look the artifact/runtime up in the feed. */
    Purl purl;

    /** The resolved or declared version that is matched against release cycles. */
    String version;

    /** Human-readable name for the report, e.g. {@code org.springframework.boot:spring-boot}. */
    String displayName;

    /** Ecosystem label for the report, e.g. {@code JVM}, {@code npm}, {@code NuGet}, {@code Java}. */
    String ecosystem;

    Kind kind;
}
