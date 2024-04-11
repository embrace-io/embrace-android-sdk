package io.embrace.android.embracesdk.features

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.findLogAttribute
import io.embrace.android.embracesdk.recordSession
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class AeiFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(harnessSupplier = {
        IntegrationTestRule.Harness(startImmediately = false)
    })

    @Test
    fun `application exit info feature`() {
        setupFakeAeiData()

        with(testRule) {
            testRule.startSdk(context = ApplicationProvider.getApplicationContext())
            harness.recordSession()

            val logOrchestrator = bootstrapper.customerLogModule.logOrchestrator
            val deliveryService = harness.overriddenDeliveryModule.deliveryService
            logOrchestrator.flush()
            val payload = deliveryService.lastSentLogPayloads.single()
            val log = checkNotNull(payload.data.logs?.single())

            // assert AEI fields populated
            assertEquals(log.findLogAttribute("timestamp").toLong(), 15000000000L)
            assertEquals(log.findLogAttribute("session_id"), "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d")
            assertEquals(log.findLogAttribute("process_importance").toInt(), 125)
            assertEquals(log.findLogAttribute("pss").toLong(), 1509123409L)
            assertEquals(log.findLogAttribute("rss").toLong(), 1123409L)
            assertEquals(log.findLogAttribute("exit_status").toInt(), 1)
            assertEquals(log.findLogAttribute("description"), "testDescription")
            assertEquals(log.findLogAttribute("reason").toInt(), 4)
            assertEquals("testInputStream", log.body)
            assertEquals("sys.exit", log.findLogAttribute("emb.type"))
        }
    }

    private fun setupFakeAeiData() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val shadowActivityManager: ShadowActivityManager = Shadows.shadowOf(am)

        shadowActivityManager.addApplicationExitInfo(mockk<ApplicationExitInfo>(relaxed = true) {
            every { timestamp } returns 15000000000L
            every { pid } returns 6952
            every { processStateSummary } returns "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d".toByteArray()
            every { importance } returns 125
            every { pss } returns 1509123409L
            every { reason } returns 4
            every { rss } returns 1123409L
            every { status } returns 1
            every { description } returns "testDescription"
            every { traceInputStream } returns "testInputStream".byteInputStream()
        })
    }
}