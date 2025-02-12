package io.embrace.android.gradle.swazzler.plugin.extension

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * An extension that can be used to configure the plugin.
 */
abstract class SwazzlerExtension(objectFactory: ObjectFactory) {

    val forceIncrementalOverwrite: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)
    val disableDependencyInjection: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)
    val disableComposeDependencyInjection: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)
    val disableRNBundleRetriever: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)
    val instrumentOkHttp: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(DEFAULT_INSTRUMENT_OKHTTP)
    val instrumentOnClick: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(DEFAULT_INSTRUMENT_ON_CLICK)
    val instrumentOnLongClick: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(DEFAULT_INSTRUMENT_ON_LONG_CLICK)
    val instrumentWebview: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(DEFAULT_INSTRUMENT_WEBVIEW)
    val instrumentFirebaseMessaging: Property<Boolean> =
        objectFactory.property(Boolean::class.java)
            .convention(DEFAULT_INSTRUMENT_FIREBASE_MESSAGING)

    // It is now ignored because we're automatically detecting all native symbols. This can be removed.
    @get:Deprecated("")
    val customSymbolsDirectory: Property<String> =
        objectFactory.property(String::class.java).convention(DEFAULT_CUSTOM_SYMBOLS_DIRECTORY)
    val classSkipList: ListProperty<String> =
        objectFactory.listProperty(String::class.java).convention(emptyList())

    var variantFilter: Action<Variant>? = null

    fun SwazzlerExtension.variantFilter(variantFilter: Action<Variant>?) {
        this.variantFilter = variantFilter
    }

    /**
     * @return true if the variant is disabled.
     */
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
    fun isPluginDisabledForVariant(variantName: String): Boolean {
        val variant = Variant(variantName)
        variantFilter?.execute(variant)
        return variant.swazzlerOff
    }

    /**
     * Represents a build variant.
     */
    class Variant internal constructor(val name: String) {
        var enabled: Boolean = true
        var swazzlerOff: Boolean = false

        fun setSwazzlingEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

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
