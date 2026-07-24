package io.embrace.android.embracesdk.testcases.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class DisableSdkFeatureTest {

    private companion object {
        private const val TEST_PREFIX = "emb_test_"
        private const val SPAN_1 = "${TEST_PREFIX}1"
        private const val SPAN_2 = "${TEST_PREFIX}2"
        private const val SPAN_3 = "${TEST_PREFIX}3"
        private const val LOG_1 = "${TEST_PREFIX}1"
        private const val LOG_2 = "${TEST_PREFIX}2"
        private const val LOG_3 = "${TEST_PREFIX}3"
        // nest the sentinel inside a subdirectory: the payload storage dirs now sweep loose
        // files with unparseable names on startup, but leave subdirectories alone. This still
        // verifies that disable() recursively deletes the storage directories.
        private const val TEST_SUBDIR_NAME = "emb_test_dir"
        private const val TEST_FILE_NAME = "test_file"
        private const val DUMMY_CONTENT = "Hello, world!"

        private fun File.sentinelFile(): File = File(File(this, TEST_SUBDIR_NAME), TEST_FILE_NAME)
    }

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private lateinit var embraceDirs: List<File>

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        embraceDirs = StorageLocation.entries.map { it.asFile(
            logger = FakeInternalLogger(),
            rootDirSupplier = { ctx.filesDir },
            fallbackDirSupplier = { ctx.cacheDir }
        ).value }
    }

    @Test
    fun `disabling SDK stops data export`() {
        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
                // create some dummy values in embrace directories to see if they get deleted
                embraceDirs.forEach {
                    it.sentinelFile().apply {
                        parentFile?.mkdirs()
                        writeText(DUMMY_CONTENT)
                    }
                }
            },
            testCaseAction = {
                embraceDirs.forEach {
                    assertEquals(DUMMY_CONTENT, it.sentinelFile().readText())
                }
                recordSession {
                    embrace.startSpan(SPAN_1).stop()
                    embrace.logInfo(LOG_1)
                    embrace.startSpan(SPAN_2).stop()
                    embrace.logInfo(LOG_2)

                    // disable SDK at this point
                    embrace.disable()

                    // log some more data
                    embrace.startSpan(SPAN_3).stop()
                    embrace.logInfo(LOG_3)
                }
            },
            assertAction = {
                // ensure that the files were deleted by waiting for the background thread
                returnIfConditionMet(
                    desiredValueSupplier = { true },
                    dataProvider = {
                        embraceDirs.all {
                            !it.sentinelFile().exists()
                        }
                    },
                    condition = { true },
                )

                assertEquals(0, getLogEnvelopes(0).size)
                assertEquals(0, getSessionEnvelopes(0).size)
            },
            otelExportAssertion = {
                val spanData = awaitSpans(2) { spanData ->
                    spanData.name.startsWith(TEST_PREFIX)
                }
                val spans = spanData.map { it.name }
                assertEquals(listOf(SPAN_1, SPAN_2), spans)

                val logData = awaitLogs(2) { logData ->
                    val msg = logData.bodyValue?.value.toString()
                    msg.startsWith(TEST_PREFIX)
                }
                assertEquals(listOf(LOG_1, LOG_2), logData.map { it.bodyValue?.value })
            }
        )
    }

    @Test
    fun `calling public methods is safe after SDK disabling`() {
        lateinit var logger: FakeInternalLogger
        testRule.runTest(
            setupAction = {
                logger = getEmbLogger().apply {
                    throwOnInternalError = false
                }
            },
            testCaseAction = {
                recordSession {
                    embrace.disable()
                    embrace.startSpan(SPAN_3).stop()
                    embrace.addBreadcrumb("foo")
                    embrace.logInfo(LOG_1)
                }
                recordSession {
                    embrace.addBreadcrumb("foo")
                    embrace.logInfo(LOG_1)
                }
            },
            assertAction = {
                returnIfConditionMet(
                    desiredValueSupplier = { true },
                    dataProvider = {
                        embraceDirs.all {
                            !it.sentinelFile().exists()
                        }
                    },
                    condition = { true },
                )

                assertEquals(0, getLogEnvelopes(0).size)
                assertEquals(0, getSessionEnvelopes(0).size)
                assertEquals(4, logger.sdkNotInitializedMessages.size)
                assertEquals(2, logger.sdkNotInitializedMessages.filter { it.msg == "add_breadcrumb" }.size)
                assertEquals(2, logger.sdkNotInitializedMessages.filter { it.msg == "log_message" }.size)
            },
        )
    }
}
