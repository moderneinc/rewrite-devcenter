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
package io.moderne.devcenter.result;

import io.moderne.devcenter.DevCenter;
import io.moderne.devcenter.DevCenterMeasure;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A result for an organization, containing measures for various DevCenter cards.
 */
@Getter
class OrganizationResult {
    /**
     * Note that for any given repository, there can only be one measure per card. That
     * is why this is expressed as a map of card to the single measure that the repository
     * has for that card.
     */
    private final Map<DevCenter.Card, DevCenterMeasure> upgradesAndMigrations = new HashMap<>();

    private final List<DevCenterMeasure> securityIssues = new ArrayList<>();
}
