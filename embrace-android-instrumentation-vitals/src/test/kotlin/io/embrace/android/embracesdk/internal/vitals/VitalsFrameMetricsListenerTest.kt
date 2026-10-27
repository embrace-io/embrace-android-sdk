package io.embrace.android.embracesdk.internal.vitals

import android.view.FrameMetrics
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class VitalsFrameMetricsListenerTest {

    private val throwingStrategy = object : FrameMetricsStrategy {
        override fun vsyncNanos(frameMetrics: FrameMetrics): Long = error("boom")
        override fun jankNanos(frameMetrics: FrameMetrics): Long = 0L
        override fun frameIntervalNanos(frameMetrics: FrameMetrics): Long = 0L
    }

    @Test
    fun `frame callbacks that throw repeatedly across windows track one error`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val errors = OnceUiCallbackErrorTracker(errorHandler)
        val listeners =
            List(2) { VitalsFrameMetricsListener(FakeFocalInteractionCallbacks(), throwingStrategy, errors) }
        repeat(5) {
            listeners.forEach { it.onFrameMetricsAvailable(mockk<Window>(), mockk<FrameMetrics>(), 0) }
        }
        assertEquals(listOf(SdkErrorType.UiCallbackFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
    }
}
