package io.embrace.android.gradle.plugin.ndk

import io.embrace.android.gradle.plugin.EXTENSION_NAME
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.extension.EXTENSION_EMBRACE_INTERNAL
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTask
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdkUploadTaskRegistrationTest {

    private val baseUrl = "https://example.com/api"

    companion object {
        val unitySymbolsDir = mockk<UnitySymbolsDir>()

        @AfterClass
        @JvmStatic
        fun tearDown() {
            unmockkAll()
        }
    }

    private fun createExtension(project: Project, ndkEnabled: Boolean, projectType: ProjectType): EmbraceExtensionInternal {
        val extension = project.extensions.create(
            EXTENSION_EMBRACE_INTERNAL,
            EmbraceExtensionInternal::class.java,
            project.objects
        )

        project.extensions.create(
            EXTENSION_NAME,
            SwazzlerExtension::class.java,
            project.objects
        )

        extension.variants.create("variantName").also { newVariant ->
            newVariant.config.set(
                VariantConfig(
                    embraceConfig = EmbraceVariantConfig(
                        appId = "appId",
                        apiToken = "apiToken",
                        ndkEnabled = ndkEnabled,
                        sdkConfig = null,
                        unityConfig = null
                    )
                )
            )
            newVariant.projectType.set(projectType)
            newVariant.unitySymbolsDir.set(unitySymbolsDir)
        }

        return extension
    }

    private fun registerTestTask(project: Project, taskName: String): TaskProvider<DefaultTask> =
        project.tasks.register(
            taskName,
            DefaultTask::class.java
        )

    @Test
    fun `test configure ndkUploadTask with ndk disabled`() {
        val taskName = NdkUploadTask.NAME
        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }
        val project = ProjectBuilder.builder().build()

        val extension = createExtension(project, false, ProjectType.UNITY)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )
        assertFalse(
            project.isTaskRegistered(
                taskName,
                variant.name
            )
        )
        registration.register(params)

        val ndkUploadTask = project.tasks.findByName("$taskName${variant.name.capitalizedString()}") as? NdkUploadTask
        assertNull(ndkUploadTask)
    }

    @Test
    fun `test configure ndkUploadTask for native project type`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()
        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }

        val extension = createExtension(project, true, ProjectType.NATIVE)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )
        assertFalse(
            project.isTaskRegistered(
                taskName,
                variant.name
            )
        )
        registration.register(params)

        assertTrue(
            project.isTaskRegistered(
                NdkUploadTask.NAME,
                variant.name
            )
        )
    }

    @Test
    fun `test configure ndkUploadTask for unity 2018-2019 project type`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()
        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }
        val mergeJniLibFoldersTaskName =
            "merge${variant.name.capitalizedString()}JniLibFolders"

        registerTestTask(project, mergeJniLibFoldersTaskName)

        val transformNativeLibsTaskName =
            "transformNativeLibsWithMergeJniLibsFor${variant.name.capitalizedString()}"

        val extension = createExtension(project, true, ProjectType.UNITY)

        registerTestTask(project, transformNativeLibsTaskName)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )
        assertFalse(
            project.isTaskRegistered(
                taskName,
                variant.name
            )
        )
        registration.register(params)

        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${variant.name.capitalizedString()}") as NdkUploadTask

        assertTrue(
            project.isTaskRegistered(
                NdkUploadTask.NAME,
                variant.name
            )
        )
        assertTrue(
            ndkUploadTask.mustRunAfter.getDependencies(ndkUploadTask).toString()
                .contains(mergeJniLibFoldersTaskName)
        )
    }

    @Test
    fun `test configure ndkUploadTask for unity 2020 project type`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()

        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }
        val mergeJniLibFoldersTaskName =
            "merge${variant.name.capitalizedString()}JniLibFolders"

        registerTestTask(project, mergeJniLibFoldersTaskName)

        val mergeNativeLibs =
            "merge${variant.name.capitalizedString()}NativeLibs"

        registerTestTask(project, mergeNativeLibs)
        val extension = createExtension(project, true, ProjectType.UNITY)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )

        assertFalse(
            project.isTaskRegistered(
                taskName,
                variant.name
            )
        )
        registration.register(params)

        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${variant.name.capitalizedString()}") as NdkUploadTask

        assertTrue(
            project.isTaskRegistered(
                NdkUploadTask.NAME,
                variant.name
            )
        )
        assertTrue(
            ndkUploadTask.mustRunAfter.getDependencies(ndkUploadTask).toString()
                .contains(mergeNativeLibs)
        )
    }

    @Test
    fun `test execute throws the exception`() {
        val project = ProjectBuilder.builder().build()
        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }
        val extension = createExtension(project, true, ProjectType.NATIVE)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )
        try {
            registration.register(params)
        } catch (e: Exception) {
            assertTrue(e is NullPointerException)
        }
    }

    @Test
    fun `verify Ndk upload task configuration once is registered`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()
        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }

        val extension = createExtension(project, true, ProjectType.NATIVE)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )
        registration.register(params)
        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${variant.name.capitalizedString()}") as NdkUploadTask

        assertEquals(
            ndkUploadTask.requestParams.get(),
            RequestParams(
                appId = "appId",
                apiToken = "apiToken",
                endpoint = EmbraceEndpoint.NDK,
                baseUrl,
            )
        )
        assertEquals(ndkUploadTask.unitySymbolsDir.orNull, null)
    }

    @Test
    fun `verify Ndk upload task configuration once is registered for unity`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()
        val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { name } returns "variantName"
        }

        val extension = createExtension(project, true, ProjectType.UNITY)

        val registration = NdkUploadTaskRegistration(mockk(relaxed = true))
        val params = RegistrationParams(
            project,
            mockk(relaxed = true),
            variant,
            mockk(relaxed = true),
            extension,
            baseUrl,
        )
        registration.register(params)
        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${variant.name.capitalizedString()}") as NdkUploadTask

        assertEquals(
            ndkUploadTask.requestParams.get(),
            RequestParams(
                appId = "appId",
                apiToken = "apiToken",
                endpoint = EmbraceEndpoint.NDK,
                baseUrl,
            )
        )
        assertEquals(ndkUploadTask.unitySymbolsDir.orNull, unitySymbolsDir)
    }
}
