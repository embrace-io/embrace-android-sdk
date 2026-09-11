// embrace-analysis-maxims: the bench toolchain's beliefs about SDK init, as code. Each maxim is a
// statement with a mechanical check; scoring a campaign yields a verdict per maxim, the verdicts
// accumulate in a ledger per cell, and MAXIMS.md is rendered from the ledger so the standing of every
// belief is generated rather than asserted.
//
// It sits above the reports because its checks are expressed in their dataset types, and the
// dependency runs one way only: the reports never know the maxims exist.
plugins {
    id("embrace-analysis-conventions")
}

dependencies {
    implementation(project(":embrace-analysis-common"))
    implementation(project(":embrace-analysis-stats"))
    implementation(project(":embrace-analysis-records"))
    implementation(project(":embrace-analysis-reports"))
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
