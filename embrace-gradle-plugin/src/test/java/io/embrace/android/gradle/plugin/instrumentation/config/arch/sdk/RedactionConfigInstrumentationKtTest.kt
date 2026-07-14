package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
Aimport io.embrace.android.gradle.plugin.instrumentation.config.model.NetworkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.junit.Test

class RedactionConfigInstrumentationKtTest {

    private val cfg = VariantConfig(
        "",
        null,
        null,
        null,
        null,
    )

    private val methods = listOf(
        ConfigMethod("getSensitiveKeysDenylist", "()Ljava/util/List;", listOf("password")),
        ConfigMethod("getUrlRedactionPatterns", "()Ljava/util/List;", listOf("https://example.com/(secret)")),
    )

    @Test
    fun `test empty cfg`() {
        val instrumentation = createRedactionConfigInstrumentation(cfg)

        methods.map { it.copy(result = null) }.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `test instrumentation`() {
        val instrumentation = createRedactionConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    null,
                    SdkLocalConfig(
                        sensitiveKeysDenylist = listOf("password"),
                        networking = NetworkLocalConfig(
                            urlRedactionPatterns = listOf("https://example.com/(secret)"),
                        ),
                    ),
                    null,
                ),
            ),
        )
        methods.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }
}
