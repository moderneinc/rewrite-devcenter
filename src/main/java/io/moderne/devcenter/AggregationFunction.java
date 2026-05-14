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
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;

/**
 * Reduces the per-row values of a column, drawn from the rows of an upstream
 * data table for a single repository, to a single representative value that the
 * {@link BucketedMetricCard} then assigns to a {@link Bucket}.
 * <p>
 * The input list has one entry per row in the upstream data table for this
 * repository. Entries may be {@code null} when the row's value for the target
 * column is missing. Numeric aggregations skip {@code null} and non-numeric
 * entries; {@link #COUNT} and {@link #UNIQUE} operate on the raw entries.
 * <p>
 * A function returns {@code null} to signal "no meaningful result"; the card
 * skips inserting a row for the repository in that case.
 */
public enum AggregationFunction {

    MIN {
        @Override
        public @Nullable Double apply(List<?> values) {
            return values.stream()
                    .map(AggregationFunction::toDouble)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        }
    },

    MAX {
        @Override
        public @Nullable Double apply(List<?> values) {
            return values.stream()
                    .map(AggregationFunction::toDouble)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        }
    },

    SUM {
        @Override
        public @Nullable Double apply(List<?> values) {
            return values.stream()
                    .map(AggregationFunction::toDouble)
                    .filter(Objects::nonNull)
                    .reduce(Double::sum)
                    .orElse(null);
        }
    },

    AVERAGE {
        @Override
        public @Nullable Double apply(List<?> values) {
            DoubleSummaryStatistics stats = values.stream()
                    .map(AggregationFunction::toDouble)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
            return stats.getCount() == 0 ? null : stats.getAverage();
        }
    },

    /**
     * Number of rows the upstream data table emitted for this repository.
     * Returns the total row count regardless of whether the configured column
     * has a value in each row. An empty input yields {@code 0}, which is a
     * meaningful bucket input — the caller does <em>not</em> skip insertion.
     */
    COUNT {
        @Override
        public Double apply(List<?> values) {
            return (double) values.size();
        }
    },

    /**
     * Number of distinct non-null values of the configured column across all
     * rows for this repository. An empty input yields {@code 0}.
     */
    UNIQUE {
        @Override
        public Double apply(List<?> values) {
            return (double) values.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
        }
    };

    /**
     * Reduce the per-row values of a column to a single representative value,
     * or {@code null} if no meaningful result can be produced.
     */
    public abstract @Nullable Double apply(List<?> values);

    /**
     * Case-insensitive lookup used by Jackson when deserializing this enum
     * from YAML/JSON option values. Not all OpenRewrite consumers configure
     * their {@link com.fasterxml.jackson.databind.ObjectMapper} with
     * {@code ACCEPT_CASE_INSENSITIVE_ENUMS} (notably the marketplace's
     * Maven recipe bundle reader), so we provide an explicit {@link JsonCreator}
     * that doesn't depend on mapper configuration.
     */
    @JsonCreator
    public static AggregationFunction fromString(String value) {
        for (AggregationFunction f : values()) {
            if (f.name().equalsIgnoreCase(value)) {
                return f;
            }
        }
        throw new IllegalArgumentException(
                "Unknown aggregation: \"" + value + "\". Expected one of MIN, MAX, SUM, AVERAGE, COUNT, UNIQUE.");
    }

    private static @Nullable Double toDouble(@Nullable Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
