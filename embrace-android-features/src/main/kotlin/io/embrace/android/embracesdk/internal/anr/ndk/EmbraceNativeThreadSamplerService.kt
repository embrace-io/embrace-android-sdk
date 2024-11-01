package io.embrace.android.embracesdk.internal.anr.ndk

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.anr.mapThreadState
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Samples the target thread stacktrace when the thread is detected as blocked.
 *
 * The NDK layer must be enabled in order to use this functionality as this class
 * calls native code.
 */
class EmbraceNativeThreadSamplerService @JvmOverloads constructor(
    private val configService: ConfigService,
    private val symbols: Lazy<Map<String, String>?>,
    private val random: Random = Random(),
    private val delegate: NdkDelegate = NativeThreadSamplerNdkDelegate(),
    private val worker: BackgroundWorker,
    private val deviceArchitecture: DeviceArchitecture,
    private val sharedObjectLoader: SharedObjectLoader,
) : NativeThreadSamplerService {

    private companion object {
        const val MAX_NATIVE_SAMPLES = 10
    }

    interface NdkDelegate {
        fun setupNativeThreadSampler(is32Bit: Boolean): Boolean
        fun monitorCurrentThread(): Boolean
        fun startSampling(unwinderOrdinal: Int, intervalMs: Long)
        fun finishSampling(): List<NativeThreadAnrSample>?
    }

    var ignored: Boolean = true

    var sampling: Boolean = false

    var count: Int = -1

    var factor: Int = -1

    var intervals: MutableList<NativeThreadAnrInterval> = mutableListOf()

    val currentInterval: NativeThreadAnrInterval?
        get() = intervals.lastOrNull()

    private var targetThread: Thread = Thread.currentThread()

    override fun setupNativeSampler(): Boolean {
        return if (sharedObjectLoader.loadEmbraceNative()) {
            delegate.setupNativeThreadSampler(deviceArchitecture.is32BitDevice)
        } else {
            false
        }
    }

    override fun monitorCurrentThread(): Boolean {
        targetThread = Thread.currentThread()
        return delegate.monitorCurrentThread()
    }

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        // use consistent config for the duration of this ANR interval.
        val anrBehavior = configService.anrBehavior
        ignored = !containsAllowedStackframes(anrBehavior, targetThread.stackTrace)
        if (ignored || shouldSkipNewSample(anrBehavior)) {
            // we've reached the data capture limit - ignore any thread blocked intervals.
            ignored = true
            return
        }

        val unwinder = anrBehavior.getNativeThreadAnrSamplingUnwinder()
        factor = anrBehavior.getNativeThreadAnrSamplingFactor()
        val offset = random.nextInt(factor)
        count = (factor - offset) % factor

        intervals.add(
            NativeThreadAnrInterval(
                targetThread.id,
                targetThread.name,
                targetThread.priority,
                offset * anrBehavior.getSamplingIntervalMs(),
                timestamp,
                mutableListOf(),
                mapThreadState(targetThread.state),
                unwinder
            )
        )
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        val limit = configService.anrBehavior.getMaxStacktracesPerInterval()
        if (count >= limit) {
            return
        }

        if (ignored || !configService.anrBehavior.isUnityAnrCaptureEnabled()) {
            return
        }
        if (count % factor == 0) {
            count = 0

            if (!sampling) {
                sampling = true

                // start sampling the native thread
                val anrBehavior = configService.anrBehavior
                val unwinder = anrBehavior.getNativeThreadAnrSamplingUnwinder()
                val intervalMs = anrBehavior.getNativeThreadAnrSamplingIntervalMs()
                delegate.startSampling(
                    unwinder.code,
                    intervalMs
                )

                worker.schedule<Unit>(::fetchIntervals, intervalMs * MAX_NATIVE_SAMPLES, TimeUnit.MILLISECONDS)
            }
        }
        count++
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        if (sampling) {
            worker.submit {
                fetchIntervals()
            }
        }

        ignored = true
        sampling = false
    }

    private fun fetchIntervals() {
        currentInterval?.let { interval ->
            delegate.finishSampling()?.let { samples ->
                interval.samples?.run {
                    clear()
                    addAll(samples)
                }
            }
        }
    }

    override fun cleanCollections() {
        intervals = mutableListOf()
    }

    private fun shouldSkipNewSample(anrBehavior: AnrBehavior): Boolean {
        val sessionLimit = anrBehavior.getMaxAnrIntervalsPerSession()
        return !configService.anrBehavior.isUnityAnrCaptureEnabled() || intervals.size >= sessionLimit
    }

    override fun getNativeSymbols(): Map<String, String>? = symbols.value

    override fun getCapturedIntervals(receivedTermination: Boolean?): List<NativeThreadAnrInterval>? {
        if (!configService.anrBehavior.isUnityAnrCaptureEnabled()) {
            return null
        }

        // optimization: avoid trying to make a JNI call every 2s due to regular session caching!
        if (sampling && receivedTermination == false) {
            // fetch JNI samples (blocks main thread, but no way around it if we want
            // the information in the session)
            fetchIntervals()
        }

        // the ANR might end before samples with offsets are recorded - avoid
        // recording an empty sample in the payload if this is the case.
        val usefulSamples = intervals.toList().filter { it.samples?.isNotEmpty() ?: false }
        if (usefulSamples.isEmpty()) {
            return null
        }
        return usefulSamples.toList()
    }

    /**
     * Determines whether or not we should sample the target thread based on the thread stacktrace
     * and the ANR config.
     */

    fun containsAllowedStackframes(
        anrBehavior: AnrBehavior,
        stacktrace: Array<StackTraceElement>,
    ): Boolean {
        if (anrBehavior.isNativeThreadAnrSamplingAllowlistIgnored()) {
            return true
        }
        val allowlist = anrBehavior.getNativeThreadAnrSamplingAllowlist()
        return stacktrace.any { frame ->
            allowlist.any { allowed ->
                frame.methodName == allowed.method && frame.className == allowed.clz
            }
        }
    }
}

fun isUnityMainThread(): Boolean = "UnityMain" == Thread.currentThread().name
