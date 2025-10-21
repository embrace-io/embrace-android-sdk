plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

dependencies {
    api(project(":embrace-android-instrumentation-schema"))
    api(project(":embrace-android-infra"))
    api(project(":embrace-android-config"))

    // TODO: alter ConfigBehavior to avoid generic params, that will avoid this dependency.
    api(project(":embrace-android-payload"))
}
