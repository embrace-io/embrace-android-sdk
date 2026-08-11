package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ServiceLoader

/**
 * Pins which instrumentation registers on a background worker rather than on the main thread during
 * SDK startup. Registering asynchronously means the data source does not exist for a short window
 * after the SDK starts, so opting in or out is a deliberate decision rather than a default - this
 * fails if a provider changes its mind without the set below being updated.
 */
internal class InstrumentationProviderAsyncInitTest {

    @Test
    fun `expected instrumentation registers asynchronously`() {
        val asyncProviders = ServiceLoader.load(
            InstrumentationProvider::class.java,
            InstrumentationProvider::class.java.classLoader,
        ).filter(InstrumentationProvider::asyncInit)
            .mapTo(sortedSetOf()) { checkNotNull(it::class.simpleName) }

        assertEquals(
            sortedSetOf(
                "AeiInstrumentationProvider",
                "PowerSaveInstrumentationProvider",
                "PowerStateInstrumentationProvider",
                "PushNotificationInstrumentationProvider",
                "TapInstrumentationProvider",
                "ThermalStateInstrumentationProvider",
                "WebviewInstrumentationProvider",
            ),
            asyncProviders,
        )
    }
}
