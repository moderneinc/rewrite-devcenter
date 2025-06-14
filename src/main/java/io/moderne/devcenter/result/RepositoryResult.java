package io.moderne.devcenter.result;

import io.moderne.devcenter.DevCenter;
import io.moderne.devcenter.DevCenterMeasure;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * A result for a repository, containing measures for various DevCenter cards.
 * <p>
 * For any given card, a repository may have no measure at all (return null), which represents
 * the "N/A" case for that repository and card.
 */
@Getter
class RepositoryResult {
    /**
     * For any given repository, there can only be one measure per card. That
     * is why this is expressed as a map of card to the single measure that the repository
     * has for that card (if any).
     */
    private final Map<DevCenter.Card, DevCenterMeasure> upgradesAndMigrations = new HashMap<>();

    /**
     * Since security issues are counted by occurrence, this is a map of security issue type to
     * the number of occurrences.
     */
    private final Map<DevCenterMeasure, Integer> securityIssues = new HashMap<>();
}
