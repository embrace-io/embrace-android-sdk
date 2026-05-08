package io.embrace.android.exampleapp.paradigms.data

data class Post(
    val id: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val body: String,
    val likeCount: Int,
    val replyCount: Int,
    val repostCount: Int,
)

data class PostAuthor(
    val handle: String,
    val displayName: String,
    val bio: String,
    val followerCount: Int,
    val followingCount: Int,
)
