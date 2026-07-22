package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.Context
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Future

internal class RnBundleIdTrackerImpl(
    private val context: Context,
    private val configService: ConfigService,
    private val store: KeyValueStore,
    private val metadataBackgroundWorker: BackgroundWorker,
) : RnBundleIdTracker {

    private var reactNativeBundleId: Future<String?> =
        if (configService.appFramework == AppFramework.REACT_NATIVE) {
            metadataBackgroundWorker.submit<String?> {
                val lastKnownJsBundleUrl = javaScriptBundleURL
                val lastKnownJsBundleId = javaScriptBundleId
                if (!lastKnownJsBundleUrl.isNullOrEmpty() && !lastKnownJsBundleId.isNullOrEmpty()) {
                    // If we have a lastKnownJsBundleId, we use that as the last known bundle ID.
                    return@submit lastKnownJsBundleId
                } else {
                    // If we don't have a lastKnownJsBundleId, we compute the bundle ID from the last known JS bundle URL.
                    // If the last known JS bundle URL is null, we set React Native bundle ID to the buildId.
                    return@submit computeReactNativeBundleId(
                        context,
                        lastKnownJsBundleUrl,
                        configService.buildInfo.rnBundleId,
                    )
                }
            }
        } else {
            metadataBackgroundWorker.submit<String?> { configService.buildInfo.buildId }
        }

    /**
     * Return the bundle Id if it was already calculated in background or null if it's not ready yet.
     * This way, we avoid blocking the main thread to wait for the value.
     */
    override fun getReactNativeBundleId(): String? =
        if (configService.appFramework == AppFramework.REACT_NATIVE && reactNativeBundleId.isDone) {
            reactNativeBundleId.get()
        } else {
            null
        }

    override fun setReactNativeBundleId(
        jsBundleUrl: String?,
        forceUpdate: Boolean?,
    ) {
        val currentUrl = javaScriptBundleURL

        if (currentUrl != jsBundleUrl || forceUpdate == true) {
            // It`s a new JS bundle URL, save the new value in preferences.
            javaScriptBundleURL = jsBundleUrl

            // Calculate the bundle ID for the new bundle URL
            reactNativeBundleId = metadataBackgroundWorker.submit<String?> {
                val bundleId = computeReactNativeBundleId(
                    context,
                    jsBundleUrl,
                    configService.buildInfo.rnBundleId,
                )
                if (forceUpdate != null) {
                    // if we have a value for forceUpdate, it means the bundleId is cacheable and we should store it.
                    javaScriptBundleId = bundleId
                }
                bundleId
            }
        }
    }

    private var javaScriptBundleURL: String?
        get() = store.getString(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = store.edit { putString(JAVA_SCRIPT_BUNDLE_URL_KEY, value) }

    private var javaScriptBundleId: String?
        get() = store.getString(JAVA_SCRIPT_BUNDLE_ID_KEY)
        set(value) = store.edit { putString(JAVA_SCRIPT_BUNDLE_ID_KEY, value) }

    companion object {
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"

        private fun getBundleAssetName(bundleUrl: String): String {
            return bundleUrl.substring(bundleUrl.indexOf("://") + 3)
        }

        private fun getBundleAsset(
            context: Context,
            bundleUrl: String,
        ): InputStream? {
            runCatching {
                return context.assets.open(getBundleAssetName(bundleUrl))
            }
            return null
        }

        private fun getCustomBundleStream(bundleUrl: String): InputStream? {
            runCatching {
                return FileInputStream(bundleUrl)
            }
            return null
        }

        internal fun computeReactNativeBundleId(
            context: Context,
            bundleUrl: String?,
            defaultBundleId: String?,
        ): String? {
            if (bundleUrl == null) {
                // If JS bundle URL is null, we set React Native bundle ID to the defaultBundleId.
                return defaultBundleId
            }

            // checks if the bundle url is an asset
            val bundleStream = if (bundleUrl.contains("assets")) {
                // looks for the bundle file in assets
                getBundleAsset(context, bundleUrl)
            } else {
                // looks for the bundle file from the custom path
                getCustomBundleStream(bundleUrl)
            }
            if (bundleStream == null) {
                return defaultBundleId
            }
            runCatching {
                bundleStream.use { inputStream ->
                    val md = MessageDigest.getInstance("MD5")
                    var read: Int
                    val data = ByteArray(8192)
                    // Feed the bundle to the digest incrementally so the whole file is
                    // never held in memory at once (RN bundles can be tens of MB).
                    while (inputStream.read(data, 0, data.size).also { read = it } != -1) {
                        md.update(data, 0, read)
                    }
                    return formatMd5Digest(md.digest())
                }
            }
            // if the hashing of the JS bundle URL fails, returns the default bundle ID
            return defaultBundleId
        }

        private fun formatMd5Digest(bundleHashed: ByteArray): String {
            val hashBundle: String
            val sb = StringBuilder()
            for (b in bundleHashed) {
                sb.append(String.format(Locale.ENGLISH, "%02x", b.toInt() and 0xff))
            }
            hashBundle = sb.toString().uppercase(Locale.ENGLISH)
            return hashBundle
        }
    }
}
