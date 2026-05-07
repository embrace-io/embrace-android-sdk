package io.embrace.android.exampleapp.paradigms.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MediaRef {
    val id: String

    @Serializable
    @SerialName("image")
    data class Image(
        override val id: String,
        val source: ImageSource,
        val altText: String? = null,
    ) : MediaRef

    @Serializable
    @SerialName("video")
    data class Video(
        override val id: String,
        val source: VideoSource,
        val captionTrack: String? = null,
    ) : MediaRef
}

@Serializable
sealed interface ImageSource {
    val aspectRatio: Float

    @Serializable
    @SerialName("procedural")
    data class Procedural(
        val seed: Long,
        override val aspectRatio: Float = 1f,
    ) : ImageSource

    @Serializable
    @SerialName("drawable")
    data class LocalDrawable(
        val resId: Int,
        override val aspectRatio: Float = 1f,
    ) : ImageSource

    @Serializable
    @SerialName("remote")
    data class Remote(
        val url: String,
        override val aspectRatio: Float = 1f,
    ) : ImageSource
}

@Serializable
sealed interface VideoSource {
    val aspectRatio: Float
    val durationMs: Long

    @Serializable
    @SerialName("procedural")
    data class Procedural(
        val seed: Long,
        override val aspectRatio: Float = 16f / 9f,
        override val durationMs: Long = 30_000L,
    ) : VideoSource

    @Serializable
    @SerialName("raw")
    data class LocalRaw(
        val resId: Int,
        override val aspectRatio: Float = 16f / 9f,
        override val durationMs: Long = 0L,
    ) : VideoSource

    @Serializable
    @SerialName("remote")
    data class Remote(
        val url: String,
        override val aspectRatio: Float = 16f / 9f,
        override val durationMs: Long = 0L,
    ) : VideoSource
}
