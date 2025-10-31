plugins {
    id("embrace-jvm-conventions")
    id("java-library")
    id("com.android.lint")
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
