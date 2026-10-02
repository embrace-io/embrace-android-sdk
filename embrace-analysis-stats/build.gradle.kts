// embrace-analysis-stats: cluster-aware inference for benchmark comparisons - the two-stage cluster
// bootstrap, the cluster permutation test, Cliff's delta, TOST equivalence, Benjamini-Hochberg, Type-7
// quantiles, power sizing - and the bit-exact CPython random generator they draw from.
//
// It depends on nothing but the standard library on purpose: nothing here knows about Android,
// Perfetto, or SDK init. Any benchmark whose samples come in clusters (passes, sessions, devices) can
// use it as it stands. The tests are the frozen statistics goldens, which come from the fixtures module.
plugins {
    id("embrace-analysis-conventions")
}

dependencies {
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
