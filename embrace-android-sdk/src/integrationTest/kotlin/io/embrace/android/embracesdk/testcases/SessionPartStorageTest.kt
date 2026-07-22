package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.delivery.storage.session.PersistedSession
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that a real session driven through the SDK is persisted into a directory of
 * independently-written Wire files by
 * [io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartStore], and that
 * reconstituting that directory yields an envelope equivalent to the one the delivery pipeline sends.
 */
@RunWith(AndroidJUnit4::class)
internal class SessionPartStorageTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(fakeStorageLayer = true)
    }

    @Test
    fun `session part is stored as a directory of wire files and reconstitutes to an equivalent envelope`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    // produce a completed span (appended to completed_spans.wire) and a log
                    embrace.startSpan("test-span")?.stop()
                    embrace.logInfo("Hi from the session-part storage test")
                }
            },
            assertAction = {
                val expected = getSingleSessionEnvelope()
                val expectedPartId = checkNotNull(expected.sessionPartId())

                val store = checkNotNull(testRule.bootstrapper.deliveryModule?.sessionPartStore)
                val directory = checkNotNull(
                    store.completedDirectories().firstOrNull {
                        PersistedSession.fromDirName(it.dir.name)?.sessionPartId == expectedPartId
                    },
                ) { "No session-part directory found for session part $expectedPartId" }

                // 1. the multi-file layout exists on disk
                assertTrue("manifest missing", directory.manifestFile.exists())
                assertTrue("metadata missing", directory.metadataFile.exists())
                assertTrue("completed spans missing", directory.completedSpansFile.exists())
                assertTrue("session span missing", directory.sessionSpanFile.exists())

                // 2. reconstitution yields an equivalent envelope
                val actual = checkNotNull(store.reconstitute(directory))

                assertEquals(expected.version, actual.version)
                assertEquals(expected.type, actual.type)
                assertEquals(expected.resource, actual.resource)
                assertEquals(expected.metadata, actual.metadata)
                assertEquals(expected.data.sharedLibSymbolMapping, actual.data.sharedLibSymbolMapping)

                // completed spans + the session span round-trip (compared by id, ordering aside)
                assertEquals(expected.spanIds(), actual.spanIds())
                assertEquals(expected.snapshotIds(), actual.snapshotIds())
                assertNotNull("session span not reconstituted", actual.sessionPartId())
                assertEquals(expectedPartId, actual.sessionPartId())
            },
        )
    }

    private fun Envelope<SessionPartPayload>.sessionPartId(): String? =
        (data.spans.orEmpty() + data.spanSnapshots.orEmpty())
            .firstOrNull { it.name == "emb-session" }
            ?.attributes?.firstOrNull { it.key == EmbSessionAttributes.EMB_SESSION_PART_ID }
            ?.data

    private fun Envelope<SessionPartPayload>.spanIds(): Set<String> =
        data.spans.orEmpty().mapNotNull { it.spanId }.toSet()

    private fun Envelope<SessionPartPayload>.snapshotIds(): Set<String> =
        data.spanSnapshots.orEmpty().mapNotNull { it.spanId }.toSet()
}
