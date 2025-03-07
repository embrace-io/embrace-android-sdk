package io.embrace.android.gradle.plugin.api

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * An extension that is used to configure the Embrace Gradle Plugin's behavior.
 */
abstract class EmbraceExtension @Inject internal constructor(objectFactory: ObjectFactory) {

    /**
     * Whether the Embrace Gradle Plugin should automatically add Embrace dependencies to this module's classpath.
     * Defaults to true.
     */
    val autoAddEmbraceDependencies: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether the Embrace Gradle Plugin should automatically add the embrace-android-compose dependency to this module's classpath.
     * Defaults to false.
     */
    val autoAddEmbraceComposeDependency: Property<Boolean> =
        objectFactory.property(Boolean::class.java)

    /**
     * Whether the Embrace Gradle Plugin should automatically upload mapping files for stacktrace deobfuscation.
     * Defaults to true.
     */
    val mappingFileUploadEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether the Embrace Gradle Plugin should report telemetry on its own performance.
     * Defaults to true.
     */
    val telemetryEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether the Embrace Gradle Plugin should fail the build if it encounters an error during a HTTP request.
     * Defaults to true.
     */
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * DSL for configuring how Embrace instruments bytecode.
     */
    val bytecodeInstrumentation: EmbraceBytecodeInstrumentation =
        objectFactory.newInstance(EmbraceBytecodeInstrumentation::class.java)

    /**
     * DSL for configuring how Embrace instruments bytecode.
     */
    fun bytecodeInstrumentation(action: Action<EmbraceBytecodeInstrumentation>) {
        action.execute(bytecodeInstrumentation)
    }

    /**
     * DSL for configuring the Embrace Gradle Plugin's behavior on a per build variant basis.
     */
    internal var buildVariantFilter: Action<BuildVariant> = Action { }

    /**
     * DSL for configuring the Embrace Gradle Plugin's behavior on a per build variant basis.
     */
    fun EmbraceExtension.buildVariantFilter(buildVariantFilter: Action<BuildVariant>) {
        this.buildVariantFilter = buildVariantFilter
    }

    /**
     * DSL for how the Embrace Gradle Plugin behaves on a specific build variant. You can use this by checking the
     * variant name, and then configuring the build variant as required.
     */
    class BuildVariant internal constructor(val name: String) {

        internal var bytecodeInstrumentationEnabled = true
        internal var pluginEnabled = true

        /**
         * Disables bytecode instrumentation entirely for this specific build variant.
         */
        fun disableBytecodeInstrumentationForVariant() {
            bytecodeInstrumentationEnabled = false
        }

        /**
         * Disables the plugin entirely for this specific build variant, on a best-effort basis
         * given Gradle build lifecycle restrictions.
         */
        fun disablePluginForVariant() {
            pluginEnabled = false
        }
    }
}
