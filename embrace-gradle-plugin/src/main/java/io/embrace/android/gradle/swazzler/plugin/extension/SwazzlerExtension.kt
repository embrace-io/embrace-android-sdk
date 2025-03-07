package io.embrace.android.gradle.swazzler.plugin.extension

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * An extension that can be used to configure the plugin.
 */
@Suppress("DEPRECATION")
@Deprecated("Deprecated. Use EmbraceExtension instead.")
abstract class SwazzlerExtension(objectFactory: ObjectFactory) {

    @Deprecated("This property is deprecated and is no longer respected.")
    val forceIncrementalOverwrite: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.autoAddEmbraceDependencies instead.")
    val disableDependencyInjection: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.autoAddEmbraceComposeDependency instead.")
    val disableComposeDependencyInjection: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("This property is deprecated and is no longer respected.")
    val disableRNBundleRetriever: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.bytecodeInstrumentation.okhttpEnabled instead.")
    val instrumentOkHttp: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.bytecodeInstrumentation.onClickEnabled instead.")
    val instrumentOnClick: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.bytecodeInstrumentation.onLongClickEnabled instead.")
    val instrumentOnLongClick: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.bytecodeInstrumentation.webviewOnPageStartedEnabled instead.")
    val instrumentWebview: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("Use embrace.bytecodeInstrumentation.firebasePushNotificationsEnabled instead.")
    val instrumentFirebaseMessaging: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Deprecated("This property is deprecated and is no longer respected.")
    val customSymbolsDirectory: Property<String> =
        objectFactory.property(String::class.java).convention(DEFAULT_CUSTOM_SYMBOLS_DIRECTORY)

    @Deprecated("Use embrace.bytecodeInstrumentation.classIgnorePatterns instead.")
    val classSkipList: ListProperty<String> = objectFactory.listProperty(String::class.java)

    @Deprecated("Use embrace.buildVariantFilter instead.")
    var variantFilter: Action<Variant>? = null

    @Deprecated("Use embrace.buildVariantFilter instead.")
    fun SwazzlerExtension.variantFilter(variantFilter: Action<Variant>?) {
        this.variantFilter = variantFilter
    }

    /**
     * @return true if the variant is disabled.
     */
    @Deprecated("Use embrace.buildVariantFilter instead.")
    fun isSwazzlingDisabled(variantName: String): Boolean {
        val variant = Variant(variantName)
        variantFilter?.execute(variant)
        return !variant.enabled
    }

    /**
     * It determines if the plugin should be off for given variant.
     *
     * @return true if the plugin should be turned off for given variant.
     */
    @Deprecated("Use embrace.buildVariantFilter instead.")
    fun isPluginDisabledForVariant(variantName: String): Boolean {
        val variant = Variant(variantName)
        variantFilter?.execute(variant)
        return variant.swazzlerOff
    }

    /**
     * Represents a build variant.
     */
    @Deprecated("Use embrace.buildVariantFilter instead.")
    class Variant internal constructor(val name: String) {
        var enabled: Boolean = true
        var swazzlerOff: Boolean = false

        @Deprecated("Use embrace.buildVariantFilter.disableBytecodeInstrumentationForVariant() instead.")
        fun setSwazzlingEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        @Deprecated("Use embrace.buildVariantFilter.disablePluginForVariant() instead.")
        fun disablePluginForVariant() {
            this.swazzlerOff = true
        }
    }

    companion object {
        const val DEFAULT_INSTRUMENT_OKHTTP: Boolean = true
        const val DEFAULT_INSTRUMENT_ON_CLICK: Boolean = true
        const val DEFAULT_INSTRUMENT_ON_LONG_CLICK: Boolean = true
        const val DEFAULT_INSTRUMENT_WEBVIEW: Boolean = true
        const val DEFAULT_INSTRUMENT_FIREBASE_MESSAGING: Boolean = false
        private const val DEFAULT_CUSTOM_SYMBOLS_DIRECTORY = ""
    }
}
