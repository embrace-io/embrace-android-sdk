package io.embrace.android.exampleapp.paradigms.bluesky.data

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.embrace.android.exampleapp.di.AppScope
import io.embrace.android.exampleapp.paradigms.data.ImageSource
import io.embrace.android.exampleapp.paradigms.data.MediaRef
import io.embrace.android.exampleapp.paradigms.data.Post
import io.embrace.android.exampleapp.paradigms.data.PostAuthor
import io.embrace.android.exampleapp.paradigms.data.VideoSource
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

/**
 * Minimal client for Bluesky's public AppView. No auth required.
 *
 * Calls `app.bsky.feed.getFeed` against the public "What's Hot" feed and maps each post into our
 * local [Post] model. Image embeds become [MediaRef.Image] with [ImageSource.Remote] (Coil
 * downloads + caches them on render); video embeds are dropped for simplicity.
 */
@SingleIn(AppScope::class)
@Inject
class BlueskyApi(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {

    data class FetchResult(val posts: List<Post>, val nextCursor: String?)

    @Throws(IOException::class)
    fun fetchFeed(
        feedUri: String,
        limit: Int = 10,
        cursor: String? = null,
    ): FetchResult {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("feed", feedUri)
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .apply { if (!cursor.isNullOrEmpty()) addQueryParameter("cursor", cursor) }
            .build()
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw IOException("Bluesky responded HTTP ${res.code}")
            }
            val root = json.parseToJsonElement(res.body.string()).jsonObject
            val feedArray = root["feed"]?.jsonArray ?: return FetchResult(emptyList(), null)
            val nextCursor = root["cursor"]?.stringValue()
            val posts = feedArray.mapNotNull { item ->
                val postObj = item.jsonObject["post"]?.jsonObject ?: return@mapNotNull null
                mapPost(postObj)
            }
            return FetchResult(posts, nextCursor)
        }
    }

    /** Public profile lookup by handle or DID. */
    @Throws(IOException::class)
    fun getProfile(actor: String): PostAuthor {
        val url = PROFILE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("actor", actor)
            .build()
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw IOException("Bluesky profile responded HTTP ${res.code}")
            }
            val obj = json.parseToJsonElement(res.body.string()).jsonObject
            val handle = obj["handle"]?.stringValue() ?: actor
            val displayName = obj["displayName"]?.stringValue() ?: handle
            val description = obj["description"]?.stringValue().orEmpty()
            val followersCount = obj["followersCount"]?.jsonPrimitive?.intOrNull ?: 0
            val followsCount = obj["followsCount"]?.jsonPrimitive?.intOrNull ?: 0
            val avatar = obj["avatar"]?.stringValue()?.let {
                ImageSource.Remote(url = it, aspectRatio = 1f)
            }
            val banner = obj["banner"]?.stringValue()?.let {
                ImageSource.Remote(url = it, aspectRatio = 3f)
            }
            val createdAt = obj["createdAt"]?.stringValue().orEmpty()
            return PostAuthor(
                handle = handle,
                displayName = displayName,
                bio = description,
                followerCount = followersCount,
                followingCount = followsCount,
                avatar = avatar,
                coverImage = banner,
                location = "",
                joinedLabel = formatJoinedLabel(createdAt),
                isVerified = false,
            )
        }
    }

    /** Public author-feed lookup by handle or DID. */
    @Throws(IOException::class)
    fun getAuthorFeed(actor: String, limit: Int = 30): List<Post> {
        val url = AUTHOR_FEED_URL.toHttpUrl().newBuilder()
            .addQueryParameter("actor", actor)
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .build()
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw IOException("Bluesky authorFeed responded HTTP ${res.code}")
            }
            val root = json.parseToJsonElement(res.body.string()).jsonObject
            val feedArray = root["feed"]?.jsonArray ?: return emptyList()
            return feedArray.mapNotNull { item ->
                val postObj = item.jsonObject["post"]?.jsonObject ?: return@mapNotNull null
                mapPost(postObj)
            }
        }
    }

    private fun mapPost(obj: JsonObject): Post? {
        val cid = obj["cid"]?.stringValue() ?: return null
        val author = obj["author"]?.jsonObject ?: return null
        val handle = author["handle"]?.stringValue() ?: return null
        val displayName = author["displayName"]?.stringValue() ?: handle
        val authorAvatar = author["avatar"]?.stringValue()?.let {
            ImageSource.Remote(url = it, aspectRatio = 1f)
        }
        val record = obj["record"]?.jsonObject ?: return null
        val text = record["text"]?.stringValue().orEmpty()
        val likeCount = obj["likeCount"]?.jsonPrimitive?.intOrNull ?: 0
        val replyCount = obj["replyCount"]?.jsonPrimitive?.intOrNull ?: 0
        val repostCount = obj["repostCount"]?.jsonPrimitive?.intOrNull ?: 0
        val indexedAt = obj["indexedAt"]?.stringValue().orEmpty()
        val media = extractMedia(cid = cid, embed = obj["embed"]?.jsonObject)
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
            authorAvatar = authorAvatar,
        )
    }

    /**
     * Returns image and video [MediaRef]s from a Bluesky embed view.
     *
     * Handles three shapes:
     *  - `app.bsky.embed.images#view` — image-only post
     *  - `app.bsky.embed.video#view` — single video (HLS playlist URL in `playlist` field)
     *  - `app.bsky.embed.recordWithMedia#view` — quote-post with attached media; recurses into the
     *    inner `media` object
     */
    private fun extractMedia(cid: String, embed: JsonObject?): List<MediaRef> {
        if (embed == null) return emptyList()
        val type = embed["\$type"]?.stringValue()
        return when (type) {
            "app.bsky.embed.images#view" -> imagesFromView(cid, embed["images"]?.jsonArray)
            "app.bsky.embed.video#view" -> listOfNotNull(videoFromView(cid, embed))
            "app.bsky.embed.recordWithMedia#view" -> {
                val media = embed["media"]?.jsonObject ?: return emptyList()
                extractMedia(cid, media)
            }
            else -> emptyList()
        }
    }

    private fun imagesFromView(cid: String, array: JsonArray?): List<MediaRef> {
        if (array == null) return emptyList()
        return array.mapIndexedNotNull { idx, el ->
            val url = el.jsonObject["fullsize"]?.stringValue() ?: return@mapIndexedNotNull null
            MediaRef.Image(
                id = "bsky_${cid}_$idx",
                source = ImageSource.Remote(url = url, aspectRatio = 4f / 3f),
            )
        }
    }

    private fun videoFromView(cid: String, embed: JsonObject): MediaRef.Video? {
        val playlist = embed["playlist"]?.stringValue() ?: return null
        val aspect = embed["aspectRatio"]?.jsonObject?.let { ar ->
            val w = ar["width"]?.jsonPrimitive?.intOrNull ?: 0
            val h = ar["height"]?.jsonPrimitive?.intOrNull ?: 0
            if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
        } ?: 16f / 9f
        return MediaRef.Video(
            id = "bsky_${cid}_video",
            source = VideoSource.Remote(url = playlist, aspectRatio = aspect),
        )
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

    private fun formatJoinedLabel(iso: String): String {
        if (iso.isEmpty()) return ""
        return try {
            val ts = Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
            val month = ts.month.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            )
            "Joined $month ${ts.year}"
        } catch (e: Exception) {
            ""
        }
    }

    private fun JsonElement.stringValue(): String? {
        val prim = this as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return if (prim.isString) prim.content else null
    }

    private companion object {
        const val BASE_URL = "https://public.api.bsky.app/xrpc/app.bsky.feed.getFeed"
        const val PROFILE_URL = "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile"
        const val AUTHOR_FEED_URL = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed"
    }
}

/**
 * The Bluesky feeds we display as tabs in the timeline. Each is a public feed-generator AT-URI;
 * `slug` is used as the cache filename and persistence key.
 */
enum class BlueskyPinnedFeed(
    val slug: String,
    val label: String,
    val feedUri: String,
) {
    WHATS_HOT(
        slug = "whats_hot",
        label = "What's Hot",
        feedUri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot",
    ),
    OBSERVABILITY(
        slug = "observability",
        label = "Observability",
        feedUri = "at://did:plc:ozumdgotrhpjrqseoyyxf54g/app.bsky.feed.generator/aaacj5ef2x6k4",
    ),
    SOCCER(
        slug = "efl",
        label = "EFL",
        feedUri = "at://did:plc:tza3zgbp6d65dejzdme2u5hx/app.bsky.feed.generator/aaac6uk2w4gjq",
    ),
}
