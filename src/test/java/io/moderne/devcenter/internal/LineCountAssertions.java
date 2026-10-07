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
package io.moderne.devcenter.internal;

import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.marker.Marker;
import org.openrewrite.tree.ParseError;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares line counters with the printed source, which is the oracle only in tests. Markers aren't source,
 * so the print omits their text.
 */
final class LineCountAssertions {

    private LineCountAssertions() {
    }

    static String sanitizedPrint(SourceFile sourceFile) {
        return sourceFile.printAll(new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.SANITIZED));
    }

    static long printedLines(String printed) {
        return printed.isEmpty() ? 0 :
          printed.chars().filter(c -> c == '\n').count() + (printed.endsWith("\n") ? 0 : 1);
    }

    static <T extends SourceFile> void assertCountMatchesSource(T sourceFile, ToLongFunction<T> counter) {
        String printed = sanitizedPrint(sourceFile);
        assertThat(counter.applyAsLong(sourceFile)).as(printed).isEqualTo(printedLines(printed));
    }

    /** Asserts that the text markers add to {@code marked} when printed isn't counted. */
    static void assertMarkersNotCounted(SourceFile unmarked, Tree marked) {
        SourceFile markedFile = (SourceFile) marked;
        assertThat(markedFile.printAll()).as("the marker prints text").isNotEqualTo(unmarked.printAll());
        assertThat(LineCounters.count(markedFile)).isEqualTo(LineCounters.count(unmarked));
        assertCountMatchesSource(markedFile, LineCounters::count);
    }

    /** A third-party marker that prints several lines of its own. */
    record MultilineMarker(UUID id) implements Marker {
        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public MultilineMarker withId(UUID id) {
            return new MultilineMarker(id);
        }

        @Override
        public String print(Cursor cursor, UnaryOperator<String> commentWrapper, boolean verbose) {
            return "one\ntwo\n";
        }
    }

    /**
     * Each fixture must parse to {@code type}; its variants and random mutations may also parse to a
     * {@link ParseError}, whose count is compared too.
     *
     * @return the number of comparisons made
     */
    static <T extends SourceFile> int assertParsedCountsMatchSource(Function<String, Optional<SourceFile>> parser,
                                                                    Class<T> type, ToLongFunction<T> counter,
                                                                    long seed, int mutationsPerFixture,
                                                                    String... fixtures) {
        Random random = new Random(seed);
        int comparisons = 0;
        for (String fixture : fixtures) {
            Optional<SourceFile> parsed = parser.apply(fixture);
            assertThat(parsed).as("fixture parses: %s", fixture).hasValueSatisfying(s -> assertThat(s).isInstanceOf(type));
            Set<String> sources = new LinkedHashSet<>(variants(fixture));
            for (int i = 0; i < mutationsPerFixture; i++) {
                sources.add(mutate(fixture, random));
            }
            for (String source : sources) {
                Optional<SourceFile> sourceFile = parser.apply(source);
                if (sourceFile.isPresent()) {
                    assertMatches(sourceFile.get(), type, counter, source);
                    comparisons++;
                }
            }
        }
        return comparisons;
    }

    private static <T extends SourceFile> void assertMatches(SourceFile sourceFile, Class<T> type,
                                                             ToLongFunction<T> counter, String source) {
        String printed = sanitizedPrint(sourceFile);
        long actual;
        if (type.isInstance(sourceFile)) {
            actual = counter.applyAsLong(type.cast(sourceFile));
        } else {
            assertThat(sourceFile).as("parsed %s", escape(source)).isInstanceOf(ParseError.class);
            actual = Newlines.lineCount(((ParseError) sourceFile).getText());
        }
        assertThat(actual).as("%s from %s", sourceFile.getClass().getSimpleName(), escape(source))
          .isEqualTo(printedLines(printed));
    }

    static List<String> variants(String source) {
        List<String> variants = new ArrayList<>();
        variants.add(source);
        variants.add(source.replace("\r\n", "\n").replace("\n", "\r\n"));
        String trimmed = source;
        while (trimmed.endsWith("\n") || trimmed.endsWith("\r")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        variants.add(trimmed);
        variants.add(source + "\n");
        variants.add("\n" + source);
        variants.add("");
        return variants;
    }

    /** Deletes, duplicates, swaps, blanks, dedents or CRLF-terminates random lines. */
    static String mutate(String source, Random random) {
        List<String> lines = new ArrayList<>(List.of(source.split("\n", -1)));
        int edits = 1 + random.nextInt(3);
        for (int e = 0; e < edits && !lines.isEmpty(); e++) {
            int i = random.nextInt(lines.size());
            switch (random.nextInt(6)) {
                case 0 -> lines.remove(i);
                case 1 -> lines.add(i, lines.get(i));
                case 2 -> {
                    int j = random.nextInt(lines.size());
                    String line = lines.get(i);
                    lines.set(i, lines.get(j));
                    lines.set(j, line);
                }
                case 3 -> lines.add(i, "");
                case 4 -> lines.set(i, lines.get(i).stripLeading());
                default -> lines.set(i, lines.get(i) + "\r");
            }
        }
        return String.join("\n", lines);
    }

    static String escape(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }
}
