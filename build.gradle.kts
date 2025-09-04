plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "io.moderne.recipe"
description = "Rewrite DevCenter integration."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("io.moderne.recipe:moderne-recipe-bom:latest.release"))

    // Only needed for result parsing (package io.moderne.devcenter.result),
    // not for defining and running DevCenter recipes.
    compileOnly("io.moderne:moderne-organizations-format:latest.release")
    implementation("com.univocity:univocity-parsers:latest.release")

    implementation("org.openrewrite:rewrite-java:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-java-security:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-maven:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-gradle:${rewriteVersion}")
    runtimeOnly("org.openrewrite:rewrite-java-17:${rewriteVersion}")

    implementation("org.slf4j:slf4j-api:1.7.+")

    testImplementation("io.moderne:moderne-organizations-format:latest.release")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-java-21:${rewriteVersion}")
    testImplementation(gradleApi())
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")
    testRuntimeOnly("junit:junit:4.+")
}

nexusPublishing {
    repositories.getByName("sonatype") {
        nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
        snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
    }
}
