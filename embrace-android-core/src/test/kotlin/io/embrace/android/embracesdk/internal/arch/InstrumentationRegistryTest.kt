package io.embrace.android.embracesdk.internal.arch

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class InstrumentationRegistryTest {

    private lateinit var registry: InstrumentationRegistry
    private lateinit var dataSource: FakeDataSource
    private lateinit var configService: FakeConfigService
    private lateinit var executorService: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        dataSource = FakeDataSource(RuntimeEnvironment.getApplication())
        configService = FakeConfigService()
        executorService = BlockingScheduledExecutorService(blockingMode = false)
        registry = InstrumentationRegistryImpl(
            InternalLoggerImpl(),
        )
    }

    @Test
    fun `test instrumentation provider load`() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        val initOrder = mutableListOf<Int>()
        val providers = listOf<InstrumentationProvider>(
            FakeInstrumentationProvider(1000, initOrder::add),
            FakeInstrumentationProvider(10, initOrder::add),
            FakeInstrumentationProvider(100, initOrder::add),
        )
        registry.loadInstrumentations(providers, args)
        assertEquals(listOf(10, 100, 1000), initOrder)
    }

    @Test
    fun `verify session lifecycle listeners`() {
        val provider =
            FakeInstrumentationProvider(
                priority = 1000,
                action = {},
                dataSourceState = DataSourceState(
                    factory = { dataSource },
                ),
            )

        assertEquals(0, dataSource.sessionEnds)
        assertEquals(0, dataSource.sessionChanges)

        registry.loadInstrumentations(
            instrumentationProviders = listOf(provider),
            args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext()),
        )
        registry.onPreSessionEnd()
        registry.onPostSessionChange()
        assertEquals(1, dataSource.sessionEnds)
        assertEquals(1, dataSource.sessionChanges)
    }

    @Test
    fun `datasource iteration is threadsafe`() {
        val iterationStarted = CountDownLatch(1)
        val continueIteration = CountDownLatch(1)
        val newDataSource = FakeDataSource(RuntimeEnvironment.getApplication())

        registry.add(
            DataSourceState(
                factory = {
                    BlockingTestDataSource(iterationStarted, continueIteration)
                },
            ),
        )

        val executor = Executors.newSingleThreadExecutor()
        try {
            val iterationDone = executor.submit {
                registry.onPostSessionChange()
            }
            assertTrue(iterationStarted.await(1, TimeUnit.SECONDS))
            registry.add(DataSourceState({ newDataSource }))
            continueIteration.countDown()
            iterationDone.get(1, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertEquals("Datasource added during registry iteration should not be touched", 0, newDataSource.sessionChanges)
        registry.onPostSessionChange()
        assertEquals(1, newDataSource.sessionChanges)
    }

    private class BlockingTestDataSource(
        private val iterationStarted: CountDownLatch,
        private val mayContinue: CountDownLatch,
    ) : DataSource, SessionPartChangeListener {
        override val instrumentationName: String = "blocking_test_data_source"

        override fun onDataCaptureEnabled() {}
        override fun onDataCaptureDisabled() {}
        override fun resetDataCaptureLimits() {}
        override fun <T> captureTelemetry(
            inputValidation: () -> Boolean,
            invalidInputCallback: () -> Unit,
            action: TelemetryDestination.() -> T?,
        ): T? = null

        override fun onPostSessionChange() {
            iterationStarted.countDown()
            mayContinue.await(1, TimeUnit.SECONDS)
        }
    }
}
