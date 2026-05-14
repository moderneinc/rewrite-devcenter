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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

/**
 * One bucket in a {@link BucketedMetricCard}'s configuration: a display
 * {@link #name} paired with an inclusive lower bound {@link #moreThan}.
 * A value is considered to fall into this bucket when
 * {@code value &gt;= moreThan}.
 * <p>
 * Bucket position in the configured list determines the {@link DevCenterMeasure}
 * ordinal — the first bucket maps to ordinal {@code 0}, the last to ordinal
 * {@code size - 1} — so callers control which end of the scale is treated as
 * "worst" vs. "best" by the consuming DevCenter visualization.
 */
@Getter
@EqualsAndHashCode
@ToString
public class Bucket {

    @Language("markdown")
    private final String name;

    private final Number moreThan;

    @JsonCreator
    public Bucket(@JsonProperty("name") String name,
                  @JsonProperty("moreThan") Number moreThan) {
        this.name = name;
        this.moreThan = moreThan;
    }

    /**
     * Pick the bucket whose {@code moreThan} is the largest value at or below
     * {@code value}, or {@code null} if no bucket applies.
     */
    public static @Nullable Bucket match(double value, Bucket[] buckets) {
        Bucket selected = null;
        for (Bucket bucket : buckets) {
            double lowerBound = bucket.getMoreThan().doubleValue();
            if (value < lowerBound) {
                continue;
            }
            if (selected == null || lowerBound > selected.getMoreThan().doubleValue()) {
                selected = bucket;
            }
        }
        return selected;
    }
}
