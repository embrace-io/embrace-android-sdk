package io.embrace.android.embracesdk.internal.ui

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

/**
 * Component that uses the [ViewTreeObserver.OnDrawListener] callback to detect that the first frame of a registered
 * [Activity] has been fully rendered and queued for display.
 *
 * This implementation has benefited from the work of Pierre-Yves Ricau and his blog post about Android application launch time
 * that can be found here: https://blog.p-y.wtf/tracking-android-app-launch-in-production. PY's code was adapted and tweaked for use here.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class FirstDrawDetector(
    private val logger: EmbLogger,
) : DrawEventEmitter {
    private var isFirstDraw: Boolean = false
    private var nullWindowCallbackErrorLogged = false

    override fun registerFirstDrawCallback(activity: Activity, completionCallback: () -> Unit) {
        if (!isFirstDraw) {
            val window = activity.window
            if (window.callback != null) {
                window.onDecorViewReady {
                    val decorView = window.decorView
                    decorView.onNextDraw {
                        if (!isFirstDraw) {
                            isFirstDraw = true
                            decorView.viewTreeObserver.registerFrameCommitCallback(completionCallback)
                        }
                    }
                }
            } else if (!nullWindowCallbackErrorLogged) {
                logger.trackInternalError(
                    type = InternalErrorType.UI_CALLBACK_FAIL,
                    throwable = IllegalStateException(
                        "Fail to attach frame rendering callback because the callback on Window was null"
                    )
                )
                nullWindowCallbackErrorLogged = true
            }
        }
    }

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
