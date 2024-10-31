plugins {
    id("com.android.library") apply false
    id("kotlin-android") apply false
}

android {
    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xexplicit-api=strict")
        }
    }
}
