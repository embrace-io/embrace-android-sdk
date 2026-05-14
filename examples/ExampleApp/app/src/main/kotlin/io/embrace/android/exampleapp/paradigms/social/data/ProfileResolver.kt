package io.embrace.android.exampleapp.paradigms.social.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.embrace.android.exampleapp.di.AppScope
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyApi
import io.embrace.android.exampleapp.paradigms.data.Post
import io.embrace.android.exampleapp.paradigms.data.PostAuthor
import io.embrace.android.exampleapp.paradigms.data.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a profile by handle. Tries static [SampleData] first, then an in-memory cache of
 * previously-fetched Bluesky profiles, then `app.bsky.actor.getProfile` + `getAuthorFeed`.
 *
 * Cache lives only for the process lifetime — profiles re-fetch after app restart.
 */
@SingleIn(AppScope::class)
@Inject
class ProfileResolver(
    private val blueskyApi: BlueskyApi,
    private val sampleData: SampleData,
) {

    sealed interface Result {
        data object Loading : Result
        data class Loaded(val author: PostAuthor, val posts: List<Post>) : Result
        data class Error(val message: String) : Result
    }

    private val cache = ConcurrentHashMap<String, Result.Loaded>()

    /** Looks up a post by id across all profile feeds we've already resolved. */
    fun cachedPost(id: String): Post? =
        cache.values.firstNotNullOfOrNull { loaded -> loaded.posts.firstOrNull { it.id == id } }

    @Composable
    fun rememberProfileState(handle: String): State<Result> {
        val state: MutableState<Result> = remember(handle) { mutableStateOf(Result.Loading) }
        LaunchedEffect(handle) {
            state.value = resolve(handle)
        }
        return state
    }

    private suspend fun resolve(handle: String): Result {
        sampleData.author(handle)?.let { author ->
            val posts = sampleData.posts.filter { it.authorHandle == handle }
            return Result.Loaded(author, posts)
        }
        cache[handle]?.let { return it }
        return try {
            val (author, posts) = withContext(Dispatchers.IO) {
                coroutineScope {
                    val profileDeferred = async { blueskyApi.getProfile(handle) }
                    val feedDeferred = async { blueskyApi.getAuthorFeed(handle, limit = 30) }
                    val results = awaitAll(profileDeferred, feedDeferred)
                    @Suppress("UNCHECKED_CAST")
                    (results[0] as PostAuthor) to (results[1] as List<Post>)
                }
            }
            val loaded = Result.Loaded(author, posts)
            cache[handle] = loaded
            loaded
        } catch (e: Exception) {
            Result.Error(e.message?.takeIf { it.isNotBlank() } ?: "Profile fetch failed")
        }
    }
}
