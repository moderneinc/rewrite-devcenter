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

import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

public class DevCenterValidationException extends Exception {
    @Getter
    private final List<String> validationErrors;

    public DevCenterValidationException(List<String> validationErrors) {
        super("DevCenter validation failed:\n" + validationErrors.stream()
                .map(error -> " - " + error)
                .collect(Collectors.joining("\n")));
        this.validationErrors = validationErrors;
    }
}
