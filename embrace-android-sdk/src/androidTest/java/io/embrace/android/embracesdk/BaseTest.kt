package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.lifecycle.ProcessLifecycleOwnerAccess
import androidx.test.platform.app.InstrumentationRegistry
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.WebViewVitals
import io.embrace.android.embracesdk.internal.EmbraceContext
import io.embrace.android.embracesdk.internal.EmbraceFileObserver
import io.embrace.android.embracesdk.internal.TestServer
import io.embrace.android.embracesdk.internal.TestServerResponse
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.utils.BitmapFactory
import io.embrace.android.embracesdk.utils.JsonValidator
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import logTestMessage

/**
 * The default Base test class, which all tests using TestServer should inherit from. This
 * class will reset the Embrace instance as well as the TestServer before each individual test
 * is run
 */
internal open class BaseTest {

    private lateinit var pendingApiCallsFilePath: String
    lateinit var mContext: EmbraceContext
    protected val serializer = EmbraceSerializer()
    private val testServer: TestServer = TestServer()
    private var fileObserver: EmbraceFileObserver? = null
    private val storageDir by lazy { File(mContext.filesDir, "embrace") }

    private val remoteConfig = RemoteConfig(
        webViewVitals = WebViewVitals(100f, 100)
    )

    @SuppressLint("VisibleForTests")
    @Before
    fun beforeEach() {
        logTestMessage("Starting test server")
        testServer.start(getDefaultNetworkResponses())

        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        mContext =
            EmbraceContext(InstrumentationRegistry.getInstrumentation().context.applicationContext)

        pendingApiCallsFilePath = storageDir.absolutePath + "/emb_failed_api_calls.json"

        // attach our mock context to the ProcessLifecycleOwner, this will give us control over the
        // activity/application lifecycle for callbacks registered with the ProcessLifecycleOwner
        Handler(Looper.getMainLooper()).post {
            logTestMessage("Attaching mock context to ProcessLifecycleOwner")
            ProcessLifecycleOwnerAccess.attach(mContext)
        }

        logTestMessage("Clearing cache folder")
        clearCacheFolder()

        logTestMessage("Setting new embrace instance")
        Embrace.setImpl(EmbraceImpl())

        logTestMessage("Setting local config")
        setLocalConfig()
        refreshScreenshotBitmap()
    }

    @After
    fun afterEach() {
        logTestMessage("Stop Embrace")
        Embrace.getImpl().stop()
        testServer.stop()
        fileObserver?.stopWatching()
    }

    private fun clearCacheFolder() {
        storageDir.deleteRecursively()
    }

    private fun getDefaultNetworkResponses(): Map<String, TestServerResponse> {
        val configMockResponse =
            TestServerResponse(HttpURLConnection.HTTP_OK, serializer.toJson(remoteConfig))

        return mapOf(
            EmbraceEndpoint.LOGGING.url to TestServerResponse(HttpURLConnection.HTTP_OK),
            EmbraceEndpoint.EVENTS.url to TestServerResponse(HttpURLConnection.HTTP_OK),
            EmbraceEndpoint.SESSIONS.url to TestServerResponse(HttpURLConnection.HTTP_OK),
            EmbraceEndpoint.SCREENSHOT.url to TestServerResponse(HttpURLConnection.HTTP_OK),
            EmbraceEndpoint.CONFIG.url to configMockResponse
        )
    }

    private fun setLocalConfig() {
        val baseUrl = testServer.getBaseUrl()
        mContext.appId = "default-test-app-Id"
        mContext.sdkConfig = run {
            val sdkConfig = SdkLocalConfig(
                networking = NetworkLocalConfig(
                    // When this config is true, an error related with openConnection reflection
                    // method not found was being thrown on this test:
                    // io.embrace.android.embracesdk.LogMessageTest.logHandledExceptionTest
                    enableNativeMonitoring = false
                ),
                baseUrls = BaseUrlLocalConfig(
                    config = baseUrl,
                    data = baseUrl,
                    dataDev = baseUrl,
                    images = baseUrl
                )
            )
            val json = serializer.toJson(sdkConfig)
            Base64.encodeToString(
                json.toByteArray(StandardCharsets.UTF_8),
                Base64.DEFAULT
            )
        }
    }

    /**
     * send the Application to the Foreground by triggering the ON_START lifecycle callbacks.
     * This method will "catch up" your current lifecycle state to ON_START by calling all methods
     * between. For example, if you do not have current lifecycle state,
     * we will trigger ON_CREATE and then trigger ON_START when this method is invoked
     */
    private fun sendForeground() {
        logTestMessage("Sending application to the foreground")
        mContext.sendForeground()
    }

