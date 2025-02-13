package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SdkLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.SessionLocalConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.junit.Test

class SessionConfigInstrumentationKtTest {

    private val cfg = VariantConfig(
        "",
        null,
        null,
        null,
        null
    )

    private val methods = listOf(
        ConfigMethod("getSessionComponents", "()Ljava/util/List;", listOf("component")),
        ConfigMethod("getFullSessionEvents", "()Ljava/util/List;", listOf("event"))
    )

    @Test
    fun `test empty cfg`() {
        val instrumentation = createSessionConfigInstrumentation(cfg)

        methods.map { it.copy(result = null) }.forEach { method ->
            verifyConfigMethodVisitor(instrumentation, method)
        }
    }

    @Test
    fun `test instrumentation`() {
        val instrumentation = createSessionConfigInstrumentation(
            cfg.copy(
                embraceConfig = EmbraceVariantConfig(
                    null,
                    null,
                    null,
                    SdkLocalConfig(
                        sessionConfig = SessionLocalConfig(
                            sessionComponents = listOf("component"),
                            fullSessionEvents = listOf("event")
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
