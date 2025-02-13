package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PluginBehaviorImplTest {

    private lateinit var project: Project
    private lateinit var extension: SwazzlerExtension
    private lateinit var behavior: PluginBehavior

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        extension = project.extensions.create("swazzler", SwazzlerExtension::class.java)
        behavior = PluginBehaviorImpl(project, extension)
    }

    @Test
    fun `log level default`() {
        assertNull(behavior.logLevel)
    }

    @Test
    fun `log level INFO`() {
        addGradleProperty(EMBRACE_LOG_LEVEL, "info")
        assertEquals(LogLevel.INFO, behavior.logLevel)
    }

    @Test
    fun `log level WARN`() {
        addGradleProperty(EMBRACE_LOG_LEVEL, "WARN")
        assertEquals(LogLevel.WARN, behavior.logLevel)
    }

    @Test
    fun `log level ERROR`() {
        addGradleProperty(EMBRACE_LOG_LEVEL, "ErRoR")
        assertEquals(LogLevel.ERROR, behavior.logLevel)
    }

    @Test
    fun `log level invalid`() {
        addGradleProperty(EMBRACE_LOG_LEVEL, "foo")
        assertNull(behavior.logLevel)
    }

    @Test
    fun `telemetry disabled default`() {
        assertFalse(behavior.isTelemetryDisabled)
    }

    @Test
    fun `telemetry disabled valid`() {
        addGradleProperty(EMBRACE_DISABLE_COLLECT_BUILD_DATA, "true")
        assertTrue(behavior.isTelemetryDisabled)
    }

    @Test
    fun `telemetry disabled invalid`() {
        addGradleProperty(EMBRACE_DISABLE_COLLECT_BUILD_DATA, "foo")
        assertFalse(behavior.isTelemetryDisabled)
    }

    @Test
    fun `unity edm enabled default`() {
        assertFalse(behavior.isUnityEdmEnabled)
    }

    @Test
    fun `unity edm enabled valid`() {
        addGradleProperty(EMBRACE_UNITY_EXTERNAL_DEPENDENCY_MANAGER, "true")
        assertTrue(behavior.isUnityEdmEnabled)
    }

    @Test
    fun `unity edm enabled invalid`() {
        addGradleProperty(EMBRACE_UNITY_EXTERNAL_DEPENDENCY_MANAGER, "foo")
        assertFalse(behavior.isUnityEdmEnabled)
    }

    @Test
    fun `il2cpp upload enabled default`() {
        assertFalse(behavior.isIl2CppMappingFilesUploadEnabled)
    }

    @Test
    fun `il2cpp upload enabled valid`() {
        addGradleProperty(EMBRACE_UPLOAD_IL2CPP_MAPPING_FILES, "true")
        assertTrue(behavior.isIl2CppMappingFilesUploadEnabled)
    }

    @Test
    fun `il2cpp upload enabled invalid`() {
        addGradleProperty(EMBRACE_UPLOAD_IL2CPP_MAPPING_FILES, "foo")
        assertFalse(behavior.isIl2CppMappingFilesUploadEnabled)
    }

    @Test
    fun `mapping file upload disabled default`() {
        assertFalse(behavior.isUploadMappingFilesDisabled)
    }

    @Test
    fun `mapping file upload disabled valid`() {
        addGradleProperty(EMBRACE_DISABLE_MAPPING_FILE_UPLOAD, "true")
        assertTrue(behavior.isUploadMappingFilesDisabled)
    }

    @Test
    fun `mapping file upload disabled invalid`() {
        addGradleProperty(EMBRACE_DISABLE_MAPPING_FILE_UPLOAD, "foo")
        assertFalse(behavior.isUploadMappingFilesDisabled)
    }

    @Test
    fun `base url default`() {
        assertEquals(DEFAULT_SYMBOL_STORE_HOST_URL, behavior.baseUrl)
    }

    @Test
    fun `base url http`() {
        val httpExample = "http://example.com"
        addGradleProperty(EMBRACE_BASE_URL, httpExample)
        assertEquals(httpExample, behavior.baseUrl)
    }

    @Test
    fun `base url https`() {
        val httpsExample = "https://example.com"
        addGradleProperty(EMBRACE_BASE_URL, httpsExample)
        assertEquals(httpsExample, behavior.baseUrl)
    }

    @Test
    fun `base url without protocol`() {
        val domainExample = "example.com"
        addGradleProperty(EMBRACE_BASE_URL, domainExample)
        assertEquals("https://$domainExample", behavior.baseUrl)
    }

    @Test
    fun `react native project false`() {
        val rnDir = findRnNodeModulesDir()
        rnDir.deleteRecursively()
        assertFalse(behavior.isReactNativeProject)
    }

    @Test
    fun `react native project true`() {
        val rnDir = findRnNodeModulesDir()
        rnDir.mkdirs()
        assertTrue(behavior.isReactNativeProject)
    }

    @Test
    fun `autoAddEmbraceDependencies default`() {
        assertTrue(behavior.autoAddEmbraceDependencies)
    }

    @Test
    fun `autoAddEmbraceDependencies disabled`() {
        extension.disableDependencyInjection.set(true)
        assertFalse(behavior.autoAddEmbraceDependencies)
    }

    @Test
    fun `autoAddEmbraceDependency disabled`() {
        extension.disableDependencyInjection.set(true)
        assertFalse(behavior.autoAddEmbraceDependencies)
    }

    /**
     * If Unity EDM is enabled, Embrace dependency should not be auto-added.
     */
    @Test
    fun `autoAddEmbraceComposeDependency unity edm override`() {
        addGradleProperty(EMBRACE_UNITY_EXTERNAL_DEPENDENCY_MANAGER, "true")
        assertFalse(behavior.autoAddEmbraceDependencies)
    }

    @Test
    fun `autoAddEmbraceComposeDependency default`() {
        assertFalse(behavior.autoAddEmbraceComposeDependency)
    }

    @Test
    fun `autoAddEmbraceComposeDependency enabled`() {
        extension.disableComposeDependencyInjection.set(false)
        assertTrue(behavior.autoAddEmbraceComposeDependency)
    }

    @Test
    fun `customSymbolDirectory default`() {
        assertEquals("", behavior.customSymbolsDirectory)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `customSymbolDirectory override`() {
        val dir = "/foo"
        extension.customSymbolsDirectory.set(dir)
        assertEquals(dir, behavior.customSymbolsDirectory)
    }

    @Test
    fun `instrumentation enabled for variant`() {
        assertFalse(behavior.isInstrumentationDisabledForVariant("foo"))
    }

    @Test
    fun `instrumentation disabled for variant`() {
        val disabledVariant = "foo"
        extension.variantFilter = Action {
            if (it.name == disabledVariant) {
                it.enabled = false
            }
        }
        assertTrue(behavior.isInstrumentationDisabledForVariant(disabledVariant))
        assertFalse(behavior.isInstrumentationDisabledForVariant("bar"))
    }

    @Test
    fun `plugin enabled for variant`() {
        assertFalse(behavior.isPluginDisabledForVariant("foo"))
    }

    @Test
    fun `plugin disabled for variant`() {
        val disabledVariant = "foo"
        extension.variantFilter = Action {
            if (it.name == disabledVariant) {
                it.swazzlerOff = true
            }
        }
        assertTrue(behavior.isPluginDisabledForVariant(disabledVariant))
        assertFalse(behavior.isPluginDisabledForVariant("bar"))
    }

    private fun addGradleProperty(key: String, value: String) {
        project.extensions.extraProperties[key] = value
    }

    private fun findRnNodeModulesDir(): File {
        val rootFile = project.layout.projectDirectory.asFile.parentFile?.parentFile
            ?: error("Parent directory of project root is null")

        return File("${File("${rootFile.path}/node_modules").path}/react-native")
    }
}
