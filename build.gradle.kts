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
    runtimeOnly("org.openrewrite:rewrite-java-17")

    // Provides the Prethink recipes referenced by code-quality-devcenter.yml
    // (e.g. io.moderne.prethink.quality.FindClassMetrics).
    implementation("io.moderne.recipe:rewrite-prethink:${rewriteVersion}")

    implementation("org.slf4j:slf4j-api:1.7.+")

    testImplementation("io.moderne:moderne-organizations-format:latest.release")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation(gradleApi())
    testImplementation("org.openrewrite.gradle.tooling:model")
    testImplementation("de.siegmar:fastcsv:3.+")
    testRuntimeOnly("junit:junit:4.+")
}

tasks.withType<Test> {
    maxHeapSize = "6g"
}

nexusPublishing {
    repositories.getByName("sonatype") {
        nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
        snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
    }
}
