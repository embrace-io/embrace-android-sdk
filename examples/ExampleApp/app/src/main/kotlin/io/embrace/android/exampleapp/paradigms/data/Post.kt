package io.embrace.android.exampleapp.paradigms.data

data class Post(
    val id: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val body: String,
    val likeCount: Int,
    val replyCount: Int,
    val repostCount: Int,
    val media: List<MediaRef> = emptyList(),
    val timestampLabel: String = "",
    val isPinned: Boolean = false,
    val isVerified: Boolean = false,
    val mentions: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
)

data class PostAuthor(
    val handle: String,
    val displayName: String,
    val bio: String,
    val followerCount: Int,
    val followingCount: Int,
    val avatar: ImageSource? = null,
    val coverImage: ImageSource? = null,
    val location: String = "",
    val joinedLabel: String = "",
    val isVerified: Boolean = false,
)
