package io.embrace.android.gradle.plugin.instrumentation.fakes

import com.android.build.api.instrumentation.InstrumentationContext
import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.Property

@Suppress("UnstableApiUsage")
class TestInstrumentationContext : InstrumentationContext {

    override val apiVersion: Property<Int> =
        DefaultProperty(PropertyHost.NO_OP, Int::class.javaObjectType).convention(ASM_API_VERSION)
}
