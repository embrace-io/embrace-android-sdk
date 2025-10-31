package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.worker.Worker

class AeiInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        val activityManager = args.systemService<ActivityManager>(Context.ACTIVITY_SERVICE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activityManager == null) {
            return null
        }

        return DataSourceState(
            factory = {
                AeiDataSourceImpl(
                    // FIXME: supply via args
                    args = args,
                    backgroundWorker = args.backgroundWorker(worker = Worker.Background.NonIoRegWorker),
                    activityManager = activityManager,
                    aeiDataStore = AeiDataStoreImpl(args.store),
                )
            },
            configGate = { args.configService.appExitInfoBehavior.isAeiCaptureEnabled() }
        )
    }
}
