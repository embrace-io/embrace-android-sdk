package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateHolderFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preStartServices = PreStartServices(logger = FakeInternalLogger(throwOnInternalError = false))

    @Test
    fun `otel config set before start is applied to pre-start services`() {
        val holder = createSdkStateHolder(
            preStartServices = preStartServices,
            sdkInitResourceUsageTrackerSupplier = { error("failed to init") },
        )
        holder.api.addSpanExporter(FakeSpanExporter())
        assertFalse(preStartServices.externalOtelConfig.hasConfiguredOtlpExport())
        holder.start(context)
        assertTrue(preStartServices.externalOtelConfig.hasConfiguredOtlpExport())
        assertEquals(SdkState.FAILED, holder.state)
    }
}
