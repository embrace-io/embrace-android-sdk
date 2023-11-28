package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwnerAccess
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import io.embrace.android.embracesdk.utils.BitmapFactory
import io.embrace.android.embracesdk.utils.JsonValidator
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * The default Base test class, which all tests using TestServer should inherit from. This
 * class will reset the Embrace instance as well as the TestServer before each individual test
 * is run
 */
public open class BaseTest {

    protected lateinit var failedApiCallsFilePath: String
    public lateinit var mContext: EmbraceContext
    protected val gson: Gson = Gson()
    public val testServer: TestServer = TestServer()
    private var fileObserver: EmbraceFileObserver? = null

    @SuppressLint("VisibleForTests")
    @Before
    public fun beforeEach() {
        testServer.start(getDefaultNetworkResponses())

        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        mContext =
            EmbraceContext(InstrumentationRegistry.getInstrumentation().context.applicationContext)

        failedApiCallsFilePath = mContext.cacheDir.absolutePath + "/emb_failed_api_calls.json"

        // attach our mock context to the ProcessLifecycleOwner, this will give us control over the
        // activity/application lifecycle for callbacks registered with the ProcessLifecycleOwner
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwnerAccess.attach(mContext)
        }
        clearCacheFolder()

        Embrace.setImpl(EmbraceImpl())

