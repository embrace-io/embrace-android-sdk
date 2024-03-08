package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import java.net.BindException
import java.net.ConnectException
import java.net.HttpRetryException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

/**
 * Intercepts Embrace SDK's exceptions errors and forwards them to the Embrace API.
 */
internal class EmbraceInternalErrorService(
    private val processStateService: ProcessStateService,
    private val clock: Clock,
    private val logStrictMode: Boolean
) : InternalErrorService {
    private var configService: ConfigService? = null
    private var err: LegacyExceptionError? = null

    override val currentExceptionError: LegacyExceptionError?
        get() = err

    // ignore network-related exceptions since they are expected
    private val ignoredExceptionClasses by lazy {
        setOf<Class<*>>(
            BindException::class.java,
            ConnectException::class.java,
            HttpRetryException::class.java,
            NoRouteToHostException::class.java,
            PortUnreachableException::class.java,
            ProtocolException::class.java,
            SocketException::class.java,
            SocketTimeoutException::class.java,
            UnknownHostException::class.java,
            UnknownServiceException::class.java
        )
    }

    private val ignoredExceptionStrings by lazy {
        ignoredExceptionClasses.map { it.name }
    }

    override fun setConfigService(configService: ConfigService?) {
        this.configService = configService
    }

    private fun ignoreThrowableCause(
        throwable: Throwable?,
        capturedThrowable: HashSet<Throwable>
    ): Boolean {
        return if (throwable != null) {
            if (ignoredExceptionClasses.contains(throwable.javaClass)) {
                logDeveloper(
                    "EmbraceInternalErrorService",
                    "Exception ignored: " + throwable.javaClass
                )
                true
            } else {
                /* if Hashset#add returns true means that the throwable was properly added,
                if it returns false, the object already exists in the set so we return false
                because we are in presence of a cycle in the Throwable cause */
                val addResult = capturedThrowable.add(throwable)
                addResult && ignoreThrowableCause(throwable.cause, capturedThrowable)
            }
        } else {
            false
        }
    }

    @Synchronized
    override fun handleInternalError(throwable: Throwable) {
        logDebug("ignoreThrowableCause - handleInternalError")
        if (ignoredExceptionClasses.contains(throwable.javaClass)) {
            logDeveloper("EmbraceInternalErrorService", "Exception ignored: " + throwable.javaClass)
            return
        } else {
            val capturedThrowable = HashSet<Throwable>()
            if (ignoreThrowableCause(throwable.cause, capturedThrowable)) {
                return
            }
        }

        // If the exception has been wrapped in another exception, the ignored exception name will
        // show up as the start of the message, delimited by a semicolon.
        val message = throwable.message

        if (message != null && ignoredExceptionStrings.contains(
                message.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0]
            )
        ) {
            logDeveloper("EmbraceInternalErrorService", "Ignored exception: $throwable")
            return
        }
        if (err == null) {
            err = LegacyExceptionError(logStrictMode)
        }

        // if the config service has not been set yet, capture the exception
        if (configService == null || configService?.dataCaptureEventBehavior?.isInternalExceptionCaptureEnabled() == true) {
            logDeveloper(
                "EmbraceInternalErrorService",
                "Capturing exception, config service is not set yet: $throwable"
            )
            err?.addException(
                throwable,
                getApplicationState(),
                clock
            )
        }
    }

    private fun getApplicationState(): String = when {
        processStateService.isInBackground -> APPLICATION_STATE_BACKGROUND
        else -> APPLICATION_STATE_ACTIVE
    }

    @Synchronized
    override fun resetExceptionErrorObject() {
        err = null
    }

    companion object {

        /**
         * Signals to the API that the application was in the foreground.
         */
        private const val APPLICATION_STATE_ACTIVE = "active"

        /**
         * Signals to the API that the application was in the background.
         */
        private const val APPLICATION_STATE_BACKGROUND = "background"
    }
}
