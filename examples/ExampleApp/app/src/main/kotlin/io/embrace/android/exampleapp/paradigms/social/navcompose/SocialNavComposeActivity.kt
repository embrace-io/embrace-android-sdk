package io.embrace.android.exampleapp.paradigms.social.navcompose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.ComposePostUi
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileUi
import io.embrace.android.exampleapp.paradigms.social.ui.TimelineUi
import io.embrace.android.exampleapp.paradigms.social.ui.PostDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialNavComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = SocialRoute.Timeline,
                ) {
                    composable<SocialRoute.Timeline> { entry ->
                        val savedHandle = entry.savedStateHandle
                        val postedBody by savedHandle
                            .getStateFlow<String?>(SAVED_STATE_POSTED_BODY, null)
                            .collectAsState()
                        TimelineUi(
                            title = "Home (Nav-Compose)",
                            staticPosts = SampleData.posts,
                            onPostClick = { id ->
                                navController.navigate(SocialRoute.PostDetail(id))
                            },
                            onAuthorClick = { handle ->
                                navController.navigate(SocialRoute.Profile(handle))
                            },
                            onCompose = {
                                navController.navigate(SocialRoute.Compose)
                            },
                            postedBody = postedBody,
                        )
                    }
                    composable<SocialRoute.PostDetail> { entry ->
                        val route: SocialRoute.PostDetail = entry.toRoute()
                        val post = SampleData.post(route.postId)
                        if (post == null) {
                            navController.popBackStack()
                        } else {
                            PostDetailUi(
                                post = post,
                                onAuthorClick = { handle ->
                                    navController.navigate(SocialRoute.Profile(handle))
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<SocialRoute.Profile> { entry ->
                        val route: SocialRoute.Profile = entry.toRoute()
                        val author = SampleData.author(route.handle)
                        if (author == null) {
                            navController.popBackStack()
                        } else {
                            val authorPosts = SampleData.posts.filter { it.authorHandle == author.handle }
                            ProfileUi(
                                author = author,
                                authorPosts = authorPosts,
                                onPostClick = { id ->
                                    navController.navigate(SocialRoute.PostDetail(id))
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<SocialRoute.Compose> {
                        ComposePostUi(
                            onPost = { body ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(SAVED_STATE_POSTED_BODY, body)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, SocialNavComposeActivity::class.java)
    }
}
