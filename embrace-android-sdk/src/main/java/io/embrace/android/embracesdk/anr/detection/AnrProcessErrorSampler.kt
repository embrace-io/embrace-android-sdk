package io.embrace.android.embracesdk.anr.detection

import android.app.ActivityManager
import android.os.Process
import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

/**
 * Checks whether the current process has been marked in an error state by the OS. The error
 * state gives useful debug information about the ANR and helps us narrow down whether the OS
 * detected a problem or not.
 *
 * https://developer.android.com/reference/android/app/ActivityManager#getProcessesInErrorState()
 *
 * This class can only check one anr process error at a time.
 */
internal class AnrProcessErrorSampler(
    private val activityManager: ActivityManager?,
    private val configService: ConfigService,
    private val anrExecutor: ScheduledExecutorService,
    private val clock: Clock,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger,
    private val pid: Int = Process.myPid()
) : BlockedThreadListener {

    private var intervalMs: Long = configService.anrBehavior.getAnrProcessErrorsIntervalMs()

    var scheduledFuture: ScheduledFuture<*>? = null

    var anrProcessErrors: NavigableMap<Long, AnrProcessErrorStateInfo> = ConcurrentSkipListMap()

    // timestamp when the thread has been unblocked

    var threadUnblockedMs: Long? = null

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        if (isFeatureEnabled()) {
            // just in case a scheduler is running, let's reset because this is a new anr
            // note that this class does not support to have a scheduler looking for 2 different anr's
            // at the same time
            reset()

            scheduleAnrProcessErrorsChecker(timestamp)
        }
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        if (isFeatureEnabled()) {
            threadUnblockedMs = timestamp
        }
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        // nothing to do here
    }

    /**
     * It gets all collected anr process errors for given range.
     *
     * @param startTime
     */
    fun getAnrProcessErrors(
        startTime: Long
    ): List<AnrProcessErrorStateInfo> {
        val bgAnrCaptureEnabled = configService.anrBehavior.isBgAnrCaptureEnabled()
        val filteredProcessErrors: Collection<AnrProcessErrorStateInfo> =
            if (!bgAnrCaptureEnabled) {
                // Filter out ANRs that started before session start
                anrProcessErrors.filter {
                    // check that timestamp of the anr process error is within session bounds
                    it.key >= startTime
                }.values
            } else {
                anrProcessErrors.values
            }

        // do not return original, safer to copy instead
        return filteredProcessErrors.toMutableList()
    }

    /**
     * Called when we need to search for an anr process error.
     *
     * @param threadBlockedTimestamp timestamp of when the thread has been blocked
     */

    internal fun onSearchForProcessErrors(threadBlockedTimestamp: Long) {
        val shouldStopScheduler = !isSchedulerAllowedToRun()
        if (shouldStopScheduler) {
            logger.logDeveloper(
                "EmbraceAnrService",
                "Anr process errors scheduler is not allowed to keep running. Stopping it"
            )
            // even if scheduler can not run anymore, let's not interrupt this run because we may find an anr
            scheduledFuture?.cancel(false)
        }

        val anrProcessErrorState = findAnrProcessErrorStateInfo(clock, activityManager, pid)
        if (anrProcessErrorState != null) {
            // anr process error found
            handleProcessErrorState(anrProcessErrorState, threadBlockedTimestamp)
        } else {
            logger.logDeveloper(
                "EmbraceAnrService",
                "Anr process errors were not found. This is expected, report has " +
                    "probably not been generated yet"
            )

            // only perform rescheduling if scheduler should not be stopped
            if (!shouldStopScheduler && intervalMs != configService.anrBehavior.getAnrProcessErrorsIntervalMs()) {
                logger.logDeveloper(
                    "EmbraceAnrService",
                    "Different capture anr process errors interval detected, restarting runnable"
                )

                // we don't want to interrupt this Runnable while it's running
                scheduledFuture?.cancel(false)

                // reschedule scheduler at the new cadence
                scheduleAnrProcessErrorsChecker(threadBlockedTimestamp)
            }
        }
    }

    /**
     * It determines if the scheduler is allowed to keep running.
     * Basically, once the thread has been unblocked, we still have [schedulerExtraTimeAllowance]
     * ms for the scheduler to keep running.
     */

    internal fun isSchedulerAllowedToRun(): Boolean {
        return when (val ms = threadUnblockedMs) {
            null -> true
            else -> (clock.now() - ms).absoluteValue <= configService.anrBehavior.getAnrProcessErrorsSchedulerExtraTimeAllowanceMs()
        }
    }

    private fun scheduleAnrProcessErrorsChecker(threadBlockedTimestamp: Long) {
        try {
            val runnable = Runnable {
                onSearchForProcessErrors(threadBlockedTimestamp)
            }

            // the OS does not generate anr process errors right away
            val delay = configService.anrBehavior.getAnrProcessErrorsDelayMs()
            logger.logDeveloper(
                "EmbraceAnrService",
                "About to schedule runnable to look for anr process errors, with " +
                    "delay=$delay - intervalMs=$intervalMs"
            )
            intervalMs = configService.anrBehavior.getAnrProcessErrorsIntervalMs()
            scheduledFuture = anrExecutor.scheduleAtFixedRate(
                runnable,
                delay,
                intervalMs,
                TimeUnit.MILLISECONDS
            )
        } catch (exc: Exception) {
            // ignore any RejectedExecution - ScheduledExecutorService only throws when shutting down.
            val message = "capture ANR process errors initialization failed"
            logger.logError(message, exc, true)
        }
    }

    private fun handleProcessErrorState(
        processErrorStateInfo: AnrProcessErrorStateInfo,
        timestamp: Long
    ) {
        logger.logDeveloper(
            "EmbraceAnrService",
            "Anr process error state found. " +
                "Cancelled scheduler so to stop looking for it."
        )

        anrProcessErrors[timestamp] = processErrorStateInfo

        logDebugInfo(processErrorStateInfo)

        // once anr process error is saved, let's cancel scheduler because we only need to fetch
        // this once per ANR
        scheduledFuture?.cancel(true)
    }

    private fun logDebugInfo(processErrorStateInfo: AnrProcessErrorStateInfo) {
        with(processErrorStateInfo) {
            logger.logDeveloper(
                "EmbraceAnrService",
                """AnrProcessErrorStateInfo=
                |tag=$tag
                |shortMsg=$shortMsg
                |longMsg=$longMsg
                |stacktrace=$stackTrace
                """.trimMargin()
            )
        }
    }

    private fun reset() {
        logger.logDeveloper(
            "EmbraceAnrService",
            "Resetting AnrProcessErrorSampler scheduler state"
        )
        threadUnblockedMs = null
        scheduledFuture?.cancel(true)
    }

    private fun isFeatureEnabled() =
        if (!configService.anrBehavior.isAnrProcessErrorsCaptureEnabled()) {
            logger.logDeveloper(
                "EmbraceAnrService",
                "ANR process errors capture is disabled"
            )
            false
        } else {
            true
        }
}
