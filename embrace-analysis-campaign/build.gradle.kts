// embrace-analysis-campaign: driving a benchmark on real devices without wrecking the run. A fleet
// campaign of N passes with the silicon cool gate and per-launch cohort verification; a matrix cell
// with every invariant machine-checked first (temperature, host quiet, device quiet, instrument
// present); the per-version compatibility patches with a journal that survives a dead process;
// borrowed state that is given back even on SIGTERM; and the dex-level A/B arm pre-flight.
//
// It needs the device module (adb), the Perfetto client (the post-run instrument check) and the
// records (it writes a run's provenance in the shape ingest reads). It never depends on the reports or
// the maxims: a campaign produces data, it does not interpret it.
plugins {
    id("embrace-analysis-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":embrace-analysis-common"))
    implementation(project(":embrace-analysis-device"))
    implementation(project(":embrace-analysis-perfetto"))
    implementation(project(":embrace-analysis-records"))
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
