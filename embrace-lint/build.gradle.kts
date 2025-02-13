plugins {
    id("java-library")
    id("kotlin")
    id("com.android.lint")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    androidModule.set(false)
    jvmTarget.set(JavaVersion.VERSION_11)
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
