// embrace-analysis-local-refs: sweep a source tree for references to one machine or one author - home
// paths, addresses, device serials, private artifact links, run-specific citations - so that what
// ships describes the work rather than the machine it ran on.
//
// The checker is generic: it takes the roots to walk and knows no repository layout, so it depends on
// nothing but the standard library. This repository's layout is one constant, supplied by the CLI.
plugins {
    id("embrace-analysis-conventions")
}

dependencies {
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
