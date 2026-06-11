plugins {
    `java`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    // The importer emits the bundled feed and sample feeds as YAML.
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
}
