plugins {
    // To create a shadow/fat jar that bundles non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
    // Code formatting — palantir-java-format, enforced via spotlessCheck in `check`
    id("com.diffplug.spotless") version "7.0.2"
}

qupathExtension {
    name = "qupath-extension-guided-tutorials"
    group = "io.github.qupath"
    version = "0.1.0"
    description = "Step-by-step guided walkthroughs of QuPath's own tutorials, inside QuPath"
    automaticModule = "io.github.qupath.extension.tutorials"
}

dependencies {
    // Pure Java/JavaFX: no AI, no network client, no new runtime dependencies.
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // The qupath-conventions/javafx plugin only wires JavaFX onto the main compile classpath.
    // FieldFinderTest builds real controls, so pin the same version for tests.
    testImplementation("org.openjfx:javafx-base:25.0.2")
    testImplementation("org.openjfx:javafx-graphics:25.0.2")
    testImplementation("org.openjfx:javafx-controls:25.0.2")
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

spotless {
    isEnforceCheck = true
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat("2.93.0")
        removeUnusedImports()
    }
}
