package io.embrace.android.gradle.plugin.instrumentation.config

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.visitor.ConfigInstrumentationClassVisitor
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentedConfigClassVisitorFactoryTest {

    private val config = VariantConfig("", null, null, null, null, null)
    private val api = ASM_API_VERSION
    private val embracePackage = "io.embrace.android.embracesdk.internal.config.instrumented"

    @Test
    fun `should return null for empty class name`() {
        assertNull(createVisitor(""))
    }

    @Test
    fun `should return null for non-config class`() {
        assertNull(createVisitor("java.lang.Boolean"))
    }

    @Test
    fun `should create visitor for BaseUrlConfigImpl`() {
        val visitor = createVisitor("$embracePackage.BaseUrlConfigImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should create visitor for EnabledFeatureConfigImpl`() {
        val visitor = createVisitor("$embracePackage.EnabledFeatureConfigImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should create visitor for NetworkCaptureConfigImpl`() {
        val visitor = createVisitor("$embracePackage.NetworkCaptureConfigImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should create visitor for ProjectConfigImpl`() {
        val visitor = createVisitor("$embracePackage.ProjectConfigImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should create visitor for RedactionConfigImpl`() {
        val visitor = createVisitor("$embracePackage.RedactionConfigImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should create visitor for SessionConfigImpl`() {
        val visitor = createVisitor("$embracePackage.SessionConfigImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should create visitor for Base64SharedObjectFilesMap`() {
        val visitor = createVisitor("$embracePackage.Base64SharedObjectFilesMapImpl")
        assertTrue(visitor is ConfigInstrumentationClassVisitor)
    }

    @Test
    fun `should return null for invalid package name`() {
        assertNull(createVisitor("invalid.package.ClassName"))
    }

    private fun createVisitor(className: String) =
        ConfigClassVisitorFactory.createClassVisitor(className, config, null, api, null)
}
