// embrace-analysis-test-fixtures: the frozen goldens every embrace-analysis module is tested against,
// and the helpers that read them. Following the repo's `embrace-test-common` / `embrace-test-fakes`
// convention, this is a plain module whose MAIN sources are test support, so each module's tests
// depend on it with `testImplementation` and see one `fixtures/` tree on their classpath.
//
// The fixtures are shared rather than partitioned because nearly every set feeds several modules: the
// statistics goldens are read by the stats, reports and records tests; the trace goldens by the
// perfetto, reports, records and local-refs tests. One copy, one loader, no drift between them.
//
// The fixture data itself is the plain `fixtures/` directory beside this file, deliberately NOT under
// `src/main/resources`: a consuming module would receive resources as a JAR, and the goldens have to be
// real files. The `embrace-analysis-conventions` plugin hands every test task its path.
//
// JUnit is `api` because the golden comparison helpers are themselves assertions.
plugins {
    id("embrace-analysis-conventions")
}

dependencies {
    api(project(":embrace-analysis-common"))
    api(libs.junit)
}
