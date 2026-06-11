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
package io.moderne.devcenter.eol;

import io.moderne.devcenter.DevCenterMeasure;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * End-of-life status of a dependency's release cycle, ordered worst-first.
 * <p>
 * The enum's natural {@link #ordinal()} doubles as the DevCenter measure ordinal, so
 * {@link #EndOfLife} (ordinal {@code 0}) is treated as the "worst" state and surfaces first.
 * The shared {@link io.moderne.devcenter.table.UpgradesAndMigrations} table keeps only the
 * lowest-ordinal measure per repository, so a repository's reported status is the worst status
 * across all of its tracked dependencies.
 */
@RequiredArgsConstructor
@Getter
public enum EolStatus implements DevCenterMeasure {
    EndOfLife("The dependency's release cycle has reached its end-of-life date."),
    EndOfLifeApproaching("The dependency's release cycle reaches end of life within the configured window."),
    Supported("The dependency's release cycle is still supported.");

    private final @Language("markdown") String description;

    @Override
    public @Language("markdown") String getName() {
        return name();
    }

    /**
     * Classify a release cycle relative to a reference date.
     *
     * @param eolFrom        the cycle's end-of-life date, or {@code null} if endoflife.date does
     *                       not publish one.
     * @param isEol          endoflife.date's own end-of-life flag, used only as a fallback when
     *                       {@code eolFrom} is {@code null} (e.g. discontinued products).
     * @param asOf           the reference date to evaluate against.
     * @param approachingDays how many days before {@code eolFrom} a cycle is considered to be
     *                       approaching end of life.
     */
    public static EolStatus of(@Nullable LocalDate eolFrom, boolean isEol, LocalDate asOf, int approachingDays) {
        if (eolFrom != null) {
            if (!eolFrom.isAfter(asOf)) {
                return EndOfLife;
            }
            if (!eolFrom.isAfter(asOf.plusDays(approachingDays))) {
                return EndOfLifeApproaching;
            }
            return Supported;
        }
        return isEol ? EndOfLife : Supported;
    }
}
