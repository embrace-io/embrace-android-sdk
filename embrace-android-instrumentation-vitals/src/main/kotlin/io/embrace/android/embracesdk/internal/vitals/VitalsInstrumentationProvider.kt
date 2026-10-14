package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

var vitalsVersionCheckerTestOverride: VersionChecker? = null

/**
 * SPI entry point for the mobile vitals instrumentation. Registers nothing below API 24.
 */
class VitalsInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        val versionChecker = vitalsVersionCheckerTestOverride ?: BuildVersionChecker
        if (!versionChecker.isAtLeast(Build.VERSION_CODES.N)) {
            return null
        }
        return {
            if (args.configService.config.autoDataCapture.smoothnessCaptureEnabled) {
                VitalsDataSource(args, versionChecker)
            } else {
                null
            }
        }
    }
}
