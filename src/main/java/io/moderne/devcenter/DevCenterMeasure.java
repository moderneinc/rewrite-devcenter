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

import org.intellij.lang.annotations.Language;
import org.openrewrite.NlsRewrite;

public interface DevCenterMeasure {
    @Language("markdown")
    @NlsRewrite.DisplayName
    String getName();

    @Language("markdown")
    @NlsRewrite.Description
    String getDescription();

    /**
     * Used to sort measures in a defined order.
     *
     * @return The ordinal position of this measure.
     */
    int ordinal();
}
