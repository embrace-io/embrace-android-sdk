// Shared by every embrace-analysis module: the repo's JVM convention (detekt with the repo config, the
// stdlib pin, JUnit 4, JVM 11, warnings-as-errors) plus where the frozen fixtures are.
//
// The fixtures are plain files under embrace-analysis-test-fixtures/fixtures, not packaged resources: a
// module receives another module's resources as a JAR, and the goldens have to be real files (the trace
// goldens are an archive that is unpacked, the stores are opened by path). So every test task is told the
// directory, and `io.embrace.analysis.fixtures.Fixtures` reads it from this property.
plugins {
    id("embrace-jvm-conventions")
}

tasks.withType<Test>().configureEach {
    systemProperty("analysis.fixturesDir", rootDir.resolve("embrace-analysis-test-fixtures/fixtures").absolutePath)
}
