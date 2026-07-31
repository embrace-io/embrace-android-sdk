package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockage
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records every blockage reported to it. Each one is copied on arrival because the detector reuses a
 * single [ThreadBlockage] instance, so retaining the argument would leave every recorded blockage
 * describing whichever one was reported last.
 */
internal class FakeThreadBlockageListener : ThreadBlockageListener {

    val started: MutableList<ThreadBlockage> = CopyOnWriteArrayList()
    val ongoing: MutableList<ThreadBlockage> = CopyOnWriteArrayList()
    val ended: MutableList<ThreadBlockage> = CopyOnWriteArrayList()

    val intervalCount: Int get() = ongoing.size

    override fun onBlockageStart(blockage: ThreadBlockage) {
        started.add(blockage.copy())
    }

    override fun onBlockageOngoing(blockage: ThreadBlockage) {
        ongoing.add(blockage.copy())
    }

    override fun onBlockageEnd(blockage: ThreadBlockage) {
        ended.add(blockage.copy())
    }
}
