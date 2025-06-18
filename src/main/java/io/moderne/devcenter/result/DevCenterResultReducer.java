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
import io.moderne.organizations.Organization;
import io.moderne.organizations.RepositoryId;
import io.moderne.organizations.RepositorySpec;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.*;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
public class DevCenterResultReducer {
    private final DevCenter devCenter;

    /**
     * Holds the results for each repository, which will be reduced to a DevCenterResult
     * for a given organization.
     */
    private final Organization<RepositoryResult> results;

    /**
     * Reduces the individual repository results for a given organization to a DevCenterResult.
     *
     * @param organization The organization to calculate the result for.
     * @return A DevCenterResult containing the aggregated results for the organization.
     */
    public DevCenterResult reduce(Organization<?> organization) {
        Map<DevCenter.Card, DevCenterResult.ByMeasure> resultsByCard = new LinkedHashMap<>();

        // Find the organization in the repository results materialization.
        Organization<RepositoryResult> result = results.getChild(organization.getPathTo(null).toArray(new String[0]));

        Set<RepositoryId> seen = new HashSet<>();
        result.forEachOrganization(org -> {
            for (RepositorySpec<RepositoryResult> repository : org.getRepositories()) {
                if (!seen.add(repository.getId())) {
                    continue; // Skip if we've already processed this repository
                }

                RepositoryResult repositoryResult = requireNonNull(repository.getMaterialized());
                for (DevCenter.Card upgrade : devCenter.getUpgradesAndMigrations()) {
                    DevCenterResult.ByMeasure byMeasure = resultsByCard.computeIfAbsent(
                            upgrade,
                            k -> new DevCenterResult.ByMeasure(upgrade.getMeasures())
                    );
                    DevCenterMeasure detectedMeasure = repositoryResult.getUpgradesAndMigrations().get(upgrade);
                    if (detectedMeasure != null) {
                        byMeasure.getMeasures()
                                .merge(detectedMeasure, 1, Integer::sum);
                    }
                }

                DevCenter.Card security = devCenter.getSecurity();
                if (security != null) {
                    DevCenterResult.ByMeasure byMeasure = resultsByCard.computeIfAbsent(
                            security,
                            card -> new DevCenterResult.ByMeasure(security.getMeasures())
                    );
                    for (Map.Entry<DevCenterMeasure, Integer> sec : repositoryResult.getSecurityIssues().entrySet()) {
                        byMeasure.getMeasures().merge(sec.getKey(), sec.getValue(), Integer::sum);
                    }
                }
            }
        });

        return new DevCenterResult(resultsByCard);
    }

    public static DevCenterResultReducer fromDataTables(
            DevCenter devCenter,
            Organization<?> root,
            @Nullable Reader upgradesAndMigrationsCsv,
            @Nullable Reader securityIssuesCsv) {
        assert upgradesAndMigrationsCsv != null || securityIssuesCsv != null :
                "At least one of upgradesAndMigrationsCsv or securityIssuesCsv must be provided";

        Organization<RepositoryResult> results = root.rematerialize(RepositoryResult::new);
        Map<RepositoryId, List<Organization<RepositoryResult>>> repositoryMap = repositoryMap(results, new HashMap<>());

        if (upgradesAndMigrationsCsv != null) {
            new UpgradesAndMigrationsReader(devCenter, repositoryMap).read(upgradesAndMigrationsCsv);
        }
        if (securityIssuesCsv != null) {
            new SecurityIssuesReader(devCenter, repositoryMap).read(securityIssuesCsv);
        }

        return new DevCenterResultReducer(devCenter, results);
    }

    /**
     * Recursively builds a map of repository IDs to the organizations that contain them.
     * This allows for constant time lookup of which organizations contain a given repository as
     * we read each row of a DevCenter data table CSV.
     *
     * @param org The organization to recurse from.
     * @param map The map to populate with repository IDs and their associated organizations.
     * @return A map of repository IDs to lists of organizations that contain them.
     */
    static Map<RepositoryId, List<Organization<RepositoryResult>>> repositoryMap(
            Organization<RepositoryResult> org,
            Map<RepositoryId, List<Organization<RepositoryResult>>> map) {
        for (RepositorySpec<RepositoryResult> r : org.getRepositories()) {
            map.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(org);
        }
        for (Organization<RepositoryResult> c : org.getChildren()) {
            repositoryMap(c, map);
        }
        return map;
    }
}
