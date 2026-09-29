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
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertCountMatchesSource;
import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static io.moderne.devcenter.internal.LineCountAssertions.sanitizedPrint;
import static org.assertj.core.api.Assertions.assertThat;

class GroovyLineCounterTest {

    @Test
    void groovySourcesMatchSource() {
        assertParsedCountsMatchSource(GroovyLineCounterTest::parseGroovy, G.CompilationUnit.class, GroovyLineCounter::count, 7, 15,
          """
            package com.example

            import java.util.List as JList
            import static java.lang.Math.max

            /**
             * A trait.
             */
            trait Greeter {
                String greet(String name) { "Hello, ${name}!" }
            }

            class Person implements Greeter {
                def name, age
                String first = 'a', last = "b"
                static def create(String n) { new Person(name: n) }
            }
            """,
          """
            def name = "world"
            def multi = \"""
                line one ${name.toUpperCase()}
                line ${ [1, 2].collect { it * 2 }.join(',') } two
                \"""
            def single = '''
              raw ${not} interpolated
            '''
            def slashy = /a\\/b
            c/
            def dollarSlashy = $/
              $name /path/ \\
            /$
            def gstr = "$name.length and ${
              name.size()
            }"
            println multi + single + slashy + dollarSlashy + gstr
            """,
          """
            def map = [
                a: 1,
                'b': [x: 2, y: [3, 4,],],
                (key): { k,
                         v -> k + v },
            ]
            def list = [1, 2,
                3,
            ]
            def (first, second) = [1, 2]
            def spread = [*list, *[5, 6]]
            def range = 1..<10
            def matches = 'abc' ==~ /a.c/
            def elvis = map.missing ?:
                'default'
            def cast = map.a as
                String
            def pointer = this.&println
            def safe = map?.b*.size()
            assert map.a == 1,
                'message'
            for (x in
                 list) {
                println x;
            }
            """,
          """
            plugins {
                id 'java'
                id "org.springframework.boot" version "3.2.0" apply false
            }

            repositories {
                mavenCentral()
                maven { url = uri("https://repo.example.com") }
            }

            dependencies {
                implementation "org.springframework.boot:spring-boot-starter-web:${springBootVersion}"
                testImplementation('org.junit.jupiter:junit-jupiter:5.10.0') {
                    exclude group: 'org.hamcrest'
                }
                compileOnly(
                    'org.projectlombok:lombok:1.18.30',
                )
            }

            tasks.named('test') {
                useJUnitPlatform()
            }
            task hello() {
                doLast { println 'hello' }
            }
            """,
          """
            #!/usr/bin/env groovy
            class Deep {
                def run() {
                    [1, 2, 3].each { a ->
                        [4, 5].each { b ->
                            if (a > b) {
                                switch (a) {
                                    case 1:
                                        try {
                                            println a
                                        } catch (Exception e) {
                                            throw new RuntimeException(e)
                                        } finally {
                                            println 'done'
                                        }
                                        break
                                    default:
                                        println b
                                }
                            }
                        }
                    }
                }
            }
            // trailing comment
            """,
          """
            class Marked {
                static
                def create() { 1 }
                def a,
                    b
            }
            foo(
            ) {
                println it
            }
            bar(1,
                2,
            )
            def c = a ?:
              b ?: c
            def e = map
                .missing ?: 'default'
            def d = a
              as String
            """,
          "println 'no trailing newline'",
          "def x = 1 /* ends in a\nblock comment */",
          "x",
          ";"
        );
    }

    @Test
    void gradleScriptsMatchSource() {
        assertParsedCountsMatchSource(GroovyLineCounterTest::parseGradle, G.CompilationUnit.class, GroovyLineCounter::count, 11, 15,
          """
            buildscript {
                ext {
                    kotlinVersion = '1.9.0'
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
                }
            }
            apply plugin: 'java'
            group = 'com.example'
            version = '1.0'
            sourceCompatibility = JavaVersion.VERSION_17

            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.group == 'org.apache.logging.log4j') {
                        details.useVersion '2.17.1'
                    }
                }
            }
            """
        );
    }

    @Test
    void markersAreNotCounted() {
        G.CompilationUnit cu = (G.CompilationUnit) parseGroovy("def a = 1\nprintln a\n").orElseThrow();
        G.CompilationUnit marked = (G.CompilationUnit) new GroovyIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                return SearchResult.found(identifier, "multi\nline");
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                return Markup.warn(super.visitMethodInvocation(method, p), new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(cu, 0);
        assertThat(marked.printAll()).isNotEqualTo(sanitizedPrint(marked));
        assertThat(GroovyLineCounter.count(marked)).isEqualTo(GroovyLineCounter.count(cu)).isEqualTo(2);
        assertCountMatchesSource(marked, GroovyLineCounter::count);
    }

    private static Optional<SourceFile> parseGroovy(String source) {
        return GroovyParser.builder().build().parse(source).findFirst();
    }

    private static Optional<SourceFile> parseGradle(String source) {
        return GradleParser.builder().build().parse(source).findFirst();
    }
}
