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
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.ruby.RubyIsoVisitor;
import org.openrewrite.ruby.RubyParser;
import org.openrewrite.ruby.tree.Rb;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.assertj.core.api.Assertions.assertThat;

class RubyLineCounterTest {

    @Test
    void heredocs() {
        assertMatchesSource(
          "x = <<~EOS\n  hi\n  there\nEOS\n",
          "x = <<~EOS\n  hi\nEOS",
          "sql = <<-SQL.strip\n    select *\n    from t\n    SQL\nputs sql\n",
          "foo(<<~A, <<~B)\n  a\nA\n  b\nB\ny = 1\n",
          "raise <<~MSG if broken?\n  #{name} is broken\n  and #{other}\nMSG\n",
          "text = <<'RAW'\nno #{interpolation}\nRAW\n",
          "def help\n  <<~TXT\n    usage: tool [options]\n\n    options:\n      -h  help\n  TXT\nend\n",
          "a = [<<~ONE, <<~TWO].join # joined\n  one\nONE\n  two\nTWO\n"
        );
    }

    @Test
    void commentsAndData() {
        assertMatchesSource(
          "# leading\n=begin\nblock\ncomment\n=end\nx = 1 # tail",
          "x = 1\n# ends in a comment\n",
          "x = 1\n# ends in a comment",
          "puts DATA.read\n__END__\ndata\nmore data\n",
          "puts DATA.read\n__END__\nno trailing newline",
          "# only a comment\n"
        );
    }

    @Test
    void stringsAndLiterals() {
        assertMatchesSource(
          "a = \"x #{y} z\"\nb = 'multi\nline'\nc = \"#{\n  nested(\"#{deep}\")\n}\"\n",
          "w = %w[one two\n  three]\ni = %i(a b c)\nq = %q{single\nquoted}\nr = %r{a\nb}x\n",
          "s = :sym\nt = :\"quoted sym\"\nh = { a: 1, 'b' => 2, :c => 3, }\n",
          "h = {\n  a: 1,\n  b: 2,\n}\nlist = [\n  [1, 2,],\n  3,\n]\nitems.each do |a,\n  b|\nend\n",
          "c = ?a\nn = 3r + 2i\nrange = (1..10).step(2)\n",
          "puts \"line one\" \\\n  \"line two\"\n"
        );
    }

    @Test
    void blocksAndChains() {
        assertMatchesSource(
          "items.each do |item, index|\n  puts item\nend\n",
          "items.map { |x| x * 2 }\n  .select { |x|\n    x > 2\n  }\n  .each(&:freeze)\n",
          "result = list\n  .map(&:to_s)\n  &.first\n",
          "l = ->(a, b) { a + b }\nm = lambda do |x|\n  x\nend\nn = -> { 1 }\n",
          "def call(a, b = 1, *rest, key:, other: 2, **opts, &blk)\n  yield(a, b)\n  other(a,\n    b,\n  )\nend\n"
        );
    }

    @Test
    void controlFlow() {
        assertMatchesSource(
          "if a\n  b\nelsif c\n  d\nelse\n  e\nend\nputs x unless y\nz += 1 while z < 10\n",
          "case value\nwhen 1, 2\n  :low\nwhen 3 then :mid\nelse\n  :high\nend\n",
          "case config\nin {name: String => name}\n  name\nin [first, *]\n  first\nend\n",
          "begin\n  work\nrescue ArgumentError, TypeError => e\n  retry\nrescue => e\n  raise\nelse\n  ok\nensure\n  done\nend\n",
          "def safe\n  risky\nrescue StandardError\n  nil\nend\n",
          "for i in 1..3\n  puts i\nend\nuntil done\n  step\nend\n[1].each { |i| next }\n"
        );
    }

    @Test
    void definitions() {
        assertMatchesSource(
          "module Outer\n  class Inner < Base\n    class << self\n      def build = new\n    end\n\n    def self.create(*args)\n      new(*args)\n    end\n\n    attr_reader :name,\n                :age\n  end\nend\n",
          "BEGIN { setup }\nEND { teardown }\nalias new_name old_name\nundef foo, bar\n",
          "a, b = 1, 2\nc, (d, e) = [3, [4, 5]]\nf, g, = pair\nx = arr[1,]\n",
          deeplyNested(12)
        );
    }

    @Test
    void edgeCases() {
        assertMatchesSource(
          "",
          "\n\n",
          "x",
          "x;y;\n",
          "  \t\n# c\n\n",
          "puts 1\r\nputs 2\r\n",
          "p <<~A\n  body\nA"
        );
    }

    @Test
    void markerTextIsNotCounted() {
        Rb.CompilationUnit cu = parse("def a\n  b(1)\nend\n");
        Rb.CompilationUnit marked = (Rb.CompilationUnit) new RubyIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                return Markup.warn(SearchResult.found(method, "multi\nline"), new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(cu, 0);
        assertThat(marked.printAll()).isNotEqualTo(cu.printAll());
        assertThat(RubyLineCounter.count(marked)).isEqualTo(RubyLineCounter.count(cu)).isEqualTo(3);
    }

    private static void assertMatchesSource(String... fixtures) {
        assertParsedCountsMatchSource(RubyLineCounterTest::parseOptional, Rb.CompilationUnit.class,
          RubyLineCounter::count, 17, 15, fixtures);
    }

    private static String deeplyNested(int depth) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            source.append("  ".repeat(i)).append("if x").append(i).append(" # level ").append(i).append('\n');
        }
        source.append("  ".repeat(depth)).append("call(<<~EOS)\n").append("  ".repeat(depth)).append("  deep\n")
          .append("  ".repeat(depth)).append("EOS\n");
        for (int i = depth - 1; i >= 0; i--) {
            source.append("  ".repeat(i)).append("end\n");
        }
        return source.toString();
    }

    private static Optional<SourceFile> parseOptional(String source) {
        return RubyParser.builder().build().parse(source).findFirst();
    }

    private static Rb.CompilationUnit parse(String source) {
        return (Rb.CompilationUnit) parseOptional(source).orElseThrow();
    }
}
