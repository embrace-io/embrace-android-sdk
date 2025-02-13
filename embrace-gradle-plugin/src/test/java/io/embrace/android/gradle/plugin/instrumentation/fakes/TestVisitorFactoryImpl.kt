@file:Suppress("UnstableApiUsage")

package io.embrace.android.gradle.plugin.instrumentation.fakes

import com.android.build.api.instrumentation.InstrumentationContext
import io.embrace.android.gradle.plugin.instrumentation.BytecodeInstrumentationParams
import io.embrace.android.gradle.plugin.instrumentation.EmbraceClassVisitorFactory
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.Property

internal class TestVisitorFactoryImpl(
    override val instrumentationContext: InstrumentationContext = TestInstrumentationContext(),
    params: BytecodeInstrumentationParams = TestBytecodeInstrumentationParams()
) : EmbraceClassVisitorFactory() {
    override val parameters: Property<BytecodeInstrumentationParams> =
        DefaultProperty(PropertyHost.NO_OP, BytecodeInstrumentationParams::class.javaObjectType).convention(params)
}