    /**
     * send the Application to the Background by triggering the ON_STOP lifecycle callbacks. This method will "catch up"
     * your current lifecycle state to ON_STOP by calling all methods inbetween. For example, if your current
     * lifecycle state is ON_RESUME, we will trigger ON_PAUSE and then trigger ON_START when this method is invoked
     */
    fun sendBackground() {
        logTestMessage("Sending application to the background")
        mContext.sendBackground()
    }

    private fun refreshScreenshotBitmap() {
        mContext.screenshotBitmap = BitmapFactory.newRandomBitmap(BITMAP_WIDTH, BITMAP_HEIGHT)
    }

    /**
     * Starts the Embrace SDK and simulates the application coming into the foreground (which
     * starts a session).
     */
    fun startEmbraceInForeground() {
        logTestMessage("Starting Embrace in the foreground")
        Embrace.getInstance().start(mContext)
        assertTrue(Embrace.getInstance().isStarted)
        sendForeground()
        logTestMessage("Adding some data to the session")
        Embrace.getInstance().addBreadcrumb("a message")
        Embrace.getInstance().setUserEmail("user@email.com")
        Embrace.getInstance().setUserIdentifier("some id")
        Embrace.getInstance().setUsername("John Doe")

        // ensure that the 'first_day' persona is always set so that
        // the session message is always deterministic no matter
        // what device it ran on
        Embrace.getInstance().addUserPersona("first_day")

        validateInitializationRequests()
    }

    /**
     * Consume requests done when app starts:
     * - Remote Config request
     * - Session
     * - Startup Moment Start Event
     * - Startup Moment End Event
     * It needs to be done with a for because the order of the requests can be different between runs.
     */
    private fun validateInitializationRequests() {
        logTestMessage("Starting validation of initialization requests")
        var isStartupStartEventValidated = false

        waitForRequest(
            listOf(
                RequestValidator(EmbraceEndpoint.EVENTS) { request ->
                    assertEquals("POST", request.method)
                    if (!isStartupStartEventValidated) {
                        isStartupStartEventValidated = true
                        validateMessageAgainstGoldenFile(
                            request,
                            "moment-startup-start-event.json"
                        )
                    } else {
                        validateMessageAgainstGoldenFile(
                            request,
                            "moment-startup-late-event.json"
                        )
                    }
                },
                RequestValidator(EmbraceEndpoint.CONFIG) { request ->
                    assertEquals("GET", request.method)
                })
        )
    }

    private fun handleUnexpectedFailure(request: RecordedRequest) {
        val body = readCompressedRequestBody(request)
        writeFailedOutputToDisk(body, "unexpected-failure", ".observed")
        fail("Unexpected Request call. ${request.path}")
    }

    /**
     * Blocks the current thread until the SDK makes a HTTP request of the expected type.
     *
     * For example, if you call [sendForeground] the SDK will send a session and this method
     * will get the request. You can then write assertions against that request.
     *
     * If the request is not received within a reasonable amount of time this method will
     * fail the test.
     */
    fun waitForRequest(requestValidator: RequestValidator) {
        waitForRequest(listOf(requestValidator))
    }

    fun waitForRequest(requestValidators: List<RequestValidator>) {
        logTestMessage("Waiting to assert that request was received by MockWebServer.")
        var remainingCount = requestValidators.size

        while (remainingCount > 0) {
            val request = testServer.takeRequest()

            if (request == null) {
                fail(
                    "Expected request not sent after configured timeout. " +
                        "The SDK probably either failed to send the data or crashed - check Logcat for clues."
                )
            }
            val req = checkNotNull(request)
            val path = checkNotNull(req.path)

            val validator = requestValidators.singleOrNull { validator ->
                path.endsWith(validator.endpoint.url)
            }

            if (validator != null) {
                validator.validate(req)
                remainingCount--
            } else {
                logTestMessage("Unexpected request: $path")
            }
        }
    }

    fun waitForFailedRequest(
        endpoint: EmbraceEndpoint,
        embraceOperation: () -> Unit,
        assertion: () -> Unit,
        validate: (file: File) -> Unit
    ) {
        logTestMessage("Waiting to assert that failed request was written to disk.")

        val startSignal = CountDownLatch(1)
        val file = File(pendingApiCallsFilePath)

        fileObserver = EmbraceFileObserver(pendingApiCallsFilePath, FileObserver.ALL_EVENTS)
        fileObserver?.startWatching(startSignal)

        testServer.addResponse(
            endpoint,
            TestServerResponse(ApiClient.NO_HTTP_RESPONSE)
        )

        logTestMessage("Performing Embrace operation that will fail.")

        embraceOperation()

        logTestMessage("Performing assertion on Embrace action. that will fail.")

        assertion()
        startSignal.await(FAILED_REQUEST_WAIT_TIME_MS, TimeUnit.MILLISECONDS)
        validate(file)
    }

