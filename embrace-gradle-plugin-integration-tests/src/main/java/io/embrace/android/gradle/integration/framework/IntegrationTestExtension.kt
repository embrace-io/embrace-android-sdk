package io.embrace.android.gradle.integration.framework

import com.android.build.api.dsl.ApplicationExtension
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTask
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * An extension that can be used to configure the embrace gradle plugin in the integration tests.
 * This effectively just provides default values & logic that makes it much easier to define
 * fixtures.
 */
@Suppress("UnstableApiUsage")
abstract class IntegrationTestExtension(objectFactory: ObjectFactory) {

    val variantData: Property<AndroidCompactedVariantData> =
        objectFactory.property(AndroidCompactedVariantData::class.java)

    val buildId: Property<String> =
        objectFactory.property(String::class.java).convention(IntegrationTestDefaults.BUILD_ID)

    val appId: Property<String> =
        objectFactory.property(String::class.java).convention(IntegrationTestDefaults.APP_ID)

    val apiToken: Property<String> =
        objectFactory.property(String::class.java).convention(IntegrationTestDefaults.API_TOKEN)

    fun configureEmbraceTask(task: EmbraceTask) {
        task.variantData.set(variantData)
    }

    fun configureGradleUploadTask(
        project: Project,
        task: EmbraceUploadTask,
        endpoint: EmbraceEndpoint = EmbraceEndpoint.PROGUARD,
        filename: String? = null
    ) {
        configureEmbraceTask(task)
        task.requestParams.set(
            RequestParams(
                appId = appId.get(),
                apiToken = apiToken.get(),
                buildId = buildId.orNull,
                endpoint = endpoint,
                fileName = filename,
                baseUrl = checkNotNull(project.findProperty("embrace.baseUrl")?.toString())
            )
        )
    }

    fun configureAndroidProject(project: Project) = with(project) {
        repositories.apply {
            google()
            mavenCentral()
        }

        // disable dependency injection as SNAPSHOT versions of SDK don't necessarily exist
        // whenever unit tests are run
        val embrace = checkNotNull(project.extensions.findByType(SwazzlerExtension::class.java))
        embrace.disableDependencyInjection.set(true)

        val android = checkNotNull(project.extensions.findByType(ApplicationExtension::class.java))

        android.apply {
            namespace = "com.example"
            compileSdk = 34

            defaultConfig {
                applicationId = "com.example.app"
                minSdk = 24
                versionCode = 1
                versionName = "1.0"
            }
            buildTypes {
                release {
                    isMinifyEnabled = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android.txt"),
                        "proguard-rules.pro"
                    )
                }
            }
        }
    }

    fun configure3rdPartyLibrary(project: Project) = with(project) {
        dependencies.add("implementation", project(":customLibrary"))
    }
}
