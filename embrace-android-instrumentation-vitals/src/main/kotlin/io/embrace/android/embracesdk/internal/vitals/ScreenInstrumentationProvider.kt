package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Starts and stops screens for Activities, and records every screen start and stop on the `emb-screen` span. Registered on
 * every API level and with no config gate, independently of [VitalsInstrumentationProvider].
 */
class ScreenInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource> = {
        ScreenDataSource(args)
    }
}

internal class ScreenDataSource(
    private val args: InstrumentationArgs,
) : DataSourceImpl(args, NoopLimitStrategy, "screen_data_source") {

    private val activitySource = ActivityScreenSource(
        clock = args.clock,
        eventBus = args.eventBus,
        errorHandler = args.errorHandler,
        versionChecker = args.versionChecker,
    )

    private val recorder = ScreenSpanRecorder(
        destination = args.destination,
        clock = args.clock,
        errorHandler = args.errorHandler,
        sessionPartId = args::sessionPartId,
    )

    private val onStart = EventHandler<ScreenStart>(recorder::onStart)
    private val onStop = EventHandler<ScreenStop>(recorder::onStop)

    override fun onDataCaptureEnabled() {
        args.eventBus.addHandler(ScreenStart.KEY, onStart)
        // after every other handler, so that every summary written for the screen is recorded
        args.eventBus.addHandler(ScreenStop.KEY, Int.MAX_VALUE, onStop)
        args.registerSessionPartEndListener(recorder)
        args.registerSessionPartChangeListener(recorder)
        recorder.start()
        activitySource.subscribe()
    }

    override fun onDataCaptureDisabled() {
        activitySource.close()
        args.eventBus.removeHandler(ScreenStart.KEY, onStart)
        args.eventBus.removeHandler(ScreenStop.KEY, onStop)
        recorder.stop()
    }
}
