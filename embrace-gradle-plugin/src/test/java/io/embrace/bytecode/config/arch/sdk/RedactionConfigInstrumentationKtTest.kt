package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.config.model.EmbraceVariantConfig
import io.embrace.bytecode.config.model.SdkLocalConfig
import io.embrace.bytecode.config.model.VariantConfig
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
