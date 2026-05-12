package io.embrace.android.exampleapp.paradigms.bluesky.data

import android.content.Context
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.embrace.android.exampleapp.di.AppScope
import io.embrace.android.exampleapp.paradigms.data.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Holds per-feed [Post]s fetched from Bluesky. Each [BlueskyPinnedFeed] is persisted to its own
 * `cacheDir/social/feed_<slug>.json` and tracks its own cursor / fetching / error state. Fetching
 * one tab does not affect the others.
 *
 * Owns its own coroutine scope so fetches triggered from the UI complete (and write to disk) even
 * if the user navigates away mid-request.
 */
@SingleIn(AppScope::class)
@Inject
class BlueskyFeedStore(
    private val context: Context,
    private val blueskyApi: BlueskyApi,
    private val json: Json,
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Guards all network fetches across every feed — only one load runs at a time. */
    private val globalFetchMutex = Mutex()
    private val anyFetchingFlow = MutableStateFlow(false)
    val anyFetching: StateFlow<Boolean> = anyFetchingFlow.asStateFlow()

    private class FeedState(val feed: BlueskyPinnedFeed, cacheRoot: File) {
        val mutex = Mutex()
        val posts = MutableStateFlow<List<Post>>(emptyList())
        val fetching = MutableStateFlow(false)
        val error = MutableStateFlow<String?>(null)
        val file: File = File(cacheRoot, "feed_${feed.slug}.json")

        @Volatile var cursor: String? = null
        @Volatile var diskLoaded: Boolean = false
    }

    private val cacheRoot: File by lazy {
        File(context.applicationContext.cacheDir, "social")
    }

    private val states: Map<BlueskyPinnedFeed, FeedState> = BlueskyPinnedFeed.entries
        .associateWith { FeedState(it, cacheRoot) }

    fun posts(feed: BlueskyPinnedFeed): StateFlow<List<Post>> = state(feed).posts.asStateFlow()
    fun isFetching(feed: BlueskyPinnedFeed): StateFlow<Boolean> = state(feed).fetching.asStateFlow()
    fun error(feed: BlueskyPinnedFeed): StateFlow<String?> = state(feed).error.asStateFlow()

    /** Look up a cached post by id across every feed; used for cross-tab navigation. */
    fun findPostById(postId: String): Post? =
        states.values.firstNotNullOfOrNull { st -> st.posts.value.firstOrNull { it.id == postId } }

    private fun state(feed: BlueskyPinnedFeed): FeedState =
        states.getValue(feed)

    /**
     * Eagerly load all cached feed files from disk. Safe to call multiple times — each feed only
     * loads on the first call. Typically invoked from `Application.onCreate`.
     */
    fun loadFromDisk() {
        states.values.forEach { st ->
            if (st.diskLoaded) return@forEach
            st.diskLoaded = true
            scope.launch {
                st.posts.value = readFromDisk(st.file)
            }
        }
    }

    /**
     * Trigger a network fetch for [feed]. No-op if any feed is currently fetching: only one load
     * runs at a time, regardless of which tab triggered it.
     */
    fun fetch(feed: BlueskyPinnedFeed, limit: Int = 30) {
        val st = state(feed)
        scope.launch {
            if (!globalFetchMutex.tryLock()) return@launch
            anyFetchingFlow.value = true
            try {
                st.mutex.withLock { fetchInternal(st, limit) }
            } finally {
                anyFetchingFlow.value = false
                globalFetchMutex.unlock()
            }
        }
    }

    /** Wipe in-memory state and delete the cache file for [feed]. */
    fun clear(feed: BlueskyPinnedFeed) {
        val st = state(feed)
        scope.launch {
            st.mutex.withLock {
                st.posts.value = emptyList()
                st.error.value = null
                st.cursor = null
                withContext(Dispatchers.IO) {
                    st.file.takeIf { it.exists() }?.delete()
                }
            }
        }
    }

    private suspend fun fetchInternal(st: FeedState, limit: Int) {
        st.fetching.value = true
        st.error.value = null
        try {
            val result = withContext(Dispatchers.IO) {
                blueskyApi.fetchFeed(feedUri = st.feed.feedUri, limit = limit, cursor = st.cursor)
            }
            st.cursor = result.nextCursor
            val existing = st.posts.value
            // Cursor walks newest → oldest, so append the new (older) batch to the end.
            val deduped = result.posts.filter { fresh -> existing.none { it.id == fresh.id } }
            val merged = existing + deduped
            st.posts.value = merged
            withContext(Dispatchers.IO) { writeToDisk(st.file, merged) }
        } catch (e: Exception) {
            st.error.value = e.message?.takeIf { it.isNotBlank() } ?: "Fetch failed"
        } finally {
            st.fetching.value = false
        }
    }

    private fun readFromDisk(file: File): List<Post> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            val text = file.readText()
            json.decodeFromString(ListSerializer(Post.serializer()), text)
        } catch (e: Exception) {
            // Malformed file (e.g., truncated by OS cache eviction). Drop it and start fresh.
            runCatching { file.delete() }
            emptyList()
        }
    }

    private fun writeToDisk(file: File, posts: List<Post>) {
        file.parentFile?.mkdirs()
        val text = json.encodeToString(ListSerializer(Post.serializer()), posts)
        file.writeText(text)
    }
}
