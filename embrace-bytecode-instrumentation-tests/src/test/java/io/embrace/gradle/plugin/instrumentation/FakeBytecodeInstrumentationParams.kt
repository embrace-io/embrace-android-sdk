package io.embrace.gradle.plugin.instrumentation

import io.embrace.android.gradle.plugin.instrumentation.BytecodeInstrumentationParams
import io.embrace.android.gradle.plugin.instrumentation.ClassInstrumentationFilter
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.gradle.api.provider.Property

class FakeBytecodeInstrumentationParams(
    override val disabled: Property<Boolean> = fakeProperty(false),
    override val shouldInstrumentFirebaseMessaging: Property<Boolean> = fakeProperty(false),
    override val shouldInstrumentWebview: Property<Boolean> = fakeProperty(true),
    override val shouldInstrumentOkHttp: Property<Boolean> = fakeProperty(true),
    override val shouldInstrumentOnLongClick: Property<Boolean> = fakeProperty(true),
    override val shouldInstrumentOnClick: Property<Boolean> = fakeProperty(true),
) : BytecodeInstrumentationParams {
    override val config: Property<VariantConfig>
        get() = TODO("Not yet implemented")
    override val encodedSharedObjectFilesMap: Property<String>
        get() = TODO("Not yet implemented")
    override val classInstrumentationFilter: Property<ClassInstrumentationFilter>
        get() = TODO("Not yet implemented")
}
