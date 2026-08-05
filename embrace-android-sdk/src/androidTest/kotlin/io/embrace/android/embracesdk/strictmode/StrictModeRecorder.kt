package io.embrace.android.embracesdk.strictmode

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * Installs [StrictMode] policies and records every violation they report.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class StrictModeRecorder {

    private val recorded = CopyOnWriteArrayList<RecordedViolation>()
    private val directExecutor = Executor(Runnable::run)

    /**
     * [StrictMode.ThreadPolicy] is thread local, so call this on the thread whose I/O should be observed.
     */
    fun install() {
        StrictMode.setThreadPolicy(threadPolicy())
        StrictMode.setVmPolicy(vmPolicy())
    }

    fun violations(): List<RecordedViolation> = recorded.toList()

    private fun threadPolicy(): StrictMode.ThreadPolicy = StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .detectNetwork()
        .detectUnbufferedIo()
        .detectCustomSlowCalls()
        .detectResourceMismatches()
        .penaltyListener(directExecutor) { record(it) }
        .build()

    private fun vmPolicy(): StrictMode.VmPolicy = StrictMode.VmPolicy.Builder()
        .detectUntaggedSockets()
        .detectNonSdkApiUsage()
        .detectLeakedSqlLiteObjects()
        .penaltyListener(directExecutor) { record(it) }
        .build()

    private fun record(violation: Violation) {
        recorded.add(RecordedViolation(violation, Thread.currentThread().name))
    }
}

