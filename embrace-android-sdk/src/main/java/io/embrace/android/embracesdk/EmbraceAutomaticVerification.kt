package io.embrace.android.embracesdk

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.embrace.android.embracesdk.samples.AutomaticVerificationChecker
import io.embrace.android.embracesdk.samples.VerificationActions
import io.embrace.android.embracesdk.samples.VerifyIntegrationException
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Class that includes the logic to run the automatic Verification executing EmbraceSamples.verifyIntegration() method.
 *
 * Under the hood this function will create a marker File. If a marker file doesn't already exist, then it execute the following steps:
 * 1. Runs {@link io.embrace.android.embracesdk.samples.VerificationActions#runActions()}
 * 2. Relaunch the application after the action crash
 * 3. Ends session manually and display result
 *
 */
internal class EmbraceAutomaticVerification(
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : ActivityLifecycleListener, ProcessStateListener {
    private val handler = Handler(Looper.getMainLooper())

    private var foregroundEventTriggered = false

    internal lateinit var activityLifecycleTracker: ActivityTracker

    internal lateinit var processStateService: ProcessStateService

    var automaticVerificationChecker = AutomaticVerificationChecker()

    var verificationActions = VerificationActions(Embrace.getInstance(), automaticVerificationChecker)

    /**
     * This flag track if the verification result popup was displayed or not,
     * in case the session continues after running the verification
     */
    private var isResultDisplayed = false

    companion object {
        internal const val TAG = "[EmbraceVerification]"
        private const val ON_FOREGROUND_DELAY = 5000L
        private const val VERIFY_INTEGRATION_DELAY = 200L
        private const val ON_FOREGROUND_TIMEOUT = 5000L
        internal val instance = EmbraceAutomaticVerification()
    }

    fun verifyIntegration() {
        instance.setActivityListener()
        instance.runVerifyIntegration()
    }

    fun setActivityListener() {
        if (!::activityLifecycleTracker.isInitialized) {
            activityLifecycleTracker = checkNotNull(Embrace.getImpl().activityLifecycleTracker)
        }
        if (!::processStateService.isInitialized) {
            processStateService = checkNotNull(Embrace.getImpl().activityService)
        }
        activityLifecycleTracker.addListener(this)
        processStateService.addListener(this)
    }

    /**
     * Started point to run the verification.
     * We use a [ScheduledExecutorService] to give enough time to the onForeground callback
     * to be executed in order to have a valid context/activity
     */
    private fun runVerifyIntegration() {
        try {
            scheduledExecutorService.schedule(
                { startVerification() },
                VERIFY_INTEGRATION_DELAY,
                TimeUnit.MILLISECONDS
            )
        } catch (e: RejectedExecutionException) {
            logInternalError(e, "Start verification rejected")
        }
    }

    fun startVerification() {
        val activity = activityLifecycleTracker.foregroundActivity
        if (activity != null) {
            try {
                if (automaticVerificationChecker.createFile(activity)) {
                    // should run the verification actions
                    showToast(activity, activity.getString(R.string.automatic_verification_started))
                    verificationActions.runActions()
                } else {
                    // the verification was already started
                    logInfo("Verification almost ready...")
                    handler.postDelayed({
                        verifyLifecycle()
                    }, ON_FOREGROUND_TIMEOUT)
                }
            } catch (e: IOException) {
                logInternalError(e, "Embrace SDK cannot run the verification in this moment")
                showToast(activity, activity.getString(R.string.automatic_verification_not_started))
            }
        } else {
            logError("Embrace SDK cannot run the verification in this moment, Activity is not present")
        }
    }

    private fun verifyLifecycle() {
        if (!foregroundEventTriggered) {
            logError("OnForeground event was not triggered")
            val exceptionsService = checkNotNull(Embrace.getImpl().internalErrorService)
            if (verifyIfInitializerIsDisabled()) {
                exceptionsService.handleInternalError(
                    VerifyIntegrationException("ProcessLifecycleInitializer disabled")
                )
                showDialogWithError(R.string.automatic_verification_no_initializer_message)
            } else {
                exceptionsService.handleInternalError(
                    VerifyIntegrationException("onForeground not invoked")
                )
                showDialogWithError(R.string.automatic_verification_lifecycle_error_message)
            }
        }
    }

    fun runEndSession() {
        Embrace.getInstance().endSession()
        logInfo("End session manually")
    }

    /**
     * Tries to detect the condition where the ProcessLifecycleInitializer is removed in the build file
     *
     * @return true if it detects that ProcessLifecycleInitializer is disabled, false otherwise
     */
    private fun verifyIfInitializerIsDisabled(): Boolean {
        logInfo("Trying to verify lifecycle annotations")
        try {
            val appInitializerClass: Class<*>?
            try {
                appInitializerClass = Class.forName("androidx.startup.AppInitializer")
            } catch (cnfe: ClassNotFoundException) {
                return false
            }

            Embrace.getImpl().application?.also { app ->
                val getInstance = appInitializerClass.getMethod("getInstance", Context::class.java)
                val isEagerlyInitialized =
                    appInitializerClass.getMethod("isEagerlyInitialized", Class::class.java)
                val lifecycleInitializerClass =
                    Class.forName("androidx.lifecycle.ProcessLifecycleInitializer")
                val appInitializer = getInstance.invoke(null, app)

                val result = isEagerlyInitialized.invoke(appInitializer, lifecycleInitializerClass) as Boolean
                return result.not()
            } ?: run {
                return false
            }
        } catch (e: Exception) {
            logWarning("Could not verify if lifecycle annotations are working: $e")
        }
        return false
    }

    /**
     * Restarts the app after a forced VerifyIntegrationException
     * was captured as part of the automatic verification
     */
    fun restartAppFromPendingIntent() {
        val exitStatus = 2
        val activity = activityLifecycleTracker.foregroundActivity
        if (activity != null) {
            val intent = activity.intent
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("from_verification", true)

            with(activity) {
                finish()
                startActivity(intent)
            }
            exitProcess(exitStatus)
        } else {
            logError("Cannot restart app, activity is not present")
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        foregroundEventTriggered = true
        val activity = activityLifecycleTracker.foregroundActivity

        if (activity != null) {
            val fromVerification = activity.intent.getBooleanExtra("from_verification", false)

            if (!fromVerification) {
                return
            }

            if (isResultDisplayed) {
                logInfo("onForeground called but the result was already displayed")
                return
            }

            handler.postDelayed({
                runEndSession()
                displayResult()
                clearUserData()
                automaticVerificationChecker.deleteFile()
            }, ON_FOREGROUND_DELAY)
        } else {
            logError("Cannot restart app, activity is not present")
        }
    }

    private fun clearUserData() {
        Embrace.getInstance().clearUserEmail()
        Embrace.getInstance().clearUsername()
        Embrace.getInstance().clearAllUserPersonas()
        Embrace.getInstance().clearUserIdentifier()
        Embrace.getInstance().clearUserAsPayer()
    }

    private fun displayResult() {
        isResultDisplayed = true

        automaticVerificationChecker.isVerificationCorrect()?.also { isCorrect ->
            if (isCorrect) {
                logInfo("Successful - Embrace is ready to go! 🎉")
                showSuccessDialog()
            } else {
                logError("Error - Something is wrong with the Embrace Configuration ⚠️")
                showDialogWithError()
            }
        } ?: logError("Cannot display end message")
    }

    private fun showToast(activity: Activity, message: String) {
        activity.runOnUiThread {
            Toast.makeText(
                activity,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showSuccessDialog() {
        val activity = activityLifecycleTracker.foregroundActivity
        if (activity != null) {
            val dialogBuilder = AlertDialog.Builder(activity)
            dialogBuilder
                .setTitle(activity.getString(R.string.automatic_verification_success_title))
                .setMessage(activity.getString(R.string.automatic_verification_success_message))
                .setCancelable(true)
                .setPositiveButton(activity.getString(R.string.got_it)) { dialog, _ ->
                    dialog.dismiss()
                }
            dialogBuilder.create().show()
        } else {
            logInfo("Verification success!")
        }
    }

    private fun showDialogWithError(errorMessage: Int? = null) {
        val activity = activityLifecycleTracker.foregroundActivity
        if (activity != null) {
            val exceptions = automaticVerificationChecker.getExceptions().map { it.message }.toMutableList()

            if (errorMessage != null) {
                exceptions.add(activity.getString(errorMessage))
            }

            val errorString = if (exceptions.isNotEmpty()) {
                activity.getString(R.string.embrace_verification_errors)
                    .replace("[X]", exceptions.joinToString("\n👉 ", "👉 "))
            } else {
                activity.getString(R.string.automatic_verification_default_error_message)
            }

            val dialogBuilder = AlertDialog.Builder(activity)
            dialogBuilder
                .setTitle(activity.getString(R.string.automatic_verification_error_title))
                .setMessage(errorString)
                .setCancelable(true)
                .setPositiveButton(activity.getString(R.string.close)) { dialog, _ ->
                    dialog.dismiss()
                }
            dialogBuilder.create().show()
        } else {
            logError("Verification error - Cannot display popup")
        }
    }

    private fun logInfo(message: String) {
        Embrace.getInstance().internalInterface.logInfo("$TAG $message", null)
    }

    private fun logWarning(message: String) {
        Embrace.getInstance().internalInterface.logWarning("$TAG $message", null, null)
    }
    private fun logError(message: String) {
        Embrace.getInstance().internalInterface.logError("$TAG $message", null, null, false)
    }

    private fun logInternalError(t: Throwable, message: String? = null) {
        message?.let { logError(message) }
        Embrace.getInstance().internalInterface.logInternalError(t)
    }
}
