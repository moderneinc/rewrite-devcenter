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

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.moderne.devcenter.DevCenter;
import io.moderne.organizations.Organization;
import io.moderne.organizations.RepositoryId;
import io.moderne.organizations.RepositorySpec;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.internal.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
class UpgradesAndMigrationsReader {
    private final DevCenter devCenter;
    private final Map<RepositoryId, List<Organization<RepositoryResult>>> repositoryMap;

    public void read(Reader upgradesAndMigrations) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);

        CsvParser parser = new CsvParser(settings);
        parser.beginParsing(upgradesAndMigrations);

        try {
            List<UpgradesAndMigrationsColumn> headers = null;
            @Nullable String[] row;

            while ((row = parser.parseNext()) != null) {
                if (row.length == 0 || (row.length == 1 && StringUtils.isBlank(row[0]))) {
                    continue; // Skip empty lines
                }

                if (headers == null) {
                    headers = new ArrayList<>();
                    for (String header : row) {
                        if (header != null) {
                            headers.add(UpgradesAndMigrationsColumn.fromString(header.trim()));
                        }
                    }
                } else {
                    String origin = null;
                    String path = null;
                    String branch = null;
                    String cardName = null;
                    Integer ordinal = null;

                    for (int i = 0; i < row.length && i < headers.size(); i++) {
                        String value = row[i];
                        if (StringUtils.isBlank(value)) {
                            value = null;
                        }
                        switch (headers.get(i)) {
                            case REPOSITORY_ORIGIN:
                                origin = value;
                                break;
                            case REPOSITORY_PATH:
                                path = value;
                                break;
                            case REPOSITORY_BRANCH:
                                branch = value;
                                break;
                            case CARD:
                                cardName = value;
                                break;
                            case ORDINAL:
                                ordinal = Integer.parseInt(requireNonNull(value));
                                break;
                        }
                    }

                    assert origin != null && path != null && ordinal != null;

                    RepositoryId id = new RepositoryId(origin, path, branch);

                    nextOrg:
                    for (Organization<RepositoryResult> org : repositoryMap.getOrDefault(id, emptyList())) {
                        // TODO should we add org.getRepository(id) to moderne-organizations-format?
                        for (RepositorySpec<RepositoryResult> repo : org.getRepositories()) {
                            if (repo.getId().equals(id)) {
                                RepositoryResult result = requireNonNull(repo.getMaterialized());
                                for (DevCenter.Card card : devCenter.getCards()) {
                                    if (card.getName().equals(cardName)) {
                                        result.getUpgradesAndMigrations().put(card,
                                                card.getMeasures().get(ordinal));
                                        continue nextOrg;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Unable to read from CSV", new IOException(e));
        } finally {
            parser.stopParsing();
        }
    }

    @RequiredArgsConstructor
    private enum UpgradesAndMigrationsColumn {
        REPOSITORY_ORIGIN("repositoryOrigin"),
        REPOSITORY_PATH("repositoryPath"),
        REPOSITORY_BRANCH("repositoryBranch"),
        CARD("card"),
        ORDINAL("ordinal"),
        UNKNOWN("unknown");

        private final String key;

        public static UpgradesAndMigrationsColumn fromString(String key) {
            for (UpgradesAndMigrationsColumn column : values()) {
                if (column.key.equalsIgnoreCase(key)) {
                    return column;
                }
            }
            return UNKNOWN;
        }
    }
}
