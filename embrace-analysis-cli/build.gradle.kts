// embrace-analysis-cli: the one executable. A Clikt dispatcher with one file per subcommand under
// cli/, each a thin adapter from flags to a call into the library module that does the work. Run it
// through the checked-in wrapper `tools/startup`, which builds this module incrementally and then
// execs the installDist launcher - NOT through `./gradlew run`, which swallows stdin, turns exit codes
// into exceptions, breaks terminal detection, and would wrap a Gradle daemon around the tool's own
// `gradlew -p examples/ExampleApp` children for the length of a campaign.
//
// Dependencies are `implementation`, not `api`: nothing depends on the CLI, and listing every library
// module here is the honest statement of what the executable assembles. The launcher keeps the name
// `startup-tools` because that is the program name every skill and every `--help` text refers to.
//
// The repo's JVM convention is applied deliberately: it brings detekt with the repo config, the
// stdlib pin, JUnit 4, and warnings-as-errors, and pins the JVM target to 11 like every other JVM
// module here.
plugins {
    id("embrace-analysis-conventions")
    application
}

dependencies {
    implementation(project(":embrace-analysis-common"))
    implementation(project(":embrace-analysis-stats"))
    implementation(project(":embrace-analysis-perfetto"))
    implementation(project(":embrace-analysis-device"))
    implementation(project(":embrace-analysis-local-refs"))
    implementation(project(":embrace-analysis-records"))
    implementation(project(":embrace-analysis-reports"))
    implementation(project(":embrace-analysis-maxims"))
    implementation(project(":embrace-analysis-campaign"))
    implementation(libs.clikt)
    testImplementation(project(":embrace-analysis-test-fixtures"))
}

application {
    applicationName = "startup-tools"
    mainClass.set("io.embrace.analysis.MainKt")
}

// `./gradlew :embrace-analysis-cli:analysisCheck` - every embrace-analysis module's tests and detekt in
// one invocation, so "is the toolchain green" stays a single command after the split. Each module's own
// `:embrace-analysis-<name>:test` remains the fast path while working inside one of them.
tasks.register("analysisCheck") {
    group = "verification"
    description = "Runs test and detekt for every embrace-analysis module."
    dependsOn(
        rootProject.subprojects
            .filter { it.name.startsWith("embrace-analysis-") }
            .flatMap { listOf(":${it.name}:test", ":${it.name}:detekt") },
    )
}
