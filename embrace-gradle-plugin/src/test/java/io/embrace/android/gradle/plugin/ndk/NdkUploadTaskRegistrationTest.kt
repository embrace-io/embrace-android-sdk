package io.embrace.android.gradle.plugin.ndk

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.config.PluginBehaviorImpl
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.gradle.GradleCompatibilityHelper
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
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NdkUploadTaskRegistrationTest {

    private val baseUrl = "https://dsym-store.emb-api.com"
    private val project = ProjectBuilder.builder().build()
    private val behavior =
        PluginBehaviorImpl(project, project.extensions.create("swazzler", SwazzlerExtension::class.java))
    private lateinit var variantConfigurationsListProperty: ListProperty<VariantConfig>

    private val mockVariant = mockk<Variant>(relaxed = true)
    private val testAndroidCompactedVariantData = AndroidCompactedVariantData(
        name = "variantName",
        flavorName = "variantFlavor",
        buildTypeName = "buildTypeName",
        isBuildTypeDebuggable = false,
        versionName = "versionName",
        productFlavors = emptyList(),
        sourceMapPath = "sourceMapPath"
    )

    companion object {
        val unitySymbolsDir = mockk<UnitySymbolsDir>()

        @AfterClass
        @JvmStatic
        fun tearDown() {
            unmockkAll()
        }
    }

    @Before
    fun setUp() {
        every { mockVariant.name } returns "variantName"
        variantConfigurationsListProperty = project.objects.listProperty(VariantConfig::class.java).convention(emptyList())
    }

    private fun registerTestTask(project: Project, taskName: String): TaskProvider<DefaultTask> =
        project.tasks.register(
            taskName,
            DefaultTask::class.java
        )

    @Test
    fun `test configure ndkUploadTask with ndk disabled`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()

        setVariantConfig(ndkEnabled = false)

        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.UNITY }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project = project,
            variant = mockVariant,
            data = testAndroidCompactedVariantData,
            variantConfigurationsListProperty = variantConfigurationsListProperty,
            behavior = behavior,
        )
        assertFalse(
            project.isTaskRegistered(
                taskName,
                testAndroidCompactedVariantData.name
            )
        )
        registration.register(params)

        val ndkUploadTask =
            project.tasks.findByName("$taskName${testAndroidCompactedVariantData.name.capitalizedString()}") as? NdkUploadTask
        assertNull(ndkUploadTask)
    }

    @Test
    fun `test configure ndkUploadTask for native project type`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()

        setVariantConfig(ndkEnabled = true)

        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.NATIVE }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project,
            variant = mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            behavior = behavior,
        )
        assertFalse(
            project.isTaskRegistered(
                taskName,
                testAndroidCompactedVariantData.name
            )
        )
        registration.register(params)

        assertTrue(
            project.isTaskRegistered(
                NdkUploadTask.NAME,
                testAndroidCompactedVariantData.name
            )
        )
    }

    @Test
    fun `test configure ndkUploadTask for unity 2018-2019 project type`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()

        val mergeJniLibFoldersTaskName =
            "merge${testAndroidCompactedVariantData.name.capitalizedString()}JniLibFolders"

        registerTestTask(project, mergeJniLibFoldersTaskName)

        val transformNativeLibsTaskName =
            "transformNativeLibsWithMergeJniLibsFor${testAndroidCompactedVariantData.name.capitalizedString()}"

        setVariantConfig(ndkEnabled = true)

        registerTestTask(project, transformNativeLibsTaskName)

        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.UNITY }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project,
            variant = mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            behavior = behavior,
        )
        assertFalse(
            project.isTaskRegistered(
                taskName,
                testAndroidCompactedVariantData.name
            )
        )
        registration.register(params)

        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${testAndroidCompactedVariantData.name.capitalizedString()}") as NdkUploadTask

        assertTrue(
            project.isTaskRegistered(
                NdkUploadTask.NAME,
                testAndroidCompactedVariantData.name
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

        val mergeJniLibFoldersTaskName =
            "merge${testAndroidCompactedVariantData.name.capitalizedString()}JniLibFolders"

        registerTestTask(project, mergeJniLibFoldersTaskName)

        val mergeNativeLibs =
            "merge${testAndroidCompactedVariantData.name.capitalizedString()}NativeLibs"

        registerTestTask(project, mergeNativeLibs)
        setVariantConfig(ndkEnabled = true)
        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.UNITY }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project,
            variant = mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            behavior = behavior,
        )

        assertFalse(
            project.isTaskRegistered(
                taskName,
                testAndroidCompactedVariantData.name
            )
        )
        registration.register(params)

        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${testAndroidCompactedVariantData.name.capitalizedString()}") as NdkUploadTask

        assertTrue(
            project.isTaskRegistered(
                NdkUploadTask.NAME,
                testAndroidCompactedVariantData.name
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

        setVariantConfig(ndkEnabled = true)
        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.NATIVE }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project,
            variant = mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            behavior = behavior,
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

        setVariantConfig(ndkEnabled = true)
        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.NATIVE }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project,
            variant = mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            behavior = behavior,
        )
        registration.register(params)
        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${testAndroidCompactedVariantData.name.capitalizedString()}") as NdkUploadTask

        assertEquals(
            RequestParams(
                appId = "appId",
                apiToken = "apiToken",
                endpoint = EmbraceEndpoint.NDK,
                failBuildOnUploadErrors = true,
                baseUrl = baseUrl,
            ),
            ndkUploadTask.requestParams.get(),
        )
        assertEquals(ndkUploadTask.unitySymbolsDir.orNull, null)
    }

    @Test
    fun `verify Ndk upload task configuration once is registered for unity`() {
        val taskName = NdkUploadTask.NAME
        val project = ProjectBuilder.builder().build()

        setVariantConfig(ndkEnabled = true)
        val unitySymbolsDirProvider = project.provider { unitySymbolsDir }
        val projectTypeProvider = project.provider { ProjectType.UNITY }

        val registration =
            NdkUploadTaskRegistration(unitySymbolsDirProvider, projectTypeProvider)
        val params = RegistrationParams(
            project,
            variant = mockVariant,
            testAndroidCompactedVariantData,
            variantConfigurationsListProperty,
            behavior = behavior,
        )
        registration.register(params)
        val ndkUploadTask: NdkUploadTask =
            project.tasks.findByName("$taskName${testAndroidCompactedVariantData.name.capitalizedString()}") as NdkUploadTask

        assertEquals(
            RequestParams(
                appId = "appId",
                apiToken = "apiToken",
                endpoint = EmbraceEndpoint.NDK,
                failBuildOnUploadErrors = true,
                baseUrl = baseUrl,
            ),
            ndkUploadTask.requestParams.get(),
        )
        assertEquals(ndkUploadTask.unitySymbolsDir.orNull, unitySymbolsDir)
    }

    private fun setVariantConfig(ndkEnabled: Boolean) {
        GradleCompatibilityHelper.add(
            variantConfigurationsListProperty,
            project.provider {
                VariantConfig(
                    variantName = "variantName",
                    embraceConfig = EmbraceVariantConfig(
                        appId = "appId",
                        apiToken = "apiToken",
                        ndkEnabled = ndkEnabled,
                        sdkConfig = null,
                        unityConfig = null
                    )
                )
            }
        )
    }
}
