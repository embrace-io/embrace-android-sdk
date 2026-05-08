package io.embrace.android.exampleapp

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttpClient for the example app. Used by both [io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyApi]
 * (JSON requests) and Coil's [coil.ImageLoader] (image requests).
 *
 * The Embrace Gradle plugin instruments OkHttp calls at build time, so requests dispatched
 * through this client are visible in Embrace network telemetry without any runtime wiring.
 *
 * The shared dispatcher caps total + per-host concurrency at 2 — useful for perf testing
 * scroll behavior under constrained network parallelism.
 */
object AppHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 2
                    maxRequestsPerHost = 2
                },
            )
            .build()
    }
}
