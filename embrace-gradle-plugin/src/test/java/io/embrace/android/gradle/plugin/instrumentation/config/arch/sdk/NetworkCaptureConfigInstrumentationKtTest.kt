package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.model.DomainLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.NetworkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.junit.Test

class NetworkCaptureConfigInstrumentationKtTest {

    private val cfg = VariantConfig(
        "",
        null,
        null,
        null,
        null
    )

    private val methods = listOf(
        ConfigMethod("getRequestLimitPerDomain", "()I", 567),
        ConfigMethod("getIgnoredRequestPatternList", "()Ljava/util/List;", listOf("pattern1", "pattern2")),
        ConfigMethod("getNetworkBodyCapturePublicKey", "()Ljava/lang/String;", "my_key"),
        ConfigMethod("getLimitsByDomain", "()Ljava/util/Map;", mapOf("domain1" to "1", "domain2" to "2")),
    )

    @Test
    fun `test empty cfg`() {
        val instrumentation = createNetworkCaptureConfigInstrumentation(cfg)

        methods.map { it.copy(result = null) }.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `test instrumentation`() {
        val instrumentation =
            createNetworkCaptureConfigInstrumentation(
                cfg.copy(
                    embraceConfig = EmbraceVariantConfig(
                        null,
                        null,
                        null,
                        SdkLocalConfig(
                            capturePublicKey = "my_key",
                            networking = NetworkLocalConfig(
                                defaultCaptureLimit = 567,
                                disabledUrlPatterns = listOf("pattern1", "pattern2"),
                                domains = listOf(
                                    DomainLocalConfig("domain1", 1),
                                    DomainLocalConfig("domain2", 2)
                                )
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