    fun validateMessageAgainstGoldenFile(
        request: RecordedRequest,
        goldenFileName: String
    ) {
        logTestMessage("Validating request against golden file $goldenFileName.")

        try {
            val requestBody = readCompressedRequestBody(request)
            logTestMessage("Read request body.")
            val goldenFileIS = mContext.assets.open("golden-files/$goldenFileName")

            logTestMessage("Comparing expected versus observed JSON.")
            val result = JsonValidator.areEquals(goldenFileIS, requestBody)

            if (!result.success) {
                val msg by lazy {
                    val observedOutput =
                        writeFailedOutputToDisk(requestBody, goldenFileName, ".observed")
                    val expected =
                        mContext.assets.open("golden-files/$goldenFileName").bufferedReader()
                            .readText()
                    val expectedOutput =
                        writeFailedOutputToDisk(expected, goldenFileName, ".expected")

                    "Request ${request.path} differs from golden-files/$goldenFileName.\n" +
                        "JSON validation failure reason: ${result.message}" +
                        " Please check logcat output for further information. Logcat output and" +
                        " the expected/observed output is saved as an" +
                        " upload artefact if this ran on CI. You can compare the difference" +
                        " on https://www.jsondiff.com/ by pulling the expected/observed files with " +
                        "adb (if running locally. " +
                        "adb pull ${expectedOutput.absolutePath} " +
                        "adb pull ${observedOutput.absolutePath} " +
                        "Full observed JSON: ${observedOutput.readText()}"
                }
                logTestMessage(msg)
                fail(msg)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to validate request against golden file.", e)
        }
    }

    private fun readCompressedRequestBody(request: RecordedRequest): String {
        return try {
            val inputStream = request.body.inputStream()
            logTestMessage("Opened input stream to request")

            val data = GZIPInputStream(inputStream)
            logTestMessage("Opened Gzip stream to request")

            data.use { String(it.readBytes()) }
        } catch (exc: IOException) {
            throw IllegalStateException(
                "Failed to uncompress input stream of request body. The SDK probably didn't send a " +
                    "request - check Logcat for any crashes in the process.",
                exc
            )
        }
    }

    private fun writeFailedOutputToDisk(
        requestBody: String,
        goldenFilename: String,
        suffix: String
    ): File {
        logTestMessage("Writing expected/observed output to disk.")
        val dir = File(mContext.externalCacheDir, "test_failure").apply { mkdir() }
        return File(dir, "${goldenFilename}$suffix").apply {
            writeText(requestBody)
        }
    }

    /**
     * Reads the file with the failed api call to validate failedApiContent is present
     *
     * @param failedApiContent the content that was intended to send in the api call
     * @param failedCallFileName file name that contains our failed api request
     */
    fun readFileContent(failedApiContent: String, failedCallFileName: String) {
        val failedApiFilePath = storageDir.path + "/emb_" + failedCallFileName
        val failedApiFile = File(failedApiFilePath)
        logTestMessage("Reading failed API call at $failedApiFilePath")
        GZIPInputStream(failedApiFile.inputStream()).use { stream ->
            logTestMessage("Asserting failed API call contains expected content.")
            val jsonString = String(stream.readBytes())
            assertTrue(jsonString.contains(failedApiContent))
        }
    }

    /**
     * Reads the file that contains all the failed api request to get the one we need to validate
     */
    fun readFile(file: File, failedApiCall: String) {
        try {
            assertTrue(file.exists() && !file.isDirectory)
            val jsonString: String = file.reader().use { it.readText() }
            assertTrue(jsonString.contains(failedApiCall))
        } catch (e: IOException) {
            fail("IOException error: ${e.message}")
        }
    }
}

public const val FAILED_REQUEST_WAIT_TIME_MS: Long = 10000
public const val TOTAL_REQUESTS_AT_INIT: Int = 3
public const val BITMAP_HEIGHT: Int = 100
public const val BITMAP_WIDTH: Int = 100

public enum class EmbraceEndpoint(public val url: String) {
    CONFIG("/v2/config"),
    SCREENSHOT("/v1/screenshot"),
    SESSIONS("/v1/log/sessions"),
    SESSIONS_V2("/v2/spans"),
    EVENTS("/v1/log/events"),
    LOGGING("/v1/log/logging")
}
