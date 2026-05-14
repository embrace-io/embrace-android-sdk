package io.embrace.android.exampleapp.di

import android.content.Context
import coil.ImageLoader
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyApi
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyFeedStore
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.ecommerce.EcommerceCartStore
import io.embrace.android.exampleapp.paradigms.social.data.ProfileResolver
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application-scoped Metro graph. Built once in [io.embrace.android.exampleapp.MainApplication]
 * and exposed via the `graph` property; Compose code reaches it via [appGraph].
 *
 * `@Provides` here covers types we don't own (third-party libraries). Types we own are
 * `@Inject`-constructed and surface as accessors below.
 */
@DependencyGraph(scope = AppScope::class)
interface AppGraph {

    val sampleData: SampleData
    val blueskyApi: BlueskyApi
    val blueskyFeedStore: BlueskyFeedStore
    val profileResolver: ProfileResolver
    val cartStore: EcommerceCartStore
    val imageLoader: ImageLoader

    /**
     * Shared OkHttpClient. Embrace's Gradle plugin instruments OkHttp call sites at build time
     * regardless of where the client is constructed, so requests through this client land in
     * Embrace network telemetry. Dispatcher caps total + per-host concurrency at 2 — useful
     * for perf testing scroll behavior under constrained network parallelism.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideOkHttpClient(): OkHttpClient =
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

    /** Single Json instance configured for our polymorphic sample data + lenient inputs. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
        explicitNulls = false
    }

    /** Coil ImageLoader wired to the shared OkHttpClient so image fetches share the dispatcher. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideImageLoader(context: Context, client: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context).okHttpClient { client }.build()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AppGraph
    }
}
