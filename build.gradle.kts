plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

// Set as appropriate for your organization
group = "org.openrewrite.recipe"
description = "Rewrite DevCenter integration."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    // The bom version can also be set to a specific version
    // https://github.com/openrewrite/rewrite-recipe-bom/releases
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies")
    implementation("org.openrewrite.recipe:rewrite-java-security")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-gradle")
    runtimeOnly("org.openrewrite:rewrite-java-17")

    implementation("org.slf4j:slf4j-api:1.7.+")

    testImplementation("org.openrewrite:rewrite-test")
    testRuntimeOnly("junit:junit:4.+")
}
