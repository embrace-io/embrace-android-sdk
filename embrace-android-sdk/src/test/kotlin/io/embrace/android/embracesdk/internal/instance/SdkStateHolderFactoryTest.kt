package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.injection.BootstrapServices
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateHolderFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bootstrapServices = BootstrapServices(logger = FakeInternalLogger(throwOnInternalError = false))

    @Test
    fun `otel config set before start is applied to bootstrap services`() {
        val holder = createSdkStateHolder(
            bootstrapServices = bootstrapServices,
            moduleGraphSupplier = { context, bootstrapServices, buffer ->
                InitializedModuleGraph(
                    context = context,
                    bootstrapServices = bootstrapServices,
                    buffer = buffer,
                    sdkInitResourceUsageTrackerSupplier = { error("failed to init") },
                )
            },
        )
        holder.api.addSpanExporter(FakeSpanExporter())
        assertFalse(bootstrapServices.externalOtelConfig.hasConfiguredOtlpExport())
        holder.start(context)
        assertTrue(bootstrapServices.externalOtelConfig.hasConfiguredOtlpExport())
        assertEquals(SdkState.FAILED, holder.state)
    }
}
