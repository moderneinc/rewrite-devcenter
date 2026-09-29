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
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.protobuf.ProtoIsoVisitor;
import org.openrewrite.protobuf.ProtoParser;
import org.openrewrite.protobuf.tree.Proto;

import static io.moderne.devcenter.internal.LineCountAssertions.assertMarkersNotCounted;
import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static org.openrewrite.Tree.randomId;

class ProtoLineCounterTest {

    @Test
    void protoMatchesSource() {
        assertParsedCountsMatchSource(s -> ProtoParser.builder().build().parse(s).findFirst(),
          Proto.Document.class, ProtoLineCounter::count, 1, 15,
          """
            syntax = "proto2";

            package example.v1;

            import "google/protobuf/timestamp.proto";
            import public "other.proto";

            option java_package = "com.example.v1";
            option java_multiple_files = true;

            /* A person,
               with a multi-line comment. */
            message Person {
              required string name = 1; // trailing
              optional int32 id = 2 [deprecated = true];
              repeated string emails = 3;
              map<string, int32> scores = 4;
              oneof contact {
                string phone = 5;
                string fax = 6;
              }
              message Address {
                optional string street = 1;
                message Geo {
                  optional double lat = 1;
                }
              }
              reserved 8, 9 to 11;
              reserved "old";
            }

            enum Status {
              STATUS_UNSPECIFIED = 0;
              STATUS_ACTIVE = 1 [(custom) = "x"];
            }

            service People {
              rpc Get (Person) returns (Person);
              rpc Watch (stream Person) returns (stream Person) {
                option deadline = 5;
              }
            }
            """,
          """
            message Legacy {
              optional int32 a = 1;
              extensions 100 to 199;
              repeated group Result = 2 {
                required string url = 3;
              }
            }

            extend Legacy {
              optional string note = 100;
              // the parser drops a comment at the very end of a file
            }""",
          "syntax = \"proto2\";"
        );
    }

    @Test
    void markersAreNotCounted() {
        Proto.Document document = (Proto.Document) ProtoParser.builder().build()
          .parse("syntax = \"proto2\";\nmessage A {\n  optional string a = 1;\n}\n").findFirst().orElseThrow();
        assertMarkersNotCounted(document, SearchResult.found(document, "multi\nline"));
        assertMarkersNotCounted(document, new ProtoIsoVisitor<Integer>() {
            @Override
            public Proto.Field visitField(Proto.Field field, Integer p) {
                return Markup.warn(field, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(document, 0));
        assertMarkersNotCounted(document, new ProtoIsoVisitor<Integer>() {
            @Override
            public Proto.Message visitMessage(Proto.Message message, Integer p) {
                return message.withMarkers(message.getMarkers().add(new MultilineMarker(randomId())));
            }
        }.visitNonNull(document, 0));
    }
}
