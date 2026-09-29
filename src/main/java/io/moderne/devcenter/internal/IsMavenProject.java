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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.xml.tree.Xml;

/**
 * Matches the same documents as {@link FindMavenProject}, without walking every XML document that isn't one.
 */
public class IsMavenProject<P> extends TreeVisitor<Tree, P> {

    @Override
    public @Nullable Tree preVisit(Tree tree, P p) {
        stopAfterPreVisit();
        if (tree instanceof Xml.Document && tree.getMarkers().findFirst(MavenResolutionResult.class).isPresent()) {
            return SearchResult.found(tree);
        }
        return tree;
    }
}
