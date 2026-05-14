package io.embrace.android.exampleapp.paradigms.news.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment

class NewsFragmentsActivity : FragmentActivity() {

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
        navController.graph = navController.createGraph(startDestination = ROUTE_SECTIONS) {
            fragment<NewsSectionsFragment>(ROUTE_SECTIONS)
            fragment<NewsArticleListFragment>("section/{$ARG_SECTION_ID}")
            fragment<NewsArticleDetailFragment>("article/{$ARG_ARTICLE_ID}")
        }
    }

    companion object {
        internal const val ROUTE_SECTIONS: String = "sections"
        internal const val ARG_SECTION_ID: String = "sectionId"
        internal const val ARG_ARTICLE_ID: String = "articleId"

        fun newIntent(context: Context): Intent =
            Intent(context, NewsFragmentsActivity::class.java)
    }
}
