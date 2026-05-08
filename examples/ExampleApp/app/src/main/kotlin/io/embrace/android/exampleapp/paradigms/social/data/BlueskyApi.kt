package io.embrace.android.exampleapp.paradigms.social.data

import io.embrace.android.exampleapp.paradigms.data.ImageSource
import io.embrace.android.exampleapp.paradigms.data.MediaRef
import io.embrace.android.exampleapp.paradigms.data.Post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Minimal client for Bluesky's public AppView. No auth required.
 *
 * Calls `app.bsky.feed.getFeed` against the public "What's Hot" feed and maps each post into our
 * local [Post] model. Image embeds become [MediaRef.Image] with [ImageSource.Remote] (Coil
 * downloads + caches them on render); video embeds are dropped for simplicity.
 *
 * The CDN that fronts `public.api.bsky.app` 403s `searchPosts` for unauthenticated callers, but
 * `getFeed` against a public feed generator is allowed. The caller passes the previous response's
 * `cursor` so successive fetches paginate deeper into the feed rather than refetching the same
 * top-of-list.
 */
internal object BlueskyApi {

    private const val BASE_URL = "https://public.api.bsky.app/xrpc/app.bsky.feed.getFeed"

    /** "What's Hot" feed generator — public, accessible without auth. */
    private const val FEED_URI =
        "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    data class FetchResult(val posts: List<Post>, val nextCursor: String?)

    @Throws(IOException::class)
    fun fetchFeed(limit: Int = 10, cursor: String? = null): FetchResult {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("feed", FEED_URI)
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .apply { if (!cursor.isNullOrEmpty()) addQueryParameter("cursor", cursor) }
            .build()
        val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
        response.use { res ->
            if (!res.isSuccessful) {
                throw IOException("Bluesky responded HTTP ${res.code}")
            }
            val body = res.body.string()
            val root = json.parseToJsonElement(body).jsonObject
            val feedArray = root["feed"]?.jsonArray ?: return FetchResult(emptyList(), null)
            val nextCursor = root["cursor"]?.stringValue()
            val posts = feedArray.mapNotNull { item ->
                val postObj = item.jsonObject["post"]?.jsonObject ?: return@mapNotNull null
                mapPost(postObj)
            }
            return FetchResult(posts, nextCursor)
        }
    }

    private fun mapPost(obj: JsonObject): Post? {
        val cid = obj["cid"]?.stringValue() ?: return null
        val author = obj["author"]?.jsonObject ?: return null
        val handle = author["handle"]?.stringValue() ?: return null
        val displayName = author["displayName"]?.stringValue() ?: handle
        val record = obj["record"]?.jsonObject ?: return null
        val text = record["text"]?.stringValue().orEmpty()
        val likeCount = obj["likeCount"]?.jsonPrimitive?.intOrNull ?: 0
        val replyCount = obj["replyCount"]?.jsonPrimitive?.intOrNull ?: 0
        val repostCount = obj["repostCount"]?.jsonPrimitive?.intOrNull ?: 0
        val indexedAt = obj["indexedAt"]?.stringValue().orEmpty()
        val imageUrls = extractImageUrls(obj["embed"]?.jsonObject)
        val media: List<MediaRef> = imageUrls.mapIndexed { idx, url ->
            MediaRef.Image(
                id = "bsky_${cid}_$idx",
                source = ImageSource.Remote(url = url, aspectRatio = 4f / 3f),
            )
        }
        return Post(
            id = "bsky_$cid",
            authorHandle = handle,
            authorDisplayName = displayName,
            body = text,
            likeCount = likeCount,
            replyCount = replyCount,
            repostCount = repostCount,
            media = media,
            timestampLabel = relativeTimeLabel(indexedAt),
            isVerified = false,
        )
    }

    private fun extractImageUrls(embed: JsonObject?): List<String> {
        if (embed == null) return emptyList()
        val type = embed["\$type"]?.stringValue()
        return when (type) {
            "app.bsky.embed.images#view" -> readFullsizeArray(embed["images"]?.jsonArray)
            "app.bsky.embed.recordWithMedia#view" -> {
                val media = embed["media"]?.jsonObject ?: return emptyList()
                if (media["\$type"]?.stringValue() == "app.bsky.embed.images#view") {
                    readFullsizeArray(media["images"]?.jsonArray)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun readFullsizeArray(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        return array.mapNotNull { el -> el.jsonObject["fullsize"]?.stringValue() }
    }

    private fun relativeTimeLabel(iso: String): String {
        if (iso.isEmpty()) return ""
        return try {
            val ts = Instant.parse(iso).toEpochMilli()
            val diffMin = (System.currentTimeMillis() - ts) / 60_000
            when {
                diffMin < 1 -> "now"
                diffMin < 60 -> "${diffMin}m"
                diffMin < 1440 -> "${diffMin / 60}h"
                else -> "${diffMin / 1440}d"
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun JsonElement.stringValue(): String? {
        val prim = this as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return if (prim.isString) prim.content else null
    }
}
