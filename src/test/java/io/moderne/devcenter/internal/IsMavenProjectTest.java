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
import org.openrewrite.Preconditions;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IsMavenProjectTest {

    @Test
    void matchesTheSameSourcesAsFindMavenProject() {
        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> sources = new ArrayList<>();
        //language=xml
        sources.addAll(MavenParser.builder().build().parse(ctx,
          """
            <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0</version>
            </project>
            """
        ).toList());
        //language=xml
        sources.addAll(XmlParser.builder().build().parse(ctx,
          """
            <beans>
                <bean id="a" class="com.example.A"/>
            </beans>
            """
        ).toList());
        sources.addAll(JavaParser.fromJavaVersion().build().parse(ctx, "class A {}").toList());
        sources.add(PlainText.builder().sourcePath(Path.of("pom.xml")).text("<project/>").build());

        assertThat(sources.getFirst().getMarkers().findFirst(MavenResolutionResult.class)).isPresent();
        for (SourceFile source : sources) {
            assertThat(matches(new IsMavenProject<>(), source, ctx))
              .as(source.getSourcePath().toString())
              .isEqualTo(matches(new FindMavenProject().getVisitor(), source, ctx))
              .isEqualTo(source == sources.getFirst());
        }
    }

    private static boolean matches(TreeVisitor<?, ExecutionContext> precondition, SourceFile source, ExecutionContext ctx) {
        // Evaluated as recipes do, which skips visitors that don't accept the source file
        return Preconditions.or(precondition).visit(source, ctx) != source;
    }
}
