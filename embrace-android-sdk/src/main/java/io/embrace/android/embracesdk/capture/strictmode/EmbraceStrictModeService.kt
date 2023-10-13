package io.embrace.android.embracesdk.capture.strictmode

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.ExceptionInfo
import io.embrace.android.embracesdk.payload.StrictModeViolation
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

@RequiresApi(Build.VERSION_CODES.P)
internal class EmbraceStrictModeService(
    private val configService: ConfigService,
    private val executorService: ExecutorService,
    private val clock: Clock
) : StrictModeService {

    private val violations = mutableListOf<StrictModeViolation>()

    override fun start() {
        addStrictModeListener(executorService, StrictMode.OnThreadViolationListener(::addViolation))
    }

    @VisibleForTesting
    internal fun addViolation(violation: Violation) {
        if (violations.size < configService.anrBehavior.getStrictModeViolationLimit()) {
            val exceptionInfo = ExceptionInfo.ofThrowable(violation)
            violations.add(StrictModeViolation(exceptionInfo, clock.now()))
        }
    }

    private fun addStrictModeListener(
        executor: Executor,
        listener: StrictMode.OnThreadViolationListener
    ) {
        // only detect I/O strict mode errors for now.
        val builder = StrictMode.ThreadPolicy.Builder().apply {
            detectDiskReads()
            detectDiskWrites()
            detectUnbufferedIo()
        }.penaltyListener(executor, listener)
        StrictMode.setThreadPolicy(builder.build())
    }

    override fun cleanCollections() {
        violations.clear()
    }

    override fun getCapturedData(): List<StrictModeViolation> = violations
}
