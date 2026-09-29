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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.scala.ScalaParser;
import org.openrewrite.scala.tree.S;

import java.util.Optional;

import static io.moderne.devcenter.internal.LineCountAssertions.assertCountMatchesSource;
import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.assertj.core.api.Assertions.assertThat;

class ScalaLineCounterTest {

    static final String[] FIXTURES = {
      """
        package com.example

        import scala.collection.mutable
        import scala.util.{Failure, Success, Try => Attempt}
        import java.io._

        /** A doc comment
          * spanning lines.
          */
        case class Person(name: String, age: Int = 0) extends Serializable with Product

        object Person {
          def apply(name: String): Person = new Person(name)
        }

        trait Greeter[A <: AnyRef] {
          def greet(a: A): String
        }
        """,
      """
        object Strings {
          val plain = "one"
          val multi = \"""first
            |second
            |third\""".stripMargin
          val name = "world"
          val interpolated = s"hello $name and ${name.length + 1}"
          val formatted = f"${3.14159}%.2f"
          val raw = raw"a\\nb"
          val multiInterpolated = s\"""line one ${
            name
          }
            line two\"""
        }
        """,
      """
        object Matching {
          def describe(x: Any): String = x match {
            case 0 => "zero"
            case i: Int if i > 0 =>
              "positive"
            case s: String => s
            case (a, b) => s"$a $b"
            case _ =>
              "other"
          }

          def pf: PartialFunction[Int, Int] = {
            case 1 => 2
            case n => n * 2
          }
        }
        """,
      """
        object Loops {
          def evens(xs: List[Int]): List[Int] =
            for {
              x <- xs
              if x % 2 == 0
              y = x * 2
            } yield y

          def pairs = for (a <- 1 to 3; b <- 1 to 3) yield (a, b)

          def run(): Unit = {
            var i = 0
            while (i < 10) {
              i += 1
            }
            try {
              risky()
            } catch {
              case e: IllegalStateException => println(e)
              case _: Throwable =>
            } finally {
              println("done")
            }
          }

          def risky(): Unit = throw new IllegalStateException("boom")
        }
        """,
      """
        enum Color:
          case Red, Green, Blue

        trait Shape:
          def area: Double

        class Circle(radius: Double) extends Shape:
          def area: Double =
            val r = radius
            math.Pi * r * r

        def main(args: Array[String]): Unit =
          if args.isEmpty then
            println("none")
          else
            println(args.mkString(","))
          for arg <- args do
            println(arg)
        """,
      """
        object Nested {
          def deep(n: Int): Int = {
            if (n > 0) {
              List(1, 2, 3).map { x =>
                Option(x).fold(0) { y =>
                  Seq(y).foldLeft(0) { (acc, z) =>
                    acc + (z match {
                      case 1 => {
                        val q = (((z + 1) * 2) - 3)
                        q
                      }
                      case _ => 0
                    })
                  }
                }
              }.sum
            } else 0
          }

          def curried(a: Int)(b: Int)(implicit c: Int): Int = a + b + c

          val f: (Int, Int) => Int = (a, b) => a + b
          val g = List(1, 2, 3).map(_ * 2)
        }
        """,
      """
        object Givens {
          extension (s: String)
            def shout: String = s.toUpperCase

          given intOrdering: Ordering[Int] = Ordering.Int

          type Pair[A] = (A, A)

          def sum[T: Numeric](xs: T*): T = xs.sum
        }
        """,
      """
        object Trailing {
          val xs = List(
            1,
            2,
          )
          def f(
            a: Int,
            b: Int,
          ): Int = a + b
        }
        // a comment at the end
        """,
      """
        /* only a block
           comment */
        object A
        /* trailing
           block */""",
      "object A { val x = 1 }",
      "class B",
    };

    @Test
    void scalaMatchesSource() {
        assertThat(assertParsedCountsMatchSource(ScalaLineCounterTest::parse, S.CompilationUnit.class,
          ScalaLineCounter::count, 7, 15, FIXTURES)).isGreaterThan(FIXTURES.length * 10);
    }

    @Test
    void markerTextIsNotCounted() {
        S.CompilationUnit cu = (S.CompilationUnit) parse(FIXTURES[2]).orElseThrow();
        long unmarked = ScalaLineCounter.count(cu);
        S.CompilationUnit marked = (S.CompilationUnit) new JavaIsoVisitor<Integer>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, Integer p) {
                return SearchResult.found(literal, "multi\nline");
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                return Markup.warn(identifier, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(cu, 0);
        assertThat(marked.printAll()).isNotEqualTo(cu.printAll());
        assertThat(ScalaLineCounter.count(marked)).isEqualTo(unmarked);
        assertCountMatchesSource(marked, ScalaLineCounter::count);
    }

    static Optional<SourceFile> parse(String source) {
        return ScalaParser.builder().build().parse(source).findFirst();
    }
}
