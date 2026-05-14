package io.embrace.android.exampleapp.paradigms.social.nav3

internal sealed interface SocialNav3Keys {
    data object Timeline : SocialNav3Keys
    data class PostDetails(val postId: String) : SocialNav3Keys
    data class Profile(val handle: String) : SocialNav3Keys
    data object Compose : SocialNav3Keys
}
