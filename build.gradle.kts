plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "io.moderne.recipe"
description = "Rewrite DevCenter integration."

recipeDependencies {
    testParserClasspath("junit:junit:4.13.2")
    testParserClasspath("org.junit.jupiter:junit-jupiter-api:6.0.2")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.opennlp" && requested.name == "opennlp-tools") {
            useVersion("2.5.9")
            because("CVE-2026-42027, CVE-2026-40682, CVE-2026-42440")
        }
    }
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))

    // Only needed for result parsing (package io.moderne.devcenter.result),
    // not for defining and running DevCenter recipes.
    compileOnly("io.moderne:moderne-organizations-format:latest.release")
    implementation("com.univocity:univocity-parsers:latest.release")

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-java-security:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-python")
    implementation("org.openrewrite:rewrite-javascript")
    implementation("org.openrewrite:rewrite-csharp")
    implementation("org.openrewrite:rewrite-go")
    runtimeOnly("org.openrewrite:rewrite-java-17")

    // Provides the Prethink recipes referenced by code-quality-devcenter.yml
    // (e.g. io.moderne.prethink.quality.FindClassMetrics).
    implementation("io.moderne.recipe:rewrite-prethink:${rewriteVersion}")

    implementation("org.slf4j:slf4j-api:1.7.+")

    // Line counting compiles against these; the first four reach the runtime classpath through rewrite-prethink
    // and rewrite-java-security, and the Moderne CLI bundles HCL and protobuf
    compileOnly("org.openrewrite:rewrite-docker")
    compileOnly("org.openrewrite:rewrite-properties")
    compileOnly("org.openrewrite:rewrite-ruby")
    compileOnly("org.openrewrite:rewrite-scala")
    compileOnly("org.openrewrite:rewrite-hcl")
    compileOnly("org.openrewrite:rewrite-protobuf")
    // The Scala markers are case classes; pinned to the version rewrite-scala's compiler uses
    compileOnly("org.scala-lang:scala-library:3.9.0")

    testImplementation("io.moderne:moderne-organizations-format:latest.release")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-docker")
    testImplementation("org.openrewrite:rewrite-properties")
    testImplementation("org.openrewrite:rewrite-ruby")
    testImplementation("org.openrewrite:rewrite-scala")
    testImplementation("org.openrewrite:rewrite-hcl")
    testImplementation("org.openrewrite:rewrite-protobuf")
    testImplementation(gradleApi())
    testImplementation("org.openrewrite.gradle.tooling:model")
    testImplementation("de.siegmar:fastcsv:3.+")
    testImplementation("io.github.classgraph:classgraph:latest.release")
    testRuntimeOnly("junit:junit:4.+")
}

tasks.withType<Test> {
    maxHeapSize = "6g"
}
