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
 * Holds a list of [Post]s fetched from Bluesky. Persists to `cacheDir/social/dynamic_posts.json`
 * using the same JSON shape as the bundled `assets/sample/posts.json`. Survives process death;
 * the OS may evict the cache directory under disk pressure (acceptable).
 *
 * Owns its own coroutine scope so a `fetch()` triggered from the UI completes (and writes to
 * disk) even if the user navigates away mid-request.
 */
@SingleIn(AppScope::class)
@Inject
class BlueskyFeedStore(
    private val context: Context,
    private val blueskyApi: BlueskyApi,
    private val json: Json,
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val backing = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = backing.asStateFlow()

    private val fetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = fetching.asStateFlow()

    private val errorState = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errorState.asStateFlow()

    private val storeFile: File by lazy {
        File(File(context.applicationContext.cacheDir, "social"), "dynamic_posts.json")
    }

    @Volatile
    private var cursor: String? = null

    @Volatile
    private var diskLoaded: Boolean = false

    /**
     * Eagerly load the cached file from disk. Safe to call multiple times — only the first
     * call performs work. Typically invoked from `Application.onCreate` so the timeline opens
     * with cached posts already in memory.
     */
    fun loadFromDisk() {
        if (diskLoaded) return
        diskLoaded = true
        scope.launch {
            val loaded = readFromDisk()
            backing.value = loaded
        }
    }

    /** Trigger a network fetch. Idempotent: a no-op if already fetching. */
    fun fetch(limit: Int = 30) {
        if (fetching.value) return
        scope.launch {
            mutex.withLock { fetchInternal(limit) }
        }
    }

    /** Wipe in-memory state and delete the cache file. */
    fun clear() {
        scope.launch {
            mutex.withLock {
                backing.value = emptyList()
                errorState.value = null
                cursor = null
                withContext(Dispatchers.IO) {
                    storeFile.takeIf { it.exists() }?.delete()
                }
            }
        }
    }

    private suspend fun fetchInternal(limit: Int) {
        fetching.value = true
        errorState.value = null
        try {
            val result = withContext(Dispatchers.IO) {
                blueskyApi.fetchFeed(limit = limit, cursor = cursor)
            }
            cursor = result.nextCursor
            val existing = backing.value
            val deduped = result.posts.filter { fresh -> existing.none { it.id == fresh.id } }
            val merged = deduped + existing
            backing.value = merged
            withContext(Dispatchers.IO) { writeToDisk(merged) }
        } catch (e: Exception) {
            errorState.value = e.message?.takeIf { it.isNotBlank() } ?: "Fetch failed"
        } finally {
            fetching.value = false
        }
    }

    private fun readFromDisk(): List<Post> {
        if (!storeFile.exists() || storeFile.length() == 0L) return emptyList()
        return try {
            val text = storeFile.readText()
            json.decodeFromString(ListSerializer(Post.serializer()), text)
        } catch (e: Exception) {
            // Malformed file (e.g., truncated by OS cache eviction). Drop it and start fresh.
            runCatching { storeFile.delete() }
            emptyList()
        }
    }

    private fun writeToDisk(posts: List<Post>) {
        storeFile.parentFile?.mkdirs()
        val text = json.encodeToString(ListSerializer(Post.serializer()), posts)
        storeFile.writeText(text)
    }
}
