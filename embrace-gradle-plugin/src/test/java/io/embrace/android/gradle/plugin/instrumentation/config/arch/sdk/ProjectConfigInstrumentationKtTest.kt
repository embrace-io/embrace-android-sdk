package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.junit.Test

class ProjectConfigInstrumentationKtTest {

    private val cfg = VariantConfig(
        "",
        null,
        null,
        null,
        null,
        null,
        embraceConfig = EmbraceVariantConfig(
            "",
            "",
            null,
            null,
            null,
        )
    )

    private val methods = listOf(
        ConfigMethod("getAppFramework", "()Ljava/lang/String;", "native"),
        ConfigMethod("getBuildId", "()Ljava/lang/String;", "my_id"),
        ConfigMethod("getBuildType", "()Ljava/lang/String;", "build_type"),
        ConfigMethod("getBuildFlavor", "()Ljava/lang/String;", "build_flavor"),
        ConfigMethod("getAppVersionName", "()Ljava/lang/String;", "1.2.3"),
        ConfigMethod("getAppVersionCode", "()Ljava/lang/String;", "123")
    )

    @Test
    fun `test empty cfg`() {
        val instrumentation = createProjectConfigInstrumentation(cfg, null)
        verifyConfigMethodVisitor(instrumentation, ConfigMethod("getAppId", "()Ljava/lang/String;", ""))

        methods.map { it.copy(result = null) }.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `test instrumentation`() {
        val instrumentation = createProjectConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    appId = "abcde",
                    apiToken = null,
                    ndkEnabled = null,
                    sdkConfig = SdkLocalConfig(appFramework = "native"),
                    unityConfig = null
                ),
                buildId = "my_id",
                buildType = "build_type",
                buildFlavor = "build_flavor",
                variantVersion = "1.2.3",
                variantVersionCode = 123
            ),
            reactNativeBundleId = "a1B2c3"
        )
        verifyConfigMethodVisitor(instrumentation, ConfigMethod("getAppId", "()Ljava/lang/String;", "abcde"))
        verifyConfigMethodVisitor(instrumentation, ConfigMethod("getReactNativeBundleId", "()Ljava/lang/String;", "a1B2c3"))
        methods.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }
}
