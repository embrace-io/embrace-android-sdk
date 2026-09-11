// embrace-analysis-common: what every other embrace-analysis module stands on and nothing that knows
// what is being measured. Running a child process safely (both streams to files, waitFor before any
// read, a real timeout), zip round-trips, CSV, Python-compatible number formatting and JSON rendering,
// and repo-root discovery. No Android, no Perfetto, no statistics.
//
// kotlinx-serialization-json is `api` because PyJson and StartupJson hand out JsonObject in their own
// signatures; a dependant that calls them needs the type. No @Serializable lives here, so the compiler
// plugin is not applied.
plugins {
    id("embrace-analysis-conventions")
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
