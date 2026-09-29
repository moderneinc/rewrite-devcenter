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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.binary.Binary;
import org.openrewrite.docker.DockerParser;
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.json.JsonParser;
import org.openrewrite.json.tree.Json;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.protobuf.ProtoParser;
import org.openrewrite.protobuf.tree.Proto;
import org.openrewrite.quark.Quark;
import org.openrewrite.remote.Remote;
import org.openrewrite.ruby.RubyParser;
import org.openrewrite.ruby.tree.Rb;
import org.openrewrite.scala.ScalaParser;
import org.openrewrite.scala.tree.S;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.toml.TomlParser;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.tree.ParseError;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.moderne.devcenter.internal.LineCountAssertions.assertCountMatchesSource;
import static org.assertj.core.api.Assertions.assertThat;

class LineCountersTest {

    @Test
    void everySourceFileTypeIsCountedUnlessItHasNoSourceText() {
        try (ScanResult scan = new ClassGraph().enableClassInfo().acceptPackages("org.openrewrite").scan()) {
            List<String> uncounted = scan.getClassesImplementing(SourceFile.class.getName()).stream()
              .filter(c -> !c.isInterface() && !c.isAbstract())
              .map(ClassInfo::loadClass)
              .filter(c -> !Quark.class.isAssignableFrom(c) && !Binary.class.isAssignableFrom(c) &&
                           !Remote.class.isAssignableFrom(c) && !LineCounters.supports(c))
              .map(Class::getName)
              .toList();
            assertThat(uncounted).isEmpty();
        }
    }

    @Test
    void countsEveryTypeLikeItsSource() {
        Map<Class<?>, SourceFile> parsed = new LinkedHashMap<>();
        parse(parsed, JavaParser.fromJavaVersion().build(), "class A {\n    int x;\n}\n");
        parse(parsed, KotlinParser.builder().build(), "class A {\n    val s = \"\"\"\n    raw\n    \"\"\"\n}\n");
        parse(parsed, GroovyParser.builder().build(), "def s = \"\"\"\n  ${1 + 1}\n\"\"\"\nprintln s\n");
        parse(parsed, ScalaParser.builder().build(), "object A {\n  val s = \"\"\"a\n  b\"\"\"\n}\n");
        parse(parsed, RubyParser.builder().build(), "text = <<~EOS\n  a\n  b\nEOS\nputs text\n");
        parse(parsed, XmlParser.builder().build(), "<?xml version=\"1.0\"?>\n<a>\n  <!-- c -->\n  <![CDATA[x\ny]]>\n</a>\n");
        parse(parsed, YamlParser.builder().build(), "a: |\n  one\n  two\n---\nb: 1\n");
        parse(parsed, JsonParser.builder().build(), "{\n  \"a\": [\n    1,\n    2\n  ]\n}\n");
        parse(parsed, PropertiesParser.builder().build(), "a=one \\\n  two\n# c\nb:3\n");
        parse(parsed, TomlParser.builder().build(), "s = \"\"\"\none\ntwo\"\"\"\n[t]\nk = 1\n");
        parse(parsed, DockerParser.builder().build(), "FROM alpine\nRUN echo a \\\n    && echo b\n");
        parse(parsed, HclParser.builder().build(), "a = <<EOF\none\nEOF\nb = { c = 1, }\n");
        parse(parsed, ProtoParser.builder().build(), "syntax = \"proto2\";\nmessage A {\n  optional string a = 1;\n}\n");
        parse(parsed, PlainTextParser.builder().build(), "one\ntwo");
        parse(parsed, XmlParser.builder().build(), "<a>\n  <b>\n");

        assertThat(parsed.keySet()).containsExactly(J.CompilationUnit.class, K.CompilationUnit.class,
          G.CompilationUnit.class, S.CompilationUnit.class, Rb.CompilationUnit.class, Xml.Document.class,
          Yaml.Documents.class, Json.Document.class, Properties.File.class, Toml.Document.class, Docker.File.class,
          Hcl.ConfigFile.class, Proto.Document.class, PlainText.class, ParseError.class);
        for (SourceFile sourceFile : parsed.values()) {
            assertThat(LineCounters.supports(sourceFile)).as(sourceFile.getClass().getName()).isTrue();
            assertCountMatchesSource(sourceFile, LineCounters::count);
        }
    }

    private static void parse(Map<Class<?>, SourceFile> parsed, Parser parser, String source) {
        SourceFile sourceFile = parser.parse(source).findFirst().orElseThrow();
        parsed.put(sourceFile.getClass(), sourceFile);
    }
}
