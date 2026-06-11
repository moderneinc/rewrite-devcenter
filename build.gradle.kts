import io.moderne.devcenter.eol.build.GenerateEolFeedTask

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

    // End-of-life cards: package.json is parsed as a JSON LST, *.csproj as XML, pyproject.toml as
    // TOML, and Dockerfiles as a Docker LST (with a plain-text fallback). Versions are managed by
    // the rewrite-bom platform above so every rewrite module moves in lockstep.
    implementation("org.openrewrite:rewrite-json")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-toml")
    implementation("org.openrewrite:rewrite-docker")
    // Parses the bundled/overridable Moderne EOL feed (YAML canonical, JSON also accepted).
    // Version is managed by the rewrite-bom platform above so Jackson stays in lockstep with rewrite.
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

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

// The bundled EOL feed and sample data files are pure data and cannot carry a license header comment.
configure<nl.javadude.gradle.plugins.license.LicenseExtension> {
    exclude("**/eol/*.json")
    exclude("**/eol/*.yaml")
    exclude("**/feeds/**")
    exclude("**/samples/**")
}

// Refresh the bundled Moderne EOL feed at src/main/resources/eol/eol-feed.yaml from the live
// end-of-life service, and regenerate the per-ecosystem sample feeds under samples/feeds.
// Run: ./gradlew generateEolFeed
tasks.register<GenerateEolFeedTask>("generateEolFeed") {
    outputFile.set(layout.projectDirectory.file("src/main/resources/eol/eol-feed.yaml"))
    samplesDirectory.set(layout.projectDirectory.dir("samples/feeds"))
    // Runtime EOL is vendor-specific; the openjdk product follows this JDK vendor's schedule.
    // Override with -PeolJavaVendor=<endoflife.date slug> (e.g. oracle-jdk, amazon-corretto).
    javaVendorSlug.set(providers.gradleProperty("eolJavaVendor").orElse("eclipse-temurin"))
}

// generateEolFeed writes into src/main/resources; tasks that consume that directory must run after
// it when both are scheduled in one invocation (e.g. `generateSamples build`). Has no effect on a
// normal build, where generateEolFeed does not run and the checked-in feed is used as-is.
listOf("processResources", "sourcesJar", "licenseMain").forEach { name ->
    tasks.matching { it.name == name }.configureEach {
        mustRunAfter("generateEolFeed")
    }
}

// Generates the example data-table extracts by running each card against the test fixtures and
// writing the resulting EndOfLifeReport rows to samples/extracts. SampleExtractsTest is skipped
// during a normal `test` run and only writes files when `eol.generateSamples` is set, so a plain
// `build` never rewrites the checked-in samples.
val generateSampleExtracts by tasks.registering(Test::class) {
    group = "documentation"
    description = "Run the cards against fixtures and write sample data-table extracts to samples/extracts."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*SampleExtractsTest") }
    systemProperty("eol.generateSamples", "true")
    systemProperty("eol.samplesDir", layout.projectDirectory.dir("samples/extracts").asFile.absolutePath)
    outputs.upToDateWhen { false }
}

// Regenerate every sample artifact: the per-ecosystem feed snapshots (from the live service) and
// the example data-table extracts (by running each card against the test fixtures).
// Run: ./gradlew generateSamples
tasks.register("generateSamples") {
    group = "documentation"
    description = "Regenerate samples/ feed snapshots and data-table extracts from the live EOL service."
    dependsOn("generateEolFeed", generateSampleExtracts)
}
