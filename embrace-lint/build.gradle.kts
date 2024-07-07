plugins {
    id("java-library")
    id("kotlin")
    id("com.android.lint")
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
