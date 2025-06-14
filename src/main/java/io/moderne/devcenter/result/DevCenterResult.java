package io.moderne.devcenter.result;

import io.moderne.devcenter.DevCenter;
import io.moderne.devcenter.DevCenterMeasure;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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
