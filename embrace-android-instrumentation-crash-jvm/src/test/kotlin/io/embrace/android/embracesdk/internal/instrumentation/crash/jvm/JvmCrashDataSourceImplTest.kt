package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(IncubatingApi::class)
@RunWith(AndroidJUnit4::class)
internal class JvmCrashDataSourceImplTest {

    private lateinit var crashDataSource: JvmCrashDataSourceImpl
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var logger: EmbLogger
    private lateinit var testException: Exception
    private lateinit var ctx: Application
    private var modifier: ((TelemetryAttributes) -> SchemaType)? = null

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(ctx)
        logger = FakeEmbLogger()
        testException = RuntimeException("Test exception")
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    private fun setupForHandleCrash(crashHandlerEnabled: Boolean = false) {
        args = FakeInstrumentationArgs(
            ctx,
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    uncaughtExceptionHandlerEnabled = crashHandlerEnabled
                )
            )
        )
        crashDataSource = JvmCrashDataSourceImpl(args).apply {
            telemetryModifier = modifier
        }
    }

    @Test
    fun `test crash handler order`() {
        setupForHandleCrash(true)
        val observedOrder = mutableListOf<Int>()
        crashDataSource.addCrashTeardownHandler { observedOrder.add(1) }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(2) }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(3) }
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(listOf(1, 2, 3), observedOrder)
    }

    @Test
    fun `test exception handler sends telemetry with config option enabled`() {
        setupForHandleCrash(true)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
        crashDataSource.logUnhandledJvmThrowable(IllegalStateException())
        assertEquals(1, args.destination.logEvents.size)
    }

    @Test
    fun `default crash handler delegate will not be set if it is already an embrace handler`() {
        val embraceDefaultHandler = EmbraceUncaughtExceptionHandler(
            defaultHandler = null,
            dataSource = FakeJvmCrashDataSource(),
            logger = FakeEmbLogger()
        )
        Thread.setDefaultUncaughtExceptionHandler(embraceDefaultHandler)
        setupForHandleCrash(true)
        assertSame(Thread.getDefaultUncaughtExceptionHandler(), embraceDefaultHandler)
    }

    @Test
    fun `test exception handler does not send telemetry with config option disabled`() {
        setupForHandleCrash(false)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
        assertEquals(0, args.destination.logEvents.size)
    }
}
