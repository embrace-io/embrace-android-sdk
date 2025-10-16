package io.embrace.android.gradle.plugin.ndk

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.PluginBehaviorImpl
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
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.internal.provider.AbstractProperty.PropertyQueryException
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.provider.ListProperty
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

    private lateinit var embraceExtension: EmbraceExtension
    private lateinit var project: Project
    private lateinit var variantConfigurationsListProperty: ListProperty<VariantConfig>
    private lateinit var testBehavior: PluginBehavior
    private lateinit var testRegistrationParams: RegistrationParams

    @Before
    fun setUp() {
        every { mockVariant.name } returns testVariantName
        project = projectBuilder.build()
        variantConfigurationsListProperty = project.objects.listProperty(VariantConfig::class.java).convention(emptyList())
        embraceExtension = project.extensions.create("embrace", EmbraceExtension::class.java)
        testBehavior = PluginBehaviorImpl(
            project,
            embraceExtension
        )
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
    fun `tasks are not executed when ndk_enabled property is not specified`() {
        verifyNoUploadTasksRegistered(
            VariantConfig(
                variantName = testVariantName,
                embraceConfig = createEmbraceVariantConfig(ndkEnabled = null)
            )
        )
    }

    @Test
    fun `tasks are not registered if no config file specified`() {
        verifyNoUploadTasksRegistered(VariantConfig(testVariantName))
    }

    @Test
    fun `tasks are registered correctly`() {
        val registration = createNdkUploadTasksRegistration()
        registerTestTask(project, "merge${testAndroidCompactedVariantData.name.capitalizedString()}NativeLibs")
        registration.register(testRegistrationParams)
        assertTaskRegistered(CompressSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(HashSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(UploadSharedObjectFilesTask.NAME, testAndroidCompactedVariantData.name)
        assertTaskRegistered(EncodeFileToBase64Task.NAME, testAndroidCompactedVariantData.name)
    }

    @Test
    fun `an error is thrown when merge native libs task is not found`() {
        // When NDK upload tasks are registered
        val registration = createNdkUploadTasksRegistration()
        registration.register(testRegistrationParams)

        // Then an exception is thrown when trying to access architecturesDirectory
        val compressionTask = project.tasks.findByName(
            "${CompressSharedObjectFilesTask.NAME}${testAndroidCompactedVariantData.name.capitalizedString()}"
        ) as CompressSharedObjectFilesTask

        val exception = assertThrows(MissingValueException::class.java) {
            compressionTask.architecturesDirectory.get()
        }

        assertEquals(
            "Cannot query the value of task ':compressSharedObjectFilesVariantName' property" +
                " 'architecturesDirectory' because it has no value available.",
            exception.message
        )
    }

    @Test
    fun `an error is thrown when customSymbolsDirectory doesn't exist`() {
        // Given a project with a customSymbolsDirectory that doesn't exist
        val variantName = testAndroidCompactedVariantData.name.capitalizedString()
        embraceExtension.customSymbolsDirectory.set("nonExistentDir")

        // When NDK upload tasks are registered
        val registration = createNdkUploadTasksRegistration()
        registration.register(testRegistrationParams)

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
        variantConfig: VariantConfig? = null,
    ) = NdkUploadTasksRegistration(
        behavior ?: testBehavior,
        variantConfig ?: testVariantConfig
    )

    private fun createEmbraceVariantConfig(
        appId: String? = "testAppId",
        apiToken: String? = "testApiToken",
        ndkEnabled: Boolean? = true,
        sdkConfig: SdkLocalConfig? = null,
        unityConfig: UnityConfig? = null,
    ) = EmbraceVariantConfig(
        appId = appId,
        apiToken = apiToken,
        ndkEnabled = ndkEnabled,
        sdkConfig = sdkConfig,
        unityConfig = unityConfig
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
}
