package io.embrace.android.exampleapp.paradigms.bluesky

internal sealed interface BlueskyFeedKey {
    data object Timeline : BlueskyFeedKey
    data class PostDetail(val postId: String) : BlueskyFeedKey
    data class Profile(val handle: String) : BlueskyFeedKey
}
