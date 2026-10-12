package io.embrace.android.embracesdk.instrumentation.leaks

import androidx.lifecycle.ViewModel
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import java.io.Closeable
import java.lang.reflect.Method

// retain a reference for use in bytecode instrumentation
internal var viewModelLeakTracker: ViewModelLeakTracker? = null

/**
 * Tracks ViewModel leaks, opening each ViewModel as its constructor returns and closing it when the ViewModel is cleared.
 */
internal class ViewModelLeakTracker(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
) {

    /**
     * Lifecycle 2.5 to 2.7's addCloseable(Closeable), looked up reflectively since 2.8+ compiles it as synthetic. Newer versions
     * use addCloseable(AutoCloseable) which we invoke directly - in which case this value remains `null`.
     */
    @Volatile
    private var legacyAddCloseable: Method? = null

    @Volatile
    private var useLegacyAddCloseable = false

    fun onViewModelCreated(viewModel: ViewModel) {
        if (addCloseable(viewModel, ClearedListener(viewModel))) {
            leakDetector.trackOpened(viewModel)
        }
    }

    private fun addCloseable(viewModel: ViewModel, closeable: Closeable): Boolean {
        if (!useLegacyAddCloseable) {
            try {
                viewModel.addCloseable(closeable)
                return true
            } catch (_: NoSuchMethodError) {
                legacyAddCloseable = runCatching {
                    ViewModel::class.java.getMethod("addCloseable", Closeable::class.java)
                }.getOrNull()
                useLegacyAddCloseable = true
            }
        }
        val method = legacyAddCloseable ?: return false
        return runCatching { method.invoke(viewModel, closeable) }.isSuccess
    }

    /**
     * Registered on each ViewModel via addCloseable, which closes it when the ViewModel is cleared.
     */
    private inner class ClearedListener(private val viewModel: ViewModel) : Closeable {
        override fun close() {
            leakDetector.trackClosed(viewModel, LeakContext(VIEW_MODEL_OBJECT_TYPE, activeSessionIdsProvider()))
        }
    }

    private companion object {

        /**
         * Reported as [LeakContext.objectType].
         */
        const val VIEW_MODEL_OBJECT_TYPE = "view_model"
    }
}
