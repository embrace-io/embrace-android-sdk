// embrace-analysis-records: the durable record of what has been measured. The schemas of the
// longitudinal store, the reference set, the corpus legs and the section medians; ingesting a run into
// the store with every admissibility guard; submitting a record to the shared corpus, redacted; the
// reference set's drift check; and the artifact manifest that guards the living documents.
//
// This is the first module that knows what is being measured. It needs the statistics (for the
// derived aggregates), the Perfetto client (ingest measures a trace's window and health) and the device
// module (a record carries the device's profile). All `api`: a record's fields ARE those types.
plugins {
    id("embrace-analysis-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // `api` where a record's own fields or a result's own fields are these modules' types: a consumer that
    // reads a StoreRecord's device profile or a measurement's health verdict needs them on its classpath.
    api(project(":embrace-analysis-common"))
    api(project(":embrace-analysis-perfetto"))
    api(project(":embrace-analysis-device"))
    implementation(project(":embrace-analysis-stats"))
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
