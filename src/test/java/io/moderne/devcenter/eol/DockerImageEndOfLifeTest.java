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

import io.moderne.devcenter.eol.internal.DockerfileImages;
import io.moderne.devcenter.eol.table.EndOfLifeReport;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.docker.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Paths;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.openrewrite.docker.Assertions.docker;
import static org.openrewrite.test.SourceSpecs.text;

class DockerImageEndOfLifeTest implements RewriteTest {

    private DockerImageEndOfLife card() {
        return new DockerImageEndOfLife("Docker base image end of life", null, "2026-05-28",
                "/feeds/test-feed.yaml", null, null);
    }

    @DocumentExample
    @Test
    void classifiesVersionTaggedBaseImageFromDockerLst() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Docker", "image", "node:16.20-alpine", "16.20", "nodejs", "16",
                                        "2023-09-11", EolStatus.EndOfLife.name()))),
                docker(
                        """
                          FROM node:16.20-alpine
                          RUN npm ci
                          """,
                        spec -> spec.path("Dockerfile")
                )
        );
    }

    @Test
    void skipsCodenameTagsAndBuildStagesFromDockerLst() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        // python:3.8-slim matches (EOL); the codename base and the build-stage
                        // reference do not produce rows.
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Docker", "image", "python:3.8-slim", "3.8", "python", "3.8",
                                        "2024-10-07", EolStatus.EndOfLife.name()))),
                docker(
                        """
                          FROM debian:bullseye AS build
                          FROM python:3.8-slim
                          COPY --from=build /app /app
                          """,
                        spec -> spec.path("Dockerfile")
                )
        );
    }

    @Test
    void alsoReadsPlainTextDockerfileFallback() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Docker", "image", "node:16.20-alpine", "16.20", "nodejs", "16",
                                        "2023-09-11", EolStatus.EndOfLife.name()))),
                // A Dockerfile that was parsed as plain text rather than a Docker LST.
                text(
                        """
                          FROM node:16.20-alpine
                          RUN npm ci
                          """,
                        spec -> spec.path("legacy.Dockerfile")
                )
        );
    }

    @Test
    void detectsImageInDockerfileContainingEnvInstruction() {
        // rewrite-docker has historically thrown while printing a Dockerfile that contains an ENV
        // instruction (an upstream Docker$Env.getPairs() NPE). Our card never prints, but this guards
        // that the presence of ENV — the construct behind that upstream bug — does not affect
        // detection of the FROM image.
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Docker", "image", "node:16.20-alpine", "16.20", "nodejs", "16",
                                        "2023-09-11", EolStatus.EndOfLife.name()))),
                docker(
                        """
                          FROM node:16.20-alpine
                          ENV NODE_ENV=production
                          RUN npm ci
                          """,
                        spec -> spec.path("Dockerfile")
                )
        );
    }

    @Test
    void malformedDockerLstDoesNotCrashDetection() {
        // A successfully-parsed Docker.File from an immature rewrite-docker can carry a null child
        // list (here: null stages). Detection must degrade gracefully (return nothing) rather than
        // propagate a NullPointerException out of the recipe visitor.
        Docker.File malformed = new Docker.File(
                UUID.randomUUID(), Paths.get("Dockerfile"), Space.EMPTY, Markers.EMPTY,
                "UTF-8", false, null, null, null, /* stages */ null, Space.EMPTY);
        assertThatCode(() -> assertThat(DockerfileImages.find(malformed)).isEmpty())
                .doesNotThrowAnyException();
    }
}
