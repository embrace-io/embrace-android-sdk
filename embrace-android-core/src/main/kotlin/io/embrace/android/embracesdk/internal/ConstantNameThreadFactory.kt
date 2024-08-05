package io.embrace.android.embracesdk.internal

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * [ThreadFactory] that creates thread with a constant name. Useful if you want to ensure the same executor produces threads with the
 * same name. Use the uniquePerInstance parameter to use the instance's hashcode to provide relative uniqueness across instances
 * of this thread factory.
 */
public class ConstantNameThreadFactory(
    namePrefix: String = "thread",
    uniquePerInstance: Boolean = false
) : ThreadFactory {
    private val defaultFactory: ThreadFactory = Executors.defaultThreadFactory()
    private val threadName = "emb-$namePrefix${if (uniquePerInstance) "-${hashCode()}" else ""}"

    override fun newThread(r: Runnable?): Thread = defaultFactory.newThread(r).apply { name = threadName }
}
