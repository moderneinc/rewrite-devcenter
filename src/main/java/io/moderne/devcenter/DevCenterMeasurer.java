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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface DevCenterMeasurer<Measures extends Enum<?> & DevCenterMeasure> {
    default List<String> getMeasures() {
        Measures[] values = null;
        for (Type generic : getClass().getGenericInterfaces()) {
            if (generic instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) generic;
                if (pt.getRawType().equals(DevCenterMeasurer.class)) {
                    Type actualTypeArgument = pt.getActualTypeArguments()[0];
                    //noinspection unchecked
                    values = (Measures[]) ((Class<Enum<?>>) actualTypeArgument).getEnumConstants();
                }
            }
        }
        if (values == null) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();
        for (Measures value : values) {
            names.add(value.getDisplayName());
        }

        return names;
    }
}
