package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.InstrumentationParameters
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Contains parameters that affect how bytecode is manipulated during instrumentation.
 *
 * As each property is an [Input] the task parameters will be cached if the values do not
 * change between builds.
 */
interface BytecodeInstrumentationParams : InstrumentationParameters {

    /**
     * Representation of the config supplied by the user that will both alter how the plugin behaves
     * & instruments the SDK.
     */
    @get:Input
    val config: Property<VariantConfig>

    /**
     * Whether or not the plugin should operate for this variant.
     */
    @get:Input
    val disabled: Property<Boolean>

    /**
     * Acts as a user-configurable filter by discarding the classes which should be
     * skipped for this variant.
     */
    @get:Input
    val classInstrumentationFilter: Property<ClassInstrumentationFilter>

    @get:Input
    val shouldInstrumentFirebaseMessaging: Property<Boolean>

    @get:Input
    val shouldInstrumentWebview: Property<Boolean>

    @get:Input
    val shouldInstrumentOkHttp: Property<Boolean>

    @get:Input
    val shouldInstrumentOnLongClick: Property<Boolean>

    @get:Input
    val shouldInstrumentOnClick: Property<Boolean>
}
