package io.embrace.android.exampleapp.paradigms.social.nav3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.ComposePostUi
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileUi
import io.embrace.android.exampleapp.paradigms.social.ui.TimelineUi
import io.embrace.android.exampleapp.paradigms.social.ui.PostDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialNav3Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val backStack = remember { mutableStateListOf<SocialNav3Keys>(SocialNav3Keys.Timeline) }
                var postedBody by remember { mutableStateOf<String?>(null) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is SocialNav3Keys.Timeline -> NavEntry(key) {
                                TimelineUi(
                                    title = "Home (Nav3)",
                                    posts = SampleData.posts,
                                    onPostClick = { id ->
                                        backStack.add(SocialNav3Keys.PostDetails(id))
                                    },
                                    onAuthorClick = { handle ->
                                        backStack.add(SocialNav3Keys.Profile(handle))
                                    },
                                    onCompose = { backStack.add(SocialNav3Keys.Compose) },
                                    postedBody = postedBody,
                                )
                            }
                            is SocialNav3Keys.PostDetails -> NavEntry(key) {
                                val post = SampleData.post(key.postId)
                                if (post == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    PostDetailUi(
                                        post = post,
                                        onAuthorClick = { handle ->
                                            backStack.add(SocialNav3Keys.Profile(handle))
                                        },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            is SocialNav3Keys.Profile -> NavEntry(key) {
                                val author = SampleData.author(key.handle)
                                if (author == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    val authorPosts = SampleData.posts.filter { it.authorHandle == author.handle }
                                    ProfileUi(
                                        author = author,
                                        authorPosts = authorPosts,
                                        onPostClick = { id ->
                                            backStack.add(SocialNav3Keys.PostDetails(id))
                                        },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            is SocialNav3Keys.Compose -> NavEntry(key) {
                                ComposePostUi(
                                    onPost = { body ->
                                        postedBody = body
                                        backStack.removeLastOrNull()
                                    },
                                    onCancel = { backStack.removeLastOrNull() },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, SocialNav3Activity::class.java)
    }
}
