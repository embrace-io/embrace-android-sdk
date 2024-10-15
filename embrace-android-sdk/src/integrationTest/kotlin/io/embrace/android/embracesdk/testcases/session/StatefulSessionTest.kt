package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.hasSpanSnapshotsOfType
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.opentelemetry.embErrorLogCount
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class StatefulSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `session messages are recorded`() {
        testRule.runTest(
            setupAction = {
                useMockWebServer = true
                overriddenConfigService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(v2StorageEnabled = true)
            },
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, World!")
                }

                // capture another session
                recordSession()
            },
            assertAction = {
                // verify first session
                val messages = getSessionEnvelopesFromMockServer(2)
                val first = messages[0]
                first.findSessionSpan().attributes?.assertMatches {
                    embSessionStartType.name to LifeEventType.STATE.name.lowercase(Locale.ENGLISH)
                    embSessionEndType.name to LifeEventType.STATE.name.lowercase(Locale.ENGLISH)
                    embErrorLogCount.name to 0
                }

                assertFalse(first.hasSpanSnapshotsOfType(EmbType.Ux.Session))

                // verify second session
                val second = messages[1]
                assertNotEquals(first.getSessionId(), second.getSessionId())
            }
        )
    }
}
