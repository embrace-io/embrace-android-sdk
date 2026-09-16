// embrace-analysis-perfetto: a client for Perfetto's trace_processor_shell. Resolving a pinned native
// prebuilt (sha256-verified, cached per machine), cold `-q` queries, a warm `server unix` session with
// `query --remote` and automatic cold fallback, CSV row parsing, and the trace-health verdict (loss
// counters, canary presence, class-load burst).
//
// The engine client knows nothing about what is being measured: the startup SQL lives with the reports
// that run it (`embrace-analysis-reports`), and the health check takes its canary slice as a parameter.
plugins {
    id("embrace-analysis-conventions")
}

dependencies {
    // `api`: the client hands back `Processes.Output` and the warm session takes `Duration`s from common's
    // helpers, so a caller of `queryRaw(...).stdout` needs common on its own classpath.
    api(project(":embrace-analysis-common"))
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
