package io.embrace.android.embracesdk.internal.envelope.metadata

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wraps a [HostedSdkVersionInfo] so that writes made by hosted SDKs can be observed.
 */
class ObservableHostedSdkVersionInfo(
    private val impl: HostedSdkVersionInfo,
) : HostedSdkVersionInfo {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var batching: Boolean = false

    override var hostedSdkVersion: String?
        get() = impl.hostedSdkVersion
        set(value) {
            impl.hostedSdkVersion = value
            notifyChanged()
        }

    override var hostedPlatformVersion: String?
        get() = impl.hostedPlatformVersion
        set(value) {
            impl.hostedPlatformVersion = value
            notifyChanged()
        }

    override var unityBuildIdNumber: String?
        get() = impl.unityBuildIdNumber
        set(value) {
            impl.unityBuildIdNumber = value
            notifyChanged()
        }

    override var javaScriptPatchNumber: String?
        get() = impl.javaScriptPatchNumber
        set(value) {
            impl.javaScriptPatchNumber = value
            notifyChanged()
        }

    override fun batch(action: () -> Unit) {
        batching = true
        try {
            impl.batch(action)
        } finally {
            batching = false
        }
        notifyChanged()
    }

    /**
     * Registers a [listener] invoked whenever one of the hosted SDK values is written.
     */
    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyChanged() {
        if (batching) {
            return
        }
        listeners.forEach { listener ->
            try {
                listener()
            } catch (ignored: Throwable) {
            }
        }
    }
}
