package io.embrace.android.exampleapp.paradigms.social.navcompose

import kotlinx.serialization.Serializable

internal sealed interface SocialRoute {

    @Serializable
    object Timeline : SocialRoute

    @Serializable
    data class PostDetail(val postId: String) : SocialRoute

    @Serializable
    data class Profile(val handle: String) : SocialRoute

    @Serializable
    object Compose : SocialRoute
}

internal const val SAVED_STATE_POSTED_BODY: String = "social_navcompose_posted_body"
