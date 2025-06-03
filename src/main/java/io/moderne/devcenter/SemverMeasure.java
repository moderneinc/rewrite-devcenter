/*
 * Copyright 2025 the original author or authors.
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
package io.moderne.devcenter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.Language;

@RequiredArgsConstructor
@Getter
public enum SemverMeasure implements DevCenterMeasure {
    Major("The version is a major version away from the target version."),
    Minor("The version is a minor version away from the target version."),
    Patch("The version is a patch version away from the target version."),
    Completed("The version is greater than or equal to the target version.");

    @Override
    public @Language("markdown") String getInstanceName() {
        return name();
    }

    private final @Language("markdown") String description;
}
