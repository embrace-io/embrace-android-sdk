package io.embrace.android.embracesdk.internal.capture.startup

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Component that captures various timestamps throughout the startup process and uses that information to log spans that approximates to
 * the canonical cold and warm "Time to First Display" metric.
 *
 * In the abstract, we want to be as close as possible to approximate the duration between the start of the app process, to when the first
 * frame is completely rendered in the first useful activity. Because of the different capabilities of each Android version, we are
 * measuring slightly different events during startup, so there will be several flavors the startup trace depending on which version of
 * Android the app is currently running on.
 *
 * Data this component provides will be used along side manually set our captured data by [AppStartupTraceEmitter] to create the final
 * traces.
 *
 * For approximating the first frame being completely drawn:
 *
 * - Android 10 onwards, we use a [ViewTreeObserver.OnDrawListener] callback to detect that the first frame from the first activity load
 *   has been fully rendered and queued for display.
 *
 * - Older Android versions that are supported, we just use when the first Activity was resumed. We will iterate on this in the future.
 *
 * Note that this implementation has benefited from the work of Pierre-Yves Ricau and his blog post about Android application launch time
 * that can be found here: https://blog.p-y.wtf/tracking-android-app-launch-in-production. PY's code was adapted and tweaked for use here.
 */
internal class StartupTracker(
    private val appStartupDataCollector: AppStartupDataCollector,
    private val logger: EmbLogger,
    private val versionChecker: VersionChecker,
) : Application.ActivityLifecycleCallbacks {
    private var isFirstDraw = false
    private var nullWindowCallbackErrorLogged = false
    private var startupActivityId: Int? = null

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.useAsStartupActivity()) {
            appStartupDataCollector.startupActivityPreCreated()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.useAsStartupActivity()) {
            val activityName = activity.localClassName
            appStartupDataCollector.startupActivityInitStart()
            if (versionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
                if (!isFirstDraw) {
                    val window = activity.window
                    if (window.callback != null) {
                        window.onDecorViewReady {
                            val decorView = window.decorView
                            decorView.onNextDraw {
                                if (!isFirstDraw) {
                                    isFirstDraw = true
                                    val callback = { appStartupDataCollector.firstFrameRendered(activityName = activityName) }
                                    decorView.viewTreeObserver.registerFrameCommitCallback(callback)
                                }
                            }
                        }
                    } else if (!nullWindowCallbackErrorLogged) {
                        logger.logError("Fail to attach frame rendering callback because the callback on Window was null")
                        nullWindowCallbackErrorLogged = true
                    }
                }
            }
        }
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.useAsStartupActivity()) {
            appStartupDataCollector.startupActivityPostCreated()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.isStartupActivity()) {
            appStartupDataCollector.startupActivityInitEnd()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.observeForStartup()) {
            appStartupDataCollector.startupActivityResumed(activityName = activity.localClassName)
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * Returns true if the Activity instance is being used as the startup Activity. It will return false if [useAsStartupActivity] has
     * not been called previously to setup the Activity instance to be used as the startup Activity.
     */
    private fun Activity.isStartupActivity(): Boolean {
        return if (observeForStartup()) {
            startupActivityId == hashCode()
        } else {
            false
        }
    }

    /**
     * Use this Activity instance as the startup activity if appropriate. Return true the current instance is the startup Activity
     * instance going forward, false otherwise.
     */
    private fun Activity.useAsStartupActivity(): Boolean {
        if (isStartupActivity()) {
            return true
        }

        if (observeForStartup()) {
            startupActivityId = hashCode()
        }

        return isStartupActivity()
    }

    companion object {
        private class PyNextDrawListener(
            val view: View,
            val onDrawCallback: () -> Unit
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
            private val delegate: Window.Callback
        ) : Window.Callback by delegate {

            val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

            override fun onContentChanged() {
                onContentChangedCallbacks.removeAll { callback ->
                    !callback()
                }
                delegate.onContentChanged()
            }
        }

        fun Activity.observeForStartup(): Boolean = !javaClass.isAnnotationPresent(StartupActivity::class.java)

        fun View.onNextDraw(onDrawCallback: () -> Unit) {
            viewTreeObserver.addOnDrawListener(
                PyNextDrawListener(this, onDrawCallback)
            )
        }

        fun Window.onDecorViewReady(onDecorViewReady: () -> Unit) {
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
    }
}
