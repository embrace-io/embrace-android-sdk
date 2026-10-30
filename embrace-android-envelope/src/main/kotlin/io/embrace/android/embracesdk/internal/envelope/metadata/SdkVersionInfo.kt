package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stores the values hosted SDKs report under the given keys. A value without a key is never stored.
 */
class SdkVersionInfo(
    private val impl: KeyValueStore,
    private val errorHandler: ErrorHandler,
    private val sdkVersionKey: String? = null,
    private val platformVersionKey: String? = null,
    private val unityBuildIdKey: String? = null,
    private val javaScriptPatchKey: String? = null,
) : HostedSdkVersionInfo {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override val hostedSdkVersion: String?
        get() = sdkVersionKey?.let(impl::getString)

    override val hostedPlatformVersion: String?
        get() = platformVersionKey?.let(impl::getString)

    override val unityBuildIdNumber: String?
        get() = unityBuildIdKey?.let(impl::getString)

    override val javaScriptPatchNumber: String?
        get() = javaScriptPatchKey?.let(impl::getString)

    override fun update(action: HostedSdkVersionEditor.() -> Unit) {
        val sdkVersion = hostedSdkVersion
        val platformVersion = hostedPlatformVersion
        val unityBuildId = unityBuildIdNumber
        val javaScriptPatch = javaScriptPatchNumber
        val editor = HostedSdkVersionEditor(sdkVersion, platformVersion, unityBuildId, javaScriptPatch).apply(action)
        val changes = listOfNotNull(
            changeOf(sdkVersionKey, sdkVersion, editor.hostedSdkVersion),
            changeOf(platformVersionKey, platformVersion, editor.hostedPlatformVersion),
            changeOf(unityBuildIdKey, unityBuildId, editor.unityBuildIdNumber),
            changeOf(javaScriptPatchKey, javaScriptPatch, editor.javaScriptPatchNumber),
        )
        if (changes.isNotEmpty()) {
            impl.editAndCommit {
                changes.forEach { (key, value) -> putString(key, value) }
            }
            errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, listeners) { it() }
        }
    }

    /**
     * Registers a [listener] invoked once after each [update] that changes a stored value.
     */
    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun changeOf(key: String?, old: String?, new: String?): Pair<String, String?>? =
        if (key != null && old != new) key to new else null
}
