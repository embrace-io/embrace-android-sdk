import io.embrace.gradle.configmodel.ConfigTarget
import io.embrace.gradle.configmodel.generateConfigSources

plugins {
    id("embrace-prod-jvm-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(libs.mockwebserver)
}

generateConfigSources(ConfigTarget.SDK)