        setLocalConfig()
        refreshScreenshotBitmap()
    }

    @After
    public fun afterEach() {
        Log.e("TestServer", "Stop Embrace")
        Embrace.getImpl().stop()
        testServer.stop()
        fileObserver?.stopWatching()
    }

    private fun clearCacheFolder() {
        mContext.cacheDir.deleteRecursively()
    }

    private fun getDefaultNetworkResponses(): Map<String, TestServerResponse> {
        val config = ConfigHooks.getConfig()
        val configMockResponse = TestServerResponse(HttpURLConnection.HTTP_OK, gson.toJson(config))

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
        mContext.sdkConfig = ConfigHooks.getSdkConfig(baseUrl)
    }

    /**
     * trigger a specific Lifecycle event to fire in the application. For example, passing in Lifecycle.Event.ON_CREATE
     * will trigger `ActivityLifecycleCallbacks.onCreate()` callbacks as well as those registered with the
     * ProcessLifecycleOwner.
     *
     * Be careful, this method uses a good deal of internal Android code, so if you call lifecycle methods
     * out of their normal order, you may get weird behavior or silent failures. If you would just like to
     * send the application to the foreground or the background, you should call sendForeground() or sendBackground()
     */
    public fun triggerLifecycleEvent(event: Lifecycle.Event) {
        mContext.triggerActivityLifecycleEvent(event)
    }

    public fun triggerOnLowMemory() {
        mContext.triggerOnLowMemory()
    }

    /**
     * send the Application to the Foreground by triggering the ON_START lifecycle callbacks.
     * This method will "catch up" your current lifecycle state to ON_START by calling all methods
     * inbetween. For example, if you do not have current lifecycle state,
     * we will trigger ON_CREATE and then trigger ON_START when this method is invoked
     */
    public fun sendForeground() {
        mContext.sendForeground()
    }

    /**
     * send the Application to the Background by triggering the ON_STOP lifecycle callbacks. This method will "catch up"
     * your current lifecycle state to ON_STOP by calling all methods inbetween. For example, if your current
     * lifecycle state is ON_RESUME, we will trigger ON_PAUSE and then trigger ON_START when this method is invoked
     */
    public fun sendBackground() {
        mContext.sendBackground()
    }

    public fun getScreenshot(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            mContext.screenshotBitmap.width,
            mContext.screenshotBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.setBitmap(bitmap)
        return bitmap
    }

    private fun refreshScreenshotBitmap() {
        mContext.screenshotBitmap = BitmapFactory.newRandomBitmap(BITMAP_WIDTH, BITMAP_HEIGHT)
    }

    /**
     * Starts the Embrace SDK and simulates the application coming into the foreground (which
     * starts a session).
     */
    public fun startEmbraceInForeground() {
        Log.e("TestServer", "Start Embrace")
        Embrace.getInstance().start(mContext)
        assertTrue(Embrace.getInstance().isStarted)
        Log.e("TestServer", "initialize lifecycle to start session")
        sendForeground()
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
        var isStartupStartEventValidated = false

        (0 until TOTAL_REQUESTS_AT_INIT).forEach { _ ->
            waitForRequest { request ->
                when (request.path?.substringBefore("?")) {
                    EmbraceEndpoint.EVENTS.url -> {
                        Assert.assertEquals("POST", request.method)
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
                    }
                    EmbraceEndpoint.SESSIONS.url -> {
                        Assert.assertEquals("POST", request.method)
                        validateMessageAgainstGoldenFile(request, "session-start.json")
                    }
                    EmbraceEndpoint.CONFIG.url -> {
                        Assert.assertEquals("GET", request.method)
                    }
                    else -> fail("Unexpected Request call. ${request.path}")
                }
                println("REQUEST: ${request.path}")
            }
        }
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
    public fun waitForRequest(action: (response: RecordedRequest) -> Unit = {}) {
        val request = testServer.takeRequest()
        request?.let(action) ?: fail(
            "Expected request not sent after configured timeout. " +
                "The SDK probably either failed to send the data or crashed - check Logcat for clues."
        )
    }

    public fun waitForFailedRequest(
        endpoint: EmbraceEndpoint,
        request: () -> Unit,
        action: () -> Unit,
        validate: (file: File) -> Unit
    ) {
        val startSignal = CountDownLatch(1)
        val file = File(failedApiCallsFilePath)

        fileObserver = EmbraceFileObserver(failedApiCallsFilePath, FileObserver.ALL_EVENTS)
        fileObserver?.startWatching(startSignal)

        testServer.addResponse(
            endpoint,
            TestServerResponse(HttpURLConnection.HTTP_BAD_REQUEST)
        )

        request()
        action()
        startSignal.await(500, TimeUnit.MILLISECONDS)
        validate(file)
    }

    public fun validateMessageAgainstGoldenFile(
        request: RecordedRequest,
        goldenFileName: String
    ) {
        try {
            val requestBody = readCompressedRequestBody(request)
            val goldenFileIS = mContext.assets.open("golden-files/$goldenFileName")

            val msg by lazy {
                val observedOutput = writeOutputToDisk(requestBody, goldenFileName, ".observed")
                val expected =
                    mContext.assets.open("golden-files/$goldenFileName").bufferedReader().readText()
                val expectedOutput = writeOutputToDisk(expected, goldenFileName, ".expected")

                "Request ${request.path} differs from golden-files/$goldenFileName. Please check " +
                    "logcat for further details. You can also compare the difference" +
                    " on https://www.jsondiff.com/ by pulling the expected/observed files with adb:\n" +
                    "adb pull ${expectedOutput.absolutePath}\n" +
                    "adb pull ${observedOutput.absolutePath}\n" +
                    "observed: $requestBody"
            }
            assertTrue(msg, JsonValidator.areEquals(goldenFileIS, requestBody))
        } catch (e: IOException) {
            fail("Failed to validate request against golden file. ${e.stackTraceToString()}")
        }
    }

    private fun readCompressedRequestBody(request: RecordedRequest): String {
        return try {
            val data = GZIPInputStream(request.body.inputStream())
            data.use { String(it.readBytes()) }
        } catch (exc: IOException) {
            throw IllegalStateException(
                "Failed to uncompress inputstream of request body. The SDK probably didn't send a " +
                    "request - check Logcat for any crashes in the process.",
                exc
            )
        }
    }

    private fun writeOutputToDisk(
        requestBody: String,
        goldenFilename: String,
        suffix: String
    ): File {
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
    public fun readFileContent(failedApiContent: String, failedCallFileName: String) {
        val failedApiFilePath =
            mContext.cacheDir.path + "/emb_" + failedCallFileName
        val failedApiFile = File(failedApiFilePath)
        val failedApiJsonString: String =
            failedApiFile.reader().use { it.readText() }
        assertTrue(failedApiJsonString.contains(failedApiContent))
    }

    /**
     * Reads the file that contains all the failed api request to get the one we need to validate
     */
    public fun readFile(file: File, failedApiCall: String) {
        try {
            assertTrue(file.exists() && !file.isDirectory)
            val jsonString: String = file.reader().use { it.readText() }
            assertTrue(jsonString.contains(failedApiCall))
        } catch (e: IOException) {
            fail("IOException error: ${e.message}")
        }
    }
}

public const val TOTAL_REQUESTS_AT_INIT: Int = 4
public const val BITMAP_HEIGHT: Int = 100
public const val BITMAP_WIDTH: Int = 100

public enum class EmbraceEndpoint(public val url: String) {
    CONFIG("/v2/config"),
    SCREENSHOT("/v1/screenshot"),
    SESSIONS("/v1/log/sessions"),
    EVENTS("/v1/log/events"),
    LOGGING("/v1/log/logging")
}
