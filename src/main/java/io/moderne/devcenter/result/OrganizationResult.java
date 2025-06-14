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
