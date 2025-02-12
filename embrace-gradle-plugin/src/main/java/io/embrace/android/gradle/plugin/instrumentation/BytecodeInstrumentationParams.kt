package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.InstrumentationParameters
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

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
     * Whether or not the plugin should operate in debug mode. This will mainly affect
     * logging.
     */
    @get:Optional
    @get:Input
    val logLevel: Property<LogLevel>

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

    /**
     * Allows for invalidating the cache if the user wants to force the Transform to run from
     * scratch.
     *
     * This may be useful if the bytecode params have not changed but the library dependencies have.
     * Gradle's default behaviour is that these changed library dependencies will not be
     * instrumented unless we alter at least one parameter value here.
     */
    @get:Input
    val invalidate: Property<Long>

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
