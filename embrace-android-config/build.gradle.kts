import io.embrace.config.codegen.GenerateConfigTask

plugins {
    id("embrace-prod-jvm-conventions")
}

val generateConfig = tasks.register<GenerateConfigTask>("generateConfig") {
    yamlFile.set(layout.projectDirectory.file("src/main/config.yaml"))
    packageName.set("io.embrace.android.embracesdk.internal.config.resolved")
    sliceDir.set(layout.buildDirectory.dir("generated/config/main"))
}

kotlin.sourceSets.main { kotlin.srcDir(generateConfig.flatMap { it.sliceDir }) }

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(libs.mockwebserver)
}
