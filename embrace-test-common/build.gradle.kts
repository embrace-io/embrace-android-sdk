plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    androidLibrary.set(false)
}
