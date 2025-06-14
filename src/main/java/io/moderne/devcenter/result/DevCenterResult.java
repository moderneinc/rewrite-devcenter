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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * A result at a particular point in time for a DevCenter.
 */
@RequiredArgsConstructor
@Getter
public class DevCenterResult {
    private final Map<DevCenter.Card, ByMeasure> resultsByCard;

    public void forEach(DevCenter.@Nullable Card card, BiConsumer<? super DevCenterMeasure, Integer> measure) {
        ByMeasure byMeasure = resultsByCard.get(card);
        if (byMeasure != null) {
            byMeasure.getMeasures().forEach(measure);
        }
    }

    @Getter
    public static class ByMeasure {
        Map<DevCenterMeasure, Integer> measures;

        public ByMeasure() {
            this.measures = new TreeMap<>(Comparator.comparing(DevCenterMeasure::ordinal));
        }
    }
}
