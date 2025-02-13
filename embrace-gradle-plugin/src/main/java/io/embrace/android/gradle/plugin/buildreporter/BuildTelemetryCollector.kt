package io.embrace.android.gradle.plugin.buildreporter

import io.embrace.android.gradle.plugin.agp.AgpWrapperImpl
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.gradle.GradleVersion
import io.embrace.android.gradle.plugin.gradle.GradleVersion.Companion.isAtLeast
import io.embrace.android.gradle.plugin.gradle.getProperty
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.system.SystemWrapper
import io.embrace.embrace_gradle_plugin.BuildConfig
import org.gradle.api.Project
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.util.UUID

/**
 * Collects metadata about a customer's build.
 */
class BuildTelemetryCollector {

    private val systemWrapper = SystemWrapper()

    fun collect(
        project: Project,
        behavior: PluginBehavior,
        providerFactory: ProviderFactory,
        variantConfigs: ListProperty<VariantConfig>,
    ): Provider<BuildTelemetryRequest> {
        // first, get telemetry that is ok to capture during the configuration phase
        val configPhaseTelemetry = with(project) {
            BuildTelemetryRequest(
                agpVersion = AgpWrapperImpl(this).version.toString(),
                gradleVersion = getGradleVersion(this),
                isBuildCacheEnabled = isBuildCacheEnabled(),
                isConfigCacheEnabled = isConfigurationCacheEnabled(),
                isGradleParallelExecutionEnabled = isParallelExecutionEnabled(),
                jvmArgs = getJvmArgs(),
                isEdmEnabled = behavior.isUnityEdmEnabled,
                edmVersion = getEdmVersion(),
                metadataRequestId = UUID.randomUUID().toString(),
                pluginVersion = BuildConfig.VERSION,
                operatingSystem = getOperatingSystem(),
                jreVersion = getJreVersion(),
                jdkVersion = getJdkVersion(),
            )
        }
        // then return a provider that is invoked in the execution phase to avoid
        // accidentally performing eager initialisations
        return providerFactory.provider {
            configPhaseTelemetry.copy(
                variantMetadata = variantConfigs.get().map { config ->
                    BuildTelemetryVariant(
                        variantName = config.variantName,
                        appId = config.embraceConfig?.appId,
                        buildId = config.buildId,
                    )
                },
            )
        }
    }

    private fun getGradleVersion(project: Project) = project.gradle.gradleVersion

    private fun getOperatingSystem(): String {
        val osName = getSystemProperty(SYS_PROP_OS_NAME)
        val osVersion = getSystemProperty(SYS_PROP_OS_VERSION)
        val osArch = getSystemProperty(SYS_PROP_OS_ARCH)
        return "$osName $osVersion $osArch"
    }

    private fun getJreVersion() = getSystemProperty(SYS_PROP_JRE_VERSION)
    private fun getJdkVersion() = getSystemProperty(SYS_PROP_JDK_VERSION)
    private fun getSystemProperty(key: String) = systemWrapper.getProperty(key) ?: ""

    private fun Project.isBuildCacheEnabled() = this.gradle.startParameter.isBuildCacheEnabled

    private fun Project.isConfigurationCacheEnabled(): Boolean {
        return try {
            if (isAtLeast(GradleVersion.GRADLE_7_6)) {
                val isConfigurationCacheRequestedMethod =
                    this.gradle.startParameter::class.java.getMethod("isConfigurationCacheRequested")
                return isConfigurationCacheRequestedMethod.invoke(this.gradle.startParameter) as Boolean
            } else {
                (this.gradle.startParameter as StartParameterInternal).configurationCache.get()
            }
        } catch (e: Throwable) {
            false
        }
    }

    private fun Project.isParallelExecutionEnabled() =
        this.gradle.startParameter.isParallelProjectExecutionEnabled

    private fun Project.getJvmArgs() = getProperty(GRADLE_JVM_ARGS).orNull ?: ""
    private fun Project.getEdmVersion() = getProperty(EMBRACE_UNITY_EDM_VERSION).orNull ?: ""
}

private const val SYS_PROP_JRE_VERSION = "java.runtime.version"
private const val SYS_PROP_JDK_VERSION = "java.version"
private const val SYS_PROP_OS_NAME = "os.name"
private const val SYS_PROP_OS_VERSION = "os.version"
private const val SYS_PROP_OS_ARCH = "os.arch"
private const val GRADLE_JVM_ARGS = "org.gradle.jvmargs"
private const val EMBRACE_UNITY_EDM_VERSION = "embrace.edmVersion"
