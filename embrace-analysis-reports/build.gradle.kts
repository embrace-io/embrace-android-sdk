// embrace-analysis-reports: the startup analyses. Four of them read traces through the Perfetto client
// with the startup SQL that lives here (`analyze`, `variance`, `outlier-factors`, `matrix-report`);
// the rest are pure functions from datasets to report text (trend, hypothesis tests, factors,
// reproducibility, cross-device sections). Every report is reproduced line for line against a frozen
// golden, so the SQL and the text are both part of the contract.
plugins {
    id("embrace-analysis-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":embrace-analysis-common"))
    implementation(project(":embrace-analysis-stats"))
    implementation(project(":embrace-analysis-perfetto"))
    implementation(project(":embrace-analysis-records"))
    testImplementation(project(":embrace-analysis-test-fixtures"))
}

// Opt-in Layer-B gate: `./gradlew -PtraceParity=1 :embrace-analysis-reports:test` re-runs every startup
// query through the native trace_processor_shell on every captured fixture trace and compares the rows
// with the frozen output. Needs the traces on disk (they are not in the repo) and re-parses each one
// nine times, so it is off by default. Read through a provider so the configuration cache stays valid.
tasks.withType<Test>().configureEach {
    systemProperty("startup.traceParity", providers.gradleProperty("traceParity").orElse("").get())
}
