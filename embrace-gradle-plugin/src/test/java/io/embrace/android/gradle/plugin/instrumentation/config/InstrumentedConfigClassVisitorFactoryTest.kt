package io.embrace.android.gradle.plugin.instrumentation.config

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.visitor.ConfigInstrumentationClassVisitor
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentedConfigClassVisitorFactoryTest {

    @Test
    fun createClassVisitor() {
        val factory = ConfigClassVisitorFactory
        val api = ASM_API_VERSION
        val pkg = "io.embrace.android.embracesdk.internal.config.instrumented"
        val cfg = VariantConfig("", null, null, null, null, null)

        assertNull(factory.createClassVisitor("", cfg, api, null))
        assertNull(factory.createClassVisitor("java.lang.Boolean", cfg, api, null))
        assertTrue(
            factory.createClassVisitor("$pkg.BaseUrlConfigImpl", cfg, api, null) is ConfigInstrumentationClassVisitor
        )
        assertTrue(
            factory.createClassVisitor(
                "$pkg.EnabledFeatureConfigImpl",
                cfg,
                api,
                null
            ) is ConfigInstrumentationClassVisitor
        )
        assertTrue(
            factory.createClassVisitor(
                "$pkg.NetworkCaptureConfigImpl",
                cfg,
                api,
                null
            ) is ConfigInstrumentationClassVisitor
        )
        assertTrue(
            factory.createClassVisitor("$pkg.ProjectConfigImpl", cfg, api, null) is ConfigInstrumentationClassVisitor
        )
        assertTrue(
            factory.createClassVisitor("$pkg.RedactionConfigImpl", cfg, api, null) is ConfigInstrumentationClassVisitor
        )
        assertTrue(
            factory.createClassVisitor("$pkg.SessionConfigImpl", cfg, api, null) is ConfigInstrumentationClassVisitor
        )
    }
}
