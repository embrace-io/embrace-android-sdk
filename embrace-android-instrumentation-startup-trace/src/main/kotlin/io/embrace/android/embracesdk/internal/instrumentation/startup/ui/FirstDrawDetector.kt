package io.embrace.android.embracesdk.internal.instrumentation.startup.ui

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Component that uses the [ViewTreeObserver.OnDrawListener] callback to detect that the first frame of a registered
 * [Activity] has been fully rendered and queued for display.
 *
 * This implementation has benefited from the work of Pierre-Yves Ricau and his blog post about Android application launch time
 * that can be found here: https://blog.p-y.wtf/tracking-android-app-launch-in-production. PY's code was adapted and tweaked for use here.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class FirstDrawDetector(
    private val errorHandler: ErrorHandler,
) : DrawEventEmitter {

    private val loadingActivities: MutableMap<Int, Runnable> = ConcurrentHashMap()

    // Registrations waiting for their first draw, keyed by instance ID. The token lets a draw listener tell whether
    // its registration is still current, so unregistering or reregistering before the first draw stops it.
    private val pendingDraws: ConcurrentHashMap<Int, Any> = ConcurrentHashMap()
    private val windowCallbacks: MutableSet<PyWindowDelegateCallback> = Collections.newSetFromMap(WeakHashMap())
    private val drawListeners: MutableSet<PyNextDrawListener> = Collections.newSetFromMap(WeakHashMap())

    @Volatile
    private var closed = false

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        drawCompleteCallback: () -> Unit,
    ) {
        if (closed) {
            return
        }
        val instanceId = traceInstanceId(activity)
        if (!trackingLoad(instanceId)) {
            val window = activity.window
            if (window.callback != null) {
                val registrationToken = Any()
                pendingDraws[instanceId] = registrationToken
                window.onDecorViewReady {
                    val decorView = window.decorView
                    decorView.onNextDraw {
                        // closed is checked as close() may have run before the registration token was stored
                        if (!closed && pendingDraws.remove(instanceId, registrationToken) && !trackingLoad(instanceId)) {
                            drawBeginCallback()
                            // the same instance must be registered and stored so that it can be unregistered later
                            val frameCommitCallback = Runnable {
                                if (!closed) {
                                    errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail, drawCompleteCallback)
                                }
                            }
                            loadingActivities[instanceId] = frameCommitCallback
                            decorView.viewTreeObserver.registerFrameCommitCallback(frameCommitCallback)
                        }
                    }
                }
            } else if (!loadingActivities.containsKey(instanceId)) {
                errorHandler.trackSdkError(
                    type = SdkErrorType.UiCallbackFail,
                    throwable = IllegalStateException(
                        "Fail to attach frame rendering callback because the callback on Window was null",
                    ),
                )

                // Adding an empty function indicates that the registration has failed and no subsequent attempts should
                // be made for this instance. This prevents over-logging of errors for the same instance if the callback
                // the window is null, should that ever happen.
                loadingActivities[instanceId] = Runnable { }
            }
        }
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
        val instanceId = traceInstanceId(activity)
        pendingDraws.remove(instanceId)
        loadingActivities[instanceId]?.let { firstDrawCallback ->
            errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
                activity.window.decorView.viewTreeObserver.unregisterFrameCommitCallback(firstDrawCallback)
            }
            loadingActivities.remove(instanceId)
        }
    }

    override fun close() {
        closed = true
        pendingDraws.clear()
        loadingActivities.clear()
        synchronized(windowCallbacks) {
            windowCallbacks.forEach(PyWindowDelegateCallback::close)
            windowCallbacks.clear()
        }
        synchronized(drawListeners) {
            drawListeners.forEach(PyNextDrawListener::close)
            drawListeners.clear()
        }
    }

    private fun trackingLoad(instanceId: Int): Boolean = loadingActivities.containsKey(instanceId)

    private fun View.onNextDraw(onDrawCallback: () -> Unit) {
        synchronized(drawListeners) {
            if (closed) {
                return
            }
            val listener =
                PyNextDrawListener(this) { errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail, onDrawCallback) }
            drawListeners += listener
            viewTreeObserver.addOnDrawListener(listener)
        }
    }

    private fun Window.onDecorViewReady(onDecorViewReady: () -> Unit) {
        if (callback != null) {
            if (peekDecorView() == null) {
                onContentChanged {
                    errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail, onDecorViewReady)
                    return@onContentChanged false
                }
            } else {
                onDecorViewReady()
            }
        }
    }

    private fun Window.onContentChanged(onDrawCallbackInvocation: () -> Boolean) {
        synchronized(windowCallbacks) {
            if (closed) {
                return
            }
            val currentCallback = callback
            val callback = if (currentCallback is PyWindowDelegateCallback && currentCallback in windowCallbacks) {
                currentCallback
            } else {
                val newCallback = PyWindowDelegateCallback(currentCallback)
                windowCallbacks += newCallback
                callback = newCallback
                newCallback
            }
            callback.addOnContentChangedCallback(onDrawCallbackInvocation)
        }
    }

    private class PyNextDrawListener(
        val view: View,
        onDrawCallback: () -> Unit,
    ) : ViewTreeObserver.OnDrawListener, AutoCloseable {
        val handler = Handler(Looper.getMainLooper())
        var invoked = false

        // nulled on close so that a listener still waiting for a draw does not retain the SDK
        @Volatile
        private var onDrawCallback: (() -> Unit)? = onDrawCallback

        override fun close() {
            onDrawCallback = null
        }

        override fun onDraw() {
            if (!invoked) {
                invoked = true
                onDrawCallback?.invoke()
                onDrawCallback = null
                handler.post {
                    if (view.viewTreeObserver.isAlive) {
                        view.viewTreeObserver.removeOnDrawListener(this)
                    }
                }
            }
        }
    }

    private class PyWindowDelegateCallback(
        private val delegate: Window.Callback,
    ) : Window.Callback by delegate, AutoCloseable {

        // only accessed on the main thread
        @Volatile
        private var onContentChangedCallbacks: MutableList<() -> Boolean>? = mutableListOf()

        fun addOnContentChangedCallback(callback: () -> Boolean) {
            onContentChangedCallbacks?.add(callback)
        }

        override fun close() {
            onContentChangedCallbacks = null
        }

        override fun onContentChanged() {
            onContentChangedCallbacks?.removeAll { callback ->
                !callback()
            }
            delegate.onContentChanged()
        }
    }
}
