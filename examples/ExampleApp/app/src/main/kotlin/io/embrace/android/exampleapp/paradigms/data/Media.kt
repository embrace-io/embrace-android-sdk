package io.embrace.android.exampleapp.paradigms.data

sealed interface MediaRef {
    val id: String

    data class Image(
        override val id: String,
        val source: ImageSource,
        val altText: String? = null,
    ) : MediaRef

    data class Video(
        override val id: String,
        val source: VideoSource,
        val captionTrack: String? = null,
    ) : MediaRef
}

sealed interface ImageSource {
    val aspectRatio: Float

    data class Procedural(
        val seed: Long,
        override val aspectRatio: Float = 1f,
    ) : ImageSource

    data class LocalDrawable(
        val resId: Int,
        override val aspectRatio: Float = 1f,
    ) : ImageSource

    data class Remote(
        val url: String,
        override val aspectRatio: Float = 1f,
    ) : ImageSource
}

sealed interface VideoSource {
    val aspectRatio: Float
    val durationMs: Long

    data class Procedural(
        val seed: Long,
        override val aspectRatio: Float = 16f / 9f,
        override val durationMs: Long = 30_000L,
    ) : VideoSource

    data class LocalRaw(
        val resId: Int,
        override val aspectRatio: Float = 16f / 9f,
        override val durationMs: Long = 0L,
    ) : VideoSource

    data class Remote(
        val url: String,
        override val aspectRatio: Float = 16f / 9f,
        override val durationMs: Long = 0L,
    ) : VideoSource
}
