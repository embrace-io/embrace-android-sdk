plugins {
    id("com.android.library")
    id("kotlin-android")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
}

android {
    namespace = "io.embrace.android.embracesdk.test.common"
}
