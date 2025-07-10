@file:Suppress("DEPRECATION")

package io.embrace.android.gradle.plugin.instrumentation.fakes

import io.embrace.android.gradle.plugin.instrumentation.BytecodeInstrumentationParams
import io.embrace.android.gradle.plugin.instrumentation.ClassInstrumentationFilter
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder

class TestBytecodeInstrumentationParams(
    disabled: Boolean = false,
    classInstrumentationFilter: ClassInstrumentationFilter = ClassInstrumentationFilter(emptyList()),
    instrumentFirebaseMessaging: Boolean = SwazzlerExtension.DEFAULT_INSTRUMENT_FIREBASE_MESSAGING,
    instrumentWebview: Boolean = SwazzlerExtension.DEFAULT_INSTRUMENT_WEBVIEW,
    instrumentAutoSdkInitialization: Boolean = false,
    instrumentOkHttp: Boolean = SwazzlerExtension.DEFAULT_INSTRUMENT_OKHTTP,
    instrumentOnLongClick: Boolean = SwazzlerExtension.DEFAULT_INSTRUMENT_ON_LONG_CLICK,
    instrumentOnClick: Boolean = SwazzlerExtension.DEFAULT_INSTRUMENT_ON_CLICK,
    applicationInitTimingEnabled: Boolean = true,
) : BytecodeInstrumentationParams {

    override val config: Property<VariantConfig> =
        DefaultProperty(PropertyHost.NO_OP, VariantConfig::class.javaObjectType).convention(
            VariantConfig("", "", null, null, null, null)
        )
    override val encodedSharedObjectFilesMap: RegularFileProperty = ProjectBuilder.builder().build().objects.fileProperty()
    override val reactNativeBundleId: RegularFileProperty = ProjectBuilder.builder().build().objects.fileProperty()
    override val disabled: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(disabled)
    override val classInstrumentationFilter: Property<ClassInstrumentationFilter> =
        DefaultProperty(PropertyHost.NO_OP, ClassInstrumentationFilter::class.javaObjectType).convention(
            classInstrumentationFilter
        )
    override val shouldInstrumentFirebaseMessaging: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(instrumentFirebaseMessaging)
    override val shouldInstrumentWebview: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(instrumentWebview)
    override val shouldInstrumentAutoSdkInitialization: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(instrumentAutoSdkInitialization)
    override val shouldInstrumentOkHttp: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(instrumentOkHttp)
    override val shouldInstrumentOnLongClick: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(instrumentOnLongClick)
    override val shouldInstrumentOnClick: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(instrumentOnClick)
    override val applicationInitTimingEnabled: Property<Boolean> =
        DefaultProperty(PropertyHost.NO_OP, Boolean::class.javaObjectType).convention(applicationInitTimingEnabled)
}
