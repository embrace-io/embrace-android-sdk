package io.embrace.android.embracesdk.internal.ui

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.capture.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.concurrent.ConcurrentHashMap

/**
 * Component that uses the [ViewTreeObserver.OnDrawListener] callback to detect that the first frame of a registered
 * [Activity] has been fully rendered and queued for display.
 *
 * This implementation has benefited from the work of Pierre-Yves Ricau and his blog post about Android application launch time
 * that can be found here: https://blog.p-y.wtf/tracking-android-app-launch-in-production. PY's code was adapted and tweaked for use here.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class FirstDrawDetector(
    private val logger: EmbLogger,
) : DrawEventEmitter {

    private val loadingActivities: MutableMap<Int, () -> Unit> = ConcurrentHashMap()

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        firstFrameDeliveredCallback: () -> Unit
    ) {
        val instanceId = traceInstanceId(activity)
        if (!trackingLoad(instanceId)) {
            val window = activity.window
            if (window.callback != null) {
                window.onDecorViewReady {
                    val decorView = window.decorView
                    decorView.onNextDraw {
                        if (!trackingLoad(instanceId)) {
                            drawBeginCallback()
                            loadingActivities[instanceId] = firstFrameDeliveredCallback
                            decorView.viewTreeObserver.registerFrameCommitCallback(firstFrameDeliveredCallback)
                        }
                    }
                }
            } else if (!loadingActivities.containsKey(instanceId)) {
                logger.trackInternalError(
                    type = InternalErrorType.UI_CALLBACK_FAIL,
                    throwable = IllegalStateException(
                        "Fail to attach frame rendering callback because the callback on Window was null"
                    )
                )

                // Adding an empty function indicates that the registration has failed and no subsequent attempts should
                // be made for this instance. This prevents over-logging of errors for the same instance if the callback
                // the window is null, should that ever happen.
                loadingActivities[instanceId] = { }
            }
        }
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
        val instanceId = traceInstanceId(activity)
        loadingActivities[instanceId]?.let { firstDrawCallback ->
            runCatching {
                activity.window.decorView.viewTreeObserver.unregisterFrameCommitCallback(firstDrawCallback)
            }.exceptionOrNull()?.let { exception ->
                logger.trackInternalError(
                    type = InternalErrorType.UI_CALLBACK_FAIL,
                    throwable = IllegalStateException("Failed to unregister first draw callback", exception)
                )
            }
            loadingActivities.remove(instanceId)
        }
    }

    private fun trackingLoad(instanceId: Int): Boolean = loadingActivities.containsKey(instanceId)

    private fun View.onNextDraw(onDrawCallback: () -> Unit) {
        viewTreeObserver.addOnDrawListener(
            PyNextDrawListener(this, onDrawCallback)
        )
    }

    private fun Window.onDecorViewReady(onDecorViewReady: () -> Unit) {
        if (callback != null) {
            if (peekDecorView() == null) {
                onContentChanged {
                    onDecorViewReady()
                    return@onContentChanged false
                }
            } else {
                onDecorViewReady()
            }
        }
    }

    private fun Window.onContentChanged(onDrawCallbackInvocation: () -> Boolean) {
        val currentCallback = callback
        val callback = if (currentCallback is PyWindowDelegateCallback) {
            currentCallback
        } else {
            val newCallback = PyWindowDelegateCallback(currentCallback)
            callback = newCallback
            newCallback
        }
        callback.onContentChangedCallbacks += onDrawCallbackInvocation
    }

    private class PyNextDrawListener(
        val view: View,
        val onDrawCallback: () -> Unit,
    ) : ViewTreeObserver.OnDrawListener {
        val handler = Handler(Looper.getMainLooper())
        var invoked = false

        override fun onDraw() {
            if (!invoked) {
                invoked = true
                onDrawCallback()
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
    ) : Window.Callback by delegate {

        val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

        override fun onContentChanged() {
            onContentChangedCallbacks.removeAll { callback ->
                !callback()
            }
            delegate.onContentChanged()
        }
    }
}
