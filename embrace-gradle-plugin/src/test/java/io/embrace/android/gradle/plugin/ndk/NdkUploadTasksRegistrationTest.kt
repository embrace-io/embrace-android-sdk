@file:Suppress("DEPRECATION")

package io.embrace.android.gradle.plugin.ndk

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.PluginBehaviorImpl
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.UnityConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.ndk.CompressSharedObjectFilesTask
import io.embrace.android.gradle.plugin.tasks.ndk.EncodeFileToBase64Task
import io.embrace.android.gradle.plugin.tasks.ndk.HashSharedObjectFilesTask
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadTasksRegistration
import io.embrace.android.gradle.plugin.tasks.ndk.UploadSharedObjectFilesTask
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.internal.provider.AbstractProperty.PropertyQueryException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NdkUploadTasksRegistrationTest {

    private val projectBuilder = ProjectBuilder.builder()

    private val testVariantName = "variantName"
    private val testVariantConfig = VariantConfig(
        variantName = testVariantName,
        embraceConfig = createEmbraceVariantConfig()
    )
    private val testAndroidCompactedVariantData = AndroidCompactedVariantData(
        name = testVariantName,
        flavorName = "variantFlavor",
        buildTypeName = "buildTypeName",
        isBuildTypeDebuggable = false,
        versionName = "versionName",
        productFlavors = emptyList(),
        sourceMapPath = "sourceMapPath"
    )

    private val mockVariant = mockk<Variant>(relaxed = true)

    private lateinit var swazzlerExtension: SwazzlerExtension
    private lateinit var embraceExtension: EmbraceExtension
    private lateinit var project: Project
    private lateinit var variantConfigurationsListProperty: ListProperty<VariantConfig>
    private lateinit var testBehavior: PluginBehavior
    private lateinit var testUnitySymbolsDirProvider: Provider<UnitySymbolsDir>
    private lateinit var testProjectTypeProvider: Provider<ProjectType>
    private lateinit var testRegistrationParams: RegistrationParams

    @Before
    fun setUp() {
        every { mockVariant.name } returns testVariantName
        project = projectBuilder.build()
        variantConfigurationsListProperty = project.objects.listProperty(VariantConfig::class.java).convention(emptyList())
        swazzlerExtension = project.extensions.create("swazzler", SwazzlerExtension::class.java)
        embraceExtension = project.extensions.create("embrace", EmbraceExtension::class.java)
        testBehavior = PluginBehaviorImpl(
            project,
            swazzlerExtension,
            embraceExtension
        )
        testUnitySymbolsDirProvider = project.provider { UnitySymbolsDir() }
        testProjectTypeProvider = project.provider { ProjectType.NATIVE }
        testRegistrationParams = RegistrationParams(
            project,
            mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            testBehavior,
        )
    }

    private fun registerTestTask(project: Project, taskName: String): TaskProvider<DefaultTask> =
        project.tasks.register(
            taskName,
            DefaultTask::class.java
        )

    @Test
    fun `skip registration when NDK is disabled`() {
        verifyNoUploadTasksRegistered(
            VariantConfig(
                variantName = testVariantName,
                embraceConfig = createEmbraceVariantConfig(ndkEnabled = false)
            )
        )
    }

    @Test
    fun `tasks are registered but not executed when ndk_enabled property not specified`() {
        verifyTasksRegisteredButNotExecuted(
            VariantConfig(
                variantName = testVariantName,
                embraceConfig = createEmbraceVariantConfig(ndkEnabled = null)
            )
        )
    }

    @Test
    fun `tasks are registered but not executed if no config file specified`() {
        verifyTasksRegisteredButNotExecuted(VariantConfig(testVariantName))
    }

    @Test
    fun `upload tasks do not run for projects that are not unity or native`() {
        // Given a project that is not unity or native
        val projectType = project.provider { ProjectType.OTHER }

        // When NDK upload tasks are registered
        val registration = createNdkUploadTasksRegistration(projectType = projectType)
        registration.register(testRegistrationParams)

        // Then:
        // The compression task should be registered
        assertTaskRegistered(CompressSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)

        // The compression task should not run
        val compressTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}${testAndroidCompactedVariantData.name.capitalizedString()}"
        ) as CompressSharedObjectFilesTask
        assertFalse(compressTask.onlyIf.isSatisfiedBy(compressTask))

        // The upload task should not run
        val uploadTask = project.tasks.findByName(
            "${UploadSharedObjectFilesTask.NAME}${testAndroidCompactedVariantData.name.capitalizedString()}"
        ) as UploadSharedObjectFilesTask
        assertFalse(uploadTask.onlyIf.isSatisfiedBy(uploadTask))

        // The injection task should not run
        val encodingTask = project.tasks.findByName(
            "${EncodeFileToBase64Task.NAME}${testAndroidCompactedVariantData.name.capitalizedString()}"
        ) as EncodeFileToBase64Task
        assertFalse(encodingTask.onlyIf.isSatisfiedBy(encodingTask))
    }

    @Test
    fun `compression task sets correct must run afters`() {
        // When NDK upload tasks are registered
        val registration = createNdkUploadTasksRegistration()
        registration.register(testRegistrationParams)

        // Then:
        // The compression task should be registered
        val capitalizedString = testAndroidCompactedVariantData.name.capitalizedString()
        val compressionTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}$capitalizedString"
        ) as CompressSharedObjectFilesTask
        assertTrue(project.tasks.contains(compressionTask))

        // The compression task must run after existing native libs merging tasks
        val nativeLibsMergingTasks = listOf(
            registerTestTask(project, "merge${capitalizedString}JniLibFolders"),
            registerTestTask(project, "merge${capitalizedString}NativeLibs"),
            registerTestTask(project, "transformNativeLibsWithMergeJniLibsFor$capitalizedString")
        )

        val compressionTaskDependencies = compressionTask.mustRunAfter.getDependencies(compressionTask)
        nativeLibsMergingTasks.forEach {
            assertTrue(compressionTaskDependencies.contains(it.get()))
        }
    }

    @Test
    fun `tasks are registered correctly`() {
        val registration = createNdkUploadTasksRegistration()
        registration.register(testRegistrationParams)
        assertTaskRegistered(CompressSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(HashSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(UploadSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(EncodeFileToBase64Task.NAME, testAndroidCompactedVariantData.name)
    }

    @Test
    fun `an error is thrown when merge native libs task is not found`() {
        // Given a project where merge native libs task is not registered
        val projectTypeProvider = project.provider { ProjectType.NATIVE }

        // When NDK upload tasks are registered
        val registration = createNdkUploadTasksRegistration(projectType = projectTypeProvider)
        registration.register(testRegistrationParams)

        // Then an exception is thrown when trying to access architecturesDirectory
        val compressionTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}${testAndroidCompactedVariantData.name.capitalizedString()}"
        ) as CompressSharedObjectFilesTask

        val exception = assertThrows(PropertyQueryException::class.java) {
            compressionTask.architecturesDirectory.get()
        }

        assertEquals(
            "Failed to query the value of task ':compressSharedObjectFilesVariantName' property 'architecturesDirectory'.",
            exception.message
        )
        assertEquals("Task with name 'mergeVariantNameNativeLibs' not found in root project 'test'.", exception.cause?.message)
    }

    @Test
    fun `an error is thrown when getSymbolFiles doesn't find any file`() {
        // Given a Unity project with an empty UnitySymbolsDir
        val projectTypeProvider = project.provider { ProjectType.UNITY }
        val variantName = testAndroidCompactedVariantData.name.capitalizedString()
        val unitySymbolsDirProvider = project.provider {
            UnitySymbolsDir()
        }

        // When NDK upload tasks are registered
        val registration =
            createNdkUploadTasksRegistration(projectType = projectTypeProvider, unitySymbolsDir = unitySymbolsDirProvider)
        registration.register(testRegistrationParams)
        registerTestTask(project, "merge${variantName}NativeLibs")

        // Then an exception is thrown when trying to access architecturesDirectory
        val compressionTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}$variantName"
        ) as CompressSharedObjectFilesTask

        val exception = assertThrows(PropertyQueryException::class.java) {
            compressionTask.architecturesDirectory.get()
        }
        assertEquals("Unity shared object files not found", exception.cause?.message)
    }

    @Test
    fun `an error is thrown when customSymbolsDirectory doesn't exist`() {
        // Given a project with a customSymbolsDirectory that doesn't exist
        val projectTypeProvider = project.provider { ProjectType.NATIVE }
        val variantName = testAndroidCompactedVariantData.name.capitalizedString()
        swazzlerExtension.customSymbolsDirectory.set("nonExistentDir")

        // When NDK upload tasks are registered
        val registration = createNdkUploadTasksRegistration(projectType = projectTypeProvider)
        registration.register(testRegistrationParams)
        registerTestTask(project, "merge${variantName}NativeLibs")

        // Then an exception is thrown when trying to access architecturesDirectory
        val compressionTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}$variantName"
        ) as CompressSharedObjectFilesTask

        val exception = assertThrows(PropertyQueryException::class.java) {
            compressionTask.architecturesDirectory.get()
        }
        assertEquals("Custom symbols directory does not exist. Specified path: nonExistentDir", exception.cause?.message)
    }

    private fun createNdkUploadTasksRegistration(
        behavior: PluginBehavior? = null,
        unitySymbolsDir: Provider<UnitySymbolsDir>? = null,
        projectType: Provider<ProjectType>? = null,
        variantConfig: VariantConfig? = null,
    ) = NdkUploadTasksRegistration(
        behavior ?: testBehavior,
        unitySymbolsDir ?: testUnitySymbolsDirProvider,
        projectType ?: testProjectTypeProvider,
        variantConfig ?: testVariantConfig
    )

    private fun createEmbraceVariantConfig(
        appId: String? = "testAppId",
        apiToken: String? = "testApiToken",
        ndkEnabled: Boolean? = true,
        sdkConfig: SdkLocalConfig? = null,
        unityConfig: UnityConfig? = null,
        configStr: String? = null,
    ) = EmbraceVariantConfig(
        appId = appId,
        apiToken = apiToken,
        ndkEnabled = ndkEnabled,
        sdkConfig = sdkConfig,
        unityConfig = unityConfig,
        configStr = configStr
    )

    private fun assertTaskNotRegistered(taskName: String, variantName: String) {
        assertFalse(project.isTaskRegistered(taskName, variantName))
        assertNull(project.tasks.findByName("${taskName}${variantName.capitalizedString()}"))
    }

    private fun assertTaskRegistered(taskName: String, variantName: String) {
        assertTrue(project.isTaskRegistered(taskName, variantName))
        assertNotNull(project.tasks.findByName("${taskName}${variantName.capitalizedString()}"))
    }

    private fun verifyNoUploadTasksRegistered(variantConfig: VariantConfig) {
        // When NDK upload tasks are registered
        val ndkUploadTasksRegistration = createNdkUploadTasksRegistration(variantConfig = variantConfig)
        ndkUploadTasksRegistration.register(testRegistrationParams)

        // Then no tasks should be registered
        assertTaskNotRegistered(CompressSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskNotRegistered(HashSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskNotRegistered(UploadSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskNotRegistered(EncodeFileToBase64Task.NAME, testAndroidCompactedVariantData.name)
    }

    private fun verifyTasksRegisteredButNotExecuted(variantConfig: VariantConfig) {
        // When NDK upload tasks are registered
        val ndkUploadTasksRegistration = createNdkUploadTasksRegistration(variantConfig = variantConfig)
        ndkUploadTasksRegistration.register(testRegistrationParams)

        // Then tasks should be registered
        assertTaskRegistered(CompressSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(HashSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(UploadSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(EncodeFileToBase64Task.NAME, testAndroidCompactedVariantData.name)

        // Compression task will not be executed
        val compressionTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}${testAndroidCompactedVariantData.name.capitalizedString()}"
        ) as CompressSharedObjectFilesTask

        assertFalse(compressionTask.onlyIf.isSatisfiedBy(compressionTask))
    }
}
