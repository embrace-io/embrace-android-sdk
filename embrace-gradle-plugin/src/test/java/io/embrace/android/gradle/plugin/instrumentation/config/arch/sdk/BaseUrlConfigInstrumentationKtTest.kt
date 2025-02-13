package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.model.BaseUrlLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.junit.Test

class BaseUrlConfigInstrumentationKtTest {

    private val cfg = VariantConfig(
        "",
        null,
        null,
        null,
        null
    )

    private val methods = listOf(
        ConfigMethod("getConfig", "()Ljava/lang/String;", "config.example.com"),
        ConfigMethod("getData", "()Ljava/lang/String;", "data.example.com")
    )

    @Test
    fun `test empty cfg`() {
        val instrumentation = createBaseUrlConfigInstrumentation(cfg)

        methods.map { it.copy(result = null) }.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `test instrumentation`() {
        val instrumentation = createBaseUrlConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    null,
                    SdkLocalConfig(
                        baseUrls = BaseUrlLocalConfig(
                            config = "config.example.com",
                            data = "data.example.com"
                        )
                    ),
                    null
                )
            )
        )
        methods.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }
}
