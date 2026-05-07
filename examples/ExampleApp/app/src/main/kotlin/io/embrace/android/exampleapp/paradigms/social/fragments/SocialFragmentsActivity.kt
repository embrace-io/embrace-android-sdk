package io.embrace.android.exampleapp.paradigms.social.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment

class SocialFragmentsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val containerId = View.generateViewId()
        setContentView(
            FrameLayout(this).apply {
                id = containerId
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            },
        )
        if (savedInstanceState != null) {
            return
        }
        val navHostFragment = NavHostFragment()
        supportFragmentManager.beginTransaction()
            .replace(containerId, navHostFragment)
            .commitNow()

        val navController = navHostFragment.navController
        navController.graph = navController.createGraph(startDestination = ROUTE_TIMELINE) {
            fragment<TimelineFragment>(ROUTE_TIMELINE)
            fragment<PostDetailFragment>("post/{$ARG_POST_ID}")
            fragment<ProfileFragment>("profile/{$ARG_HANDLE}")
            fragment<ComposePostFragment>(ROUTE_COMPOSE)
        }
    }

    companion object {
        internal const val ROUTE_TIMELINE: String = "timeline"
        internal const val ROUTE_COMPOSE: String = "compose"
        internal const val ARG_POST_ID: String = "postId"
        internal const val ARG_HANDLE: String = "handle"
        internal const val FRAGMENT_RESULT_COMPOSE: String = "social_fragments_compose_result"
        internal const val FRAGMENT_RESULT_KEY_BODY: String = "body"

        fun newIntent(context: Context): Intent =
            Intent(context, SocialFragmentsActivity::class.java)
    }
}
