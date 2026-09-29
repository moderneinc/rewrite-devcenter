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
package io.moderne.devcenter.internal;

import org.openrewrite.SourceFile;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.golang.tree.Go;
import org.openrewrite.golang.tree.GoMod;
import org.openrewrite.golang.tree.GoSum;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.protobuf.tree.Proto;
import org.openrewrite.python.tree.Py;
import org.openrewrite.ruby.tree.Rb;
import org.openrewrite.scala.tree.S;
import org.openrewrite.text.PlainText;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.tree.ParseError;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Counts lines of code in a source file from its source text, without printing it.
 * <p>
 * Each supported type gets a dedicated in-process visitor that walks the LST directly, which matters most for
 * languages whose printer runs out-of-process over RPC (JavaScript/TypeScript, Python, C#, Go, go.mod, go.sum).
 * Text that markers would print isn't source and isn't counted. Other types aren't counted at all.
 * <p>
 * Types are matched by class name, so a language module missing at runtime is never loaded, and nothing is cached
 * on classes that a recipe classloader's parent loaded.
 */
public final class LineCounters {

    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            "org.openrewrite.java.tree.J$CompilationUnit",
            "org.openrewrite.kotlin.tree.K$CompilationUnit",
            "org.openrewrite.groovy.tree.G$CompilationUnit",
            "org.openrewrite.scala.tree.S$CompilationUnit",
            "org.openrewrite.ruby.tree.Rb$CompilationUnit",
            "org.openrewrite.javascript.tree.JS$CompilationUnit",
            "org.openrewrite.python.tree.Py$CompilationUnit",
            "org.openrewrite.csharp.tree.Cs$CompilationUnit",
            "org.openrewrite.golang.tree.Go$CompilationUnit",
            "org.openrewrite.golang.tree.GoMod",
            "org.openrewrite.golang.tree.GoSum",
            "org.openrewrite.xml.tree.Xml$Document",
            "org.openrewrite.yaml.tree.Yaml$Documents",
            "org.openrewrite.json.tree.Json$Document",
            "org.openrewrite.properties.tree.Properties$File",
            "org.openrewrite.toml.tree.Toml$Document",
            "org.openrewrite.docker.tree.Docker$File",
            "org.openrewrite.hcl.tree.Hcl$ConfigFile",
            "org.openrewrite.protobuf.tree.Proto$Document",
            "org.openrewrite.text.PlainText",
            "org.openrewrite.tree.ParseError"
    ));

    private LineCounters() {
    }

    public static boolean supports(SourceFile sourceFile) {
        return supports(sourceFile.getClass());
    }

    static boolean supports(Class<?> type) {
        return SUPPORTED.contains(type.getName());
    }

    /**
     * @return the number of lines, or 0 for a source file that isn't {@link #supports(SourceFile) supported}.
     */
    public static long count(SourceFile sourceFile) {
        switch (sourceFile.getClass().getName()) {
            case "org.openrewrite.java.tree.J$CompilationUnit":
                return JavaLineCounter.count((J.CompilationUnit) sourceFile);
            case "org.openrewrite.kotlin.tree.K$CompilationUnit":
                return KotlinLineCounter.count((K.CompilationUnit) sourceFile);
            case "org.openrewrite.groovy.tree.G$CompilationUnit":
                return GroovyLineCounter.count((G.CompilationUnit) sourceFile);
            case "org.openrewrite.scala.tree.S$CompilationUnit":
                return ScalaLineCounter.count((S.CompilationUnit) sourceFile);
            case "org.openrewrite.ruby.tree.Rb$CompilationUnit":
                return RubyLineCounter.count((Rb.CompilationUnit) sourceFile);
            case "org.openrewrite.javascript.tree.JS$CompilationUnit":
                return JavaScriptLineCounter.count((JS.CompilationUnit) sourceFile);
            case "org.openrewrite.python.tree.Py$CompilationUnit":
                return PythonLineCounter.count((Py.CompilationUnit) sourceFile);
            case "org.openrewrite.csharp.tree.Cs$CompilationUnit":
                return CSharpLineCounter.count((Cs.CompilationUnit) sourceFile);
            case "org.openrewrite.golang.tree.Go$CompilationUnit":
                return GoLineCounter.count((Go.CompilationUnit) sourceFile);
            case "org.openrewrite.golang.tree.GoMod":
                return GoModLineCounter.count((GoMod) sourceFile);
            case "org.openrewrite.golang.tree.GoSum":
                return GoSumLineCounter.count((GoSum) sourceFile);
            case "org.openrewrite.xml.tree.Xml$Document":
                return XmlLineCounter.count((Xml.Document) sourceFile);
            case "org.openrewrite.yaml.tree.Yaml$Documents":
                return YamlLineCounter.count((Yaml.Documents) sourceFile);
            case "org.openrewrite.json.tree.Json$Document":
                return JsonLineCounter.count((Json.Document) sourceFile);
            case "org.openrewrite.properties.tree.Properties$File":
                return PropertiesLineCounter.count((Properties.File) sourceFile);
            case "org.openrewrite.toml.tree.Toml$Document":
                return TomlLineCounter.count((Toml.Document) sourceFile);
            case "org.openrewrite.docker.tree.Docker$File":
                return DockerLineCounter.count((Docker.File) sourceFile);
            case "org.openrewrite.hcl.tree.Hcl$ConfigFile":
                return HclLineCounter.count((Hcl.ConfigFile) sourceFile);
            case "org.openrewrite.protobuf.tree.Proto$Document":
                return ProtoLineCounter.count((Proto.Document) sourceFile);
            case "org.openrewrite.text.PlainText":
                return PlainTextLineCounter.count((PlainText) sourceFile);
            case "org.openrewrite.tree.ParseError":
                return Newlines.lineCount(((ParseError) sourceFile).getText());
            default:
                return 0;
        }
    }
}
