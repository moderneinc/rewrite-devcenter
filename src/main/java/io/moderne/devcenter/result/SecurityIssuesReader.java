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
import io.moderne.organizations.Organization;
import io.moderne.organizations.RepositoryId;
import io.moderne.organizations.RepositorySpec;
import lombok.RequiredArgsConstructor;
import nbbrd.picocsv.Csv;
import org.openrewrite.internal.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
class SecurityIssuesReader {
    private final DevCenter devCenter;
    private final Map<RepositoryId, List<Organization<RepositoryResult>>> repositoryMap;

    public void read(Reader securitiesIssues) {
        try (Csv.Reader csv = Csv.Reader.of(Csv.Format.DEFAULT, Csv.ReaderOptions.builder()
                .lenientSeparator(true).build(), securitiesIssues)) {

            List<SecurityIssuesColumn> headers = null;
            while (csv.readLine()) {
                if (csv.isComment()) {
                    continue;
                }
                if (csv.readField()) { // skips empty lines
                    if (headers == null) {
                        headers = new ArrayList<>();
                        do {
                            headers.add(SecurityIssuesColumn.fromString(csv.toString().trim()));
                        } while (csv.readField());
                    } else {
                        String origin = null, path = null, branch = null;
                        Integer ordinal = null;
                        int i = 0;
                        do {
                            if (i >= headers.size()) {
                                throw new IllegalStateException("More columns in CSV than headers");
                            }
                            String value = csv.toString().trim();
                            if (StringUtils.isBlank(value)) {
                                value = null;
                            }
                            switch (headers.get(i++)) {
                                case REPOSITORY_ORIGIN:
                                    origin = value;
                                    break;
                                case REPOSITORY_PATH:
                                    path = value;
                                    break;
                                case REPOSITORY_BRANCH:
                                    branch = value;
                                    break;
                                case ORDINAL:
                                    ordinal = Integer.parseInt(requireNonNull(value));
                                    break;
                            }
                        } while (csv.readField());

                        assert origin != null && path != null && ordinal != null;

                        RepositoryId id = new RepositoryId(origin, path, branch);

                        nextOrg:
                        for (Organization<RepositoryResult> org : repositoryMap.getOrDefault(id, Collections.emptyList())) {
                            // TODO should we add org.getRepository(id) to moderne-organizations-format?
                            for (RepositorySpec<RepositoryResult> repo : org.getRepositories()) {
                                if (repo.getId().equals(id)) {
                                    RepositoryResult result = requireNonNull(repo.getMaterialized());
                                    DevCenter.Card security = requireNonNull(devCenter.getSecurity());
                                    result.getSecurityIssues().merge(security.getMeasures().get(ordinal), 1, Integer::sum);
                                    continue nextOrg;
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read from CSV", e);
        }
    }

    @RequiredArgsConstructor
    private enum SecurityIssuesColumn {
        REPOSITORY_ORIGIN("repositoryOrigin"),
        REPOSITORY_PATH("repositoryPath"),
        REPOSITORY_BRANCH("repositoryBranch"),
        ORDINAL("ordinal"),
        UNKNOWN("unknown");

        private final String key;

        public static SecurityIssuesColumn fromString(String key) {
            for (SecurityIssuesColumn column : values()) {
                if (column.key.equalsIgnoreCase(key)) {
                    return column;
                }
            }
            return UNKNOWN;
        }
    }
}
