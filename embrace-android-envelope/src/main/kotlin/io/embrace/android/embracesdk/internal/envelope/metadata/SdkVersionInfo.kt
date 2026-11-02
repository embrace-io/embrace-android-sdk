package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateEditor
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.edit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stores the values hosted SDKs report in [PersistedSdkStateStore]. Values that [appFramework] doesn't report are
 * never stored.
 */
class SdkVersionInfo(
    private val stateStore: PersistedSdkStateStore,
    private val errorHandler: ErrorHandler,
    private val appFramework: AppFramework,
) : HostedSdkVersionInfo {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override val hostedSdkVersion: String?
        get() = stateStore.snapshot().hostedSdkVersion

    override val hostedPlatformVersion: String?
        get() = stateStore.snapshot().hostedPlatformVersion

    override val unityBuildIdNumber: String?
        get() = stateStore.snapshot().unityBuildIdNumber

    override val javaScriptPatchNumber: String?
        get() = stateStore.snapshot().javaScriptPatchNumber

    override fun update(action: HostedSdkVersionEditor.() -> Unit) {
        val current = stateStore.snapshot()
        if (current.edit { applyHosted(action) } != current) {
            stateStore.update { applyHosted(action) }
            errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, listeners) { it() }
        }
    }

    /**
     * Registers a [listener] invoked once after each [update] that changes a stored value.
     */
    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun PersistedSdkStateEditor.applyHosted(action: HostedSdkVersionEditor.() -> Unit) {
        if (appFramework == AppFramework.NATIVE) {
            return
        }
        val editor = HostedSdkVersionEditor(
            hostedSdkVersion,
            hostedPlatformVersion,
            unityBuildIdNumber,
            javaScriptPatchNumber,
        ).apply(action)
        hostedSdkVersion = editor.hostedSdkVersion
        hostedPlatformVersion = editor.hostedPlatformVersion
        if (appFramework == AppFramework.UNITY) {
            unityBuildIdNumber = editor.unityBuildIdNumber
        }
        if (appFramework == AppFramework.REACT_NATIVE) {
            javaScriptPatchNumber = editor.javaScriptPatchNumber
        }
    }
}
