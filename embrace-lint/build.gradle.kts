plugins {
    id("java-library")
    kotlin("jvm")
    id("com.android.lint")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    androidLibrary.set(false)
    jvmTarget.set(JavaVersion.VERSION_11)
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
