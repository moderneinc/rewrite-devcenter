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

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.docker.DockerIsoVisitor;
import org.openrewrite.docker.DockerParser;
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static io.moderne.devcenter.internal.LineCountAssertions.sanitizedPrint;
import static org.assertj.core.api.Assertions.assertThat;

class DockerLineCounterTest {

    @Test
    void countsMatchSource() {
        assertParsedCountsMatchSource(DockerLineCounterTest::parse, Docker.File.class, DockerLineCounter::count, 13, 15,
          """
            # syntax=docker/dockerfile:1
            ARG BASE=eclipse-temurin
            ARG VERSION=21

            FROM ${BASE}:${VERSION}-jdk AS build
            WORKDIR /app
            COPY --chown=app:app gradle/ gradle/
            RUN apt-get update \\
                && apt-get install -y \\
                  curl \\

                  git \\
                # a comment inside the continuation
                && rm -rf /var/lib/apt/lists/*
            ENV JAVA_OPTS="-Xmx512m" \\
                APP_HOME=/app
            ENV OLD_STYLE value with spaces
            LABEL org.opencontainers.image.title="demo" \\
                  version="1.0"

            FROM eclipse-temurin:21-jre@sha256:0123456789abcdef AS runtime
            COPY --from=build /app/build/libs/*.jar /app/app.jar
            EXPOSE 8080 8443/tcp 9000-9010/udp
            USER app:app
            VOLUME ["/data",
                    "/logs"]
            VOLUME /tmp /cache
            HEALTHCHECK --interval=30s --timeout=3s \\
              CMD curl -f http://localhost:8080/ || exit 1
            STOPSIGNAL SIGTERM
            ONBUILD RUN echo onbuild
            SHELL ["/bin/bash", "-c"]
            ENTRYPOINT ["java",
                        "-jar",
                        "/app/app.jar"]
            CMD ["--spring.profiles.active=prod"]
            """,
          """
            FROM alpine
            RUN <<EOF
            set -e
            echo "heredoc"

            EOF
            RUN <<-EOT bash
            \techo "tab-stripped"
            EOT
            RUN <<"EOF"
            echo $HOME
            EOF
            RUN <<A cat /dev/stdin && <<B cat
            first
            A
            second
            B
            COPY <<EOF /etc/config
            key=value
            EOF
            HEALTHCHECK NONE
            MAINTAINER someone@example.com
            """,
          """
            # escape=`
            FROM mcr.microsoft.com/windows/servercore
            RUN powershell -Command `
                Write-Host hello; `
                Write-Host world
            COPY . C:\\app
            # ends with a comment""",
          """
            FROM scratch
            ADD --chmod=755 https://example.com/tool /usr/local/bin/tool
            ADD ["src file", "dest dir/"]
            CMD echo "shell form" 'quoted' $HOME ${USER}
            """,
          "FROM busybox",
          "# only a comment\n"
        );
    }

    @Test
    void markersAreNotCounted() {
        Docker.File docker = parse("FROM alpine\nRUN echo hi\nCMD [\"sh\"]\n").map(Docker.File.class::cast).orElseThrow();
        Docker.File marked = (Docker.File) new DockerIsoVisitor<ExecutionContext>() {
            @Override
            public Docker.Run visitRun(Docker.Run run, ExecutionContext ctx) {
                return SearchResult.found(run, "multi\nline");
            }

            @Override
            public Docker.Cmd visitCmd(Docker.Cmd cmd, ExecutionContext ctx) {
                return Markup.warn(cmd, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(docker, new InMemoryExecutionContext());
        assertThat(marked.printAll()).isNotEqualTo(sanitizedPrint(marked));
        assertThat(DockerLineCounter.count(marked)).isEqualTo(DockerLineCounter.count(docker)).isEqualTo(3);
    }

    private static Optional<SourceFile> parse(String source) {
        return DockerParser.builder().build().parse(source).findFirst();
    }
}
