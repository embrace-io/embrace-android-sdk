package io.embrace.android.embracesdk.samples

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EmbraceAutomaticVerification
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Execute actions to verify the following features:
 *  - Log a Breadcrumb
 *  - Set user data
 *  - Add info, warning and error logs
 *  - Start and end a moment
 *  - Executes a GET request
 *  - Add the trace id to the request (default or the one specified in the local config)
 *  - Check the current and the latest SDK version
 *  - Execute a POST request
 *  - Execute a bad request
 *  - Trigger an ANR
 *  - Throw an Exception
 */
internal class VerificationActions(
    private val embraceInstance: Embrace,
    private val logger: InternalEmbraceLogger,
    private val automaticVerificationChecker: AutomaticVerificationChecker,
) {

    companion object {
        private const val THROW_EXCEPTION_DELAY_MILLIS = 100L
        private const val ANR_DURATION_MILLIS = 2000L
        private const val MOMENT_DURATION_MILLIS = 3000L

        private const val networkingGetUrl =
            "https://dash-api.embrace.io/external/sdk/android/version"
        private const val networkingPostUrl = "https://httpbin.org/post"
        private const val networkingWrongUrl = "https://httpbin.org/deaasd/ASdasdkjl"
        private const val networkingPostBody = "{\"key_one\":\"value_one\"}"
        private const val embraceChangelogLink = "https://embrace.io/docs/android/changelog/"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val actionsToVerify = listOf(
        Pair({ setUserData() }, "Set user data"),
        Pair({ executeLogsActions() }, "Log messages"),
        Pair({ executeMoment() }, "Trigger moment"),
        Pair({ executeNetworkHttpGETRequest() }, "Executing network request: GET"),
        Pair({ executeNetworkHttpPOSTRequest() }, "Executing network request: POST"),
        Pair(
            { executeNetworkHttpWrongRequest() },
            "Executing network request: testing a wrong url"
        ),
        Pair({ triggerAnr() }, "Causing an ANR, the application will be tilt"),
        Pair({ throwAnException() }, "Throwing an Exception! 💣")
    )
    private var currentStep = 0
    private val totalSteps = actionsToVerify.size

    private val sampleProperties = mapOf(
        "String" to "Test String",
        "LongString" to "This value will be trimmed Lorem ipsum dolor sit amet, " +
            "consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo " +
            "consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum. " +
            "In culpa qui officia deserunt mollit anim id est laborum.",
        "Float" to 1.0f,
        "Nested Properties" to mapOf("a" to "b", "c" to "d")
    )

    /**
     * Execute actions to verify the following features:
     *  - Log a Breadcrumb
     *  - Set user data
     *  - Add info, warning and error logs
     *  - Start and end a moment
     *  - Executes a GET request
     *  - Check the current and the latest SDK version
     *  - Execute a POST request
     *  - Execute a bad request
     *  - Trigger an ANR
     *  - Throw an Exception
     */
    fun runActions() {
        logger.logInfo("${EmbraceAutomaticVerification.TAG} Starting Verification...")
        embraceInstance.addBreadcrumb("This is a breadcrumb")
        actionsToVerify.forEach {
            verifyAction(it.first, it.second)
        }
    }

    private fun verifyAction(action: () -> Unit, message: String) {
        currentStep++
        try {
            logger.logInfo(
                "${EmbraceAutomaticVerification.TAG} ✓ Step $currentStep/$totalSteps: $message"
            )
            action.invoke()
        } catch (e: Throwable) {
            logger.logError(
                "${EmbraceAutomaticVerification.TAG} -- $message ERROR ${e.localizedMessage}"
            )
            automaticVerificationChecker.addException(e)
        }
    }

    private fun setUserData() {
        val identifier = "1234567890"
        val username = "Mr. Automated User"
        val email = "automated@embrace.io"

        embraceInstance.setUserIdentifier(identifier)
        embraceInstance.setUsername(username)
        embraceInstance.setUserEmail(email)
        embraceInstance.setUserAsPayer()
        embraceInstance.addUserPersona("userPersona")
    }

    private fun executeLogsActions() {
        embraceInstance.logMessage("test info", Severity.INFO, sampleProperties)
        embraceInstance.logMessage("test warn", Severity.WARNING, sampleProperties)
        embraceInstance.logException(
            Throwable("Sample throwable"),
            Severity.ERROR,
            sampleProperties,
            "test error"
        )
    }

    private fun executeMoment() {
        val momentName = "Verify Integration Moment"
        val momentIdentifier = "Verify Integration identifier"
        embraceInstance.startMoment(momentName, momentIdentifier, sampleProperties)
        handler.postDelayed({
            embraceInstance.endMoment(momentName, momentIdentifier)
        }, MOMENT_DURATION_MILLIS)
    }

    fun executeNetworkHttpGETRequest() {
        val connection = URL(networkingGetUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty(
            embraceInstance.traceIdHeader,
            "traceId : ${embraceInstance.traceIdHeader}"
        )

        val data = connection.inputStream.bufferedReader().readText()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw VerifyIntegrationException("RESPONSE CODE IS ${connection.responseCode}")
        }

        checkEmbraceSDKVersion(JSONObject(data).getString("value"))
    }

    private fun checkEmbraceSDKVersion(latestEmbraceVersion: String) {
        val currentVersion = BuildConfig.VERSION_NAME

        if (ComparableVersion(currentVersion) < ComparableVersion(latestEmbraceVersion)) {
            logger.logWarning(
                "${EmbraceAutomaticVerification.TAG} Note that there is a newer version of Embrace available 🙌! " +
                    "You can read the changelog for $latestEmbraceVersion here: $embraceChangelogLink"
            )
        }
    }

    private fun executeNetworkHttpPOSTRequest() {
        val connection = URL(networkingPostUrl).openConnection() as HttpURLConnection
        connection.doOutput = true
        DataOutputStream(connection.outputStream).use { it.writeBytes(networkingPostBody) }

        val result = connection.responseCode

        if (result != HttpURLConnection.HTTP_OK) {
            throw VerifyIntegrationException("RESPONSE CODE IS $result")
        }
    }

    private fun executeNetworkHttpWrongRequest() {
        val connection = URL(networkingWrongUrl).openConnection() as HttpURLConnection
        val result = connection.responseCode
        if (result != HttpURLConnection.HTTP_NOT_FOUND) {
            throw VerifyIntegrationException("RESPONSE CODE IS $result")
        }
    }

    private fun triggerAnr() {
        handler.post { Thread.sleep(ANR_DURATION_MILLIS) }
        logger.logInfo("${EmbraceAutomaticVerification.TAG} ANR Finished")
    }

    private fun throwAnException() {
        handler.postDelayed({
            throw VerifyIntegrationException("Forced Exception to verify integration")
        }, THROW_EXCEPTION_DELAY_MILLIS)
    }
}
