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

import io.moderne.devcenter.internal.LineCountAssertions.MultilineMarker;
import org.junit.jupiter.api.Test;
import org.openrewrite.hcl.HclIsoVisitor;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;

import static io.moderne.devcenter.internal.LineCountAssertions.assertMarkersNotCounted;
import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.openrewrite.Tree.randomId;

class HclLineCounterTest {

    @Test
    void hclMatchesSource() {
        assertParsedCountsMatchSource(s -> HclParser.builder().build().parse(s).findFirst(),
          Hcl.ConfigFile.class, HclLineCounter::count, 1, 15,
          """
            # Terraform
            terraform {
              required_version = ">= 1.3"
              required_providers {
                aws = {
                  source  = "hashicorp/aws",
                  version = "~> 5.0"
                }
              }
            }

            /* a multi-line
               comment */
            resource "aws_instance" "web" {
              ami           = var.ami // trailing
              instance_type = var.large ? "m5.large" : "t3.micro"
              count         = length(var.names)
              tags = merge(
                local.tags,
                {
                  Name = "web-${count.index}"
                },
              )
            }
            """,
          """
            locals {
              ids      = aws_instance.web[*].id
              names    = aws_instance.web.*.tags.Name
              first    = aws_instance.web[0].id
              legacy   = aws_instance.web.0.id
              upper    = [for s in var.list : upper(s) if s != ""]
              by_name  = { for k, v in var.map : v.name => k... }
              negative = !var.enabled && -var.offset < 0
              tuple    = [
                1,
                2,
              ]
            }
            """,
          """
            variable "policy" {
              default = <<EOF
            {
              "Version": "${var.version}"
            }
            EOF
            }

            output "script" {
              value = <<-EOT
                echo hello
                echo ${var.name}
                EOT
            }
            """,
          """
            a {
              b {
                c {
                  d {
                    e = 1
                  }
                }
              }
            }
            # ends in a comment""",
          "x = 1"
        );
    }

    @Test
    void markersAreNotCounted() {
        Hcl.ConfigFile file = (Hcl.ConfigFile) HclParser.builder().build()
          .parse("resource \"a\" \"b\" {\n  x = \"one\"\n}\n").findFirst().orElseThrow();
        assertMarkersNotCounted(file, SearchResult.found(file, "multi\nline"));
        assertMarkersNotCounted(file, new HclIsoVisitor<Integer>() {
            @Override
            public Hcl.Literal visitLiteral(Hcl.Literal literal, Integer p) {
                return Markup.warn(literal, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(file, 0));
        assertMarkersNotCounted(file, new HclIsoVisitor<Integer>() {
            @Override
            public Hcl.Attribute visitAttribute(Hcl.Attribute attribute, Integer p) {
                return attribute.withMarkers(attribute.getMarkers().add(new MultilineMarker(randomId())));
            }
        }.visitNonNull(file, 0));
    }
}
