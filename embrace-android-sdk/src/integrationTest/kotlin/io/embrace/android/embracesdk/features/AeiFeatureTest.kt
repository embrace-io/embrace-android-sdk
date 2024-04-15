package io.embrace.android.embracesdk.features

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
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
            testRule.embrace.start(ApplicationProvider.getApplicationContext())
            val message = harness.recordSession()
            val deliveryService = harness.overriddenDeliveryModule.deliveryService
            val blobRecord = checkNotNull(deliveryService.blobMessages.single().applicationExits.single())
            val sessionRecord = checkNotNull(message?.performanceInfo?.appExitInfoData?.single())
            assertEquals(blobRecord.copy(trace = null), sessionRecord)

            // assert AEI fields populated
            assertEquals(blobRecord.timestamp, 15000000000L)
            assertEquals(blobRecord.sessionId, "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d")
            assertEquals(blobRecord.importance, 125)
            assertEquals(blobRecord.pss, 1509123409L)
            assertEquals(blobRecord.reason, 4)
            assertEquals(blobRecord.rss, 1123409L)
            assertEquals(blobRecord.status, 1)
            assertEquals(blobRecord.description, "testDescription")
            assertEquals(blobRecord.trace, "testInputStream")
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