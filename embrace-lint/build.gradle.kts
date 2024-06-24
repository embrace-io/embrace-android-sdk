plugins {
    id("java-library")
    id("kotlin")
    id("com.android.lint")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
