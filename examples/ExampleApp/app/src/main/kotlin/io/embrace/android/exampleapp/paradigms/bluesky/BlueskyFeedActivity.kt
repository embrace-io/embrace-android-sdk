package io.embrace.android.exampleapp.paradigms.bluesky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.bluesky.ui.BlueskyFeedTimelineUi
import io.embrace.android.exampleapp.paradigms.social.ui.PostDetailUi
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileScreen
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

/**
 * Standalone activity for the Bluesky live-feed demo. Hosts an in-memory Nav3 back stack:
 * Timeline → PostDetail → Profile. PostDetail looks up first in [BlueskyFeedStore] (the timeline's
 * cached posts) and falls back to [ProfileResolver]'s author-feed cache so posts surfaced via
 * a profile screen are also viewable in detail.
 */
class BlueskyFeedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val graph = appGraph()
                val backStack = remember {
                    mutableStateListOf<BlueskyFeedKey>(BlueskyFeedKey.Timeline)
                }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            BlueskyFeedKey.Timeline -> NavEntry(key) {
                                BlueskyFeedTimelineUi(
                                    onPostClick = { id ->
                                        backStack.add(BlueskyFeedKey.PostDetail(id))
                                    },
                                    onAuthorClick = { handle ->
                                        backStack.add(BlueskyFeedKey.Profile(handle))
                                    },
                                    onBack = { finish() },
                                )
                            }
                            is BlueskyFeedKey.PostDetail -> NavEntry(key) {
                                val post = graph.blueskyFeedStore.posts.value
                                    .firstOrNull { it.id == key.postId }
                                    ?: graph.profileResolver.cachedPost(key.postId)
                                if (post == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    PostDetailUi(
                                        post = post,
                                        onAuthorClick = { handle ->
                                            backStack.add(BlueskyFeedKey.Profile(handle))
                                        },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            is BlueskyFeedKey.Profile -> NavEntry(key) {
                                ProfileScreen(
                                    handle = key.handle,
                                    onPostClick = { id ->
                                        backStack.add(BlueskyFeedKey.PostDetail(id))
                                    },
                                    onBack = { backStack.removeLastOrNull() },
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
            Intent(context, BlueskyFeedActivity::class.java)
    }
}
