package io.embrace.android.embracesdk.internal

import android.os.FileObserver
import java.util.concurrent.CountDownLatch

@Suppress("DEPRECATION")
public class EmbraceFileObserver(
    path: String,
    mask: Int,
) : FileObserver(path, mask) {

    private lateinit var startSignal: CountDownLatch

    public fun startWatching(startSignal: CountDownLatch) {
        this.startSignal = startSignal
        startWatching()
    }

    override fun onEvent(p0: Int, p1: String?) {
        startSignal.countDown()
    }
}
