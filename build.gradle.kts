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

val rewriteVersion = "8.73.0-SNAPSHOT"
dependencies {
    // Only needed for result parsing (package io.moderne.devcenter.result),
    // not for defining and running DevCenter recipes.
    compileOnly("io.moderne:moderne-organizations-format:latest.release")
    implementation("com.univocity:univocity-parsers:latest.release")

    implementation("org.openrewrite:rewrite-java:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:1.50.0")
    implementation("org.openrewrite.recipe:rewrite-java-security:3.26.1")
    implementation("org.openrewrite:rewrite-maven:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-gradle:${rewriteVersion}")
    runtimeOnly("org.openrewrite:rewrite-java-17:${rewriteVersion}")

    implementation("org.slf4j:slf4j-api:1.7.+")

    testImplementation("io.moderne:moderne-organizations-format:latest.release")
    testImplementation("org.openrewrite:rewrite-test:${rewriteVersion}")
    testImplementation("org.openrewrite:rewrite-java-21:${rewriteVersion}")
    testImplementation(gradleApi())
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")
    testRuntimeOnly("junit:junit:4.+")
}
//
//tasks.withType<Test> {
//    val heapDumpDir = layout.buildDirectory.dir("heap-dumps").get().asFile
//    doFirst { heapDumpDir.mkdirs() }
//    jvmArgs(
//        "-XX:+HeapDumpOnOutOfMemoryError",
//        "-XX:HeapDumpPath=${heapDumpDir.absolutePath}"
//    )
//}

nexusPublishing {
    repositories.getByName("sonatype") {
        nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
        snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
    }
}
