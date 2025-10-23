plugins {
    id("java-library")
    id("kotlin")
    id("com.android.lint")
    id("io.embrace.internal.build-logic")
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
