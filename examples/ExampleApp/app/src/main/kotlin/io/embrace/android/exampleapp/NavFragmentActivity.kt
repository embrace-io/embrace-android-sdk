package io.embrace.android.exampleapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment

abstract class NavFragmentActivity : FragmentActivity() {

    protected lateinit var navController: NavController
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navHostId = View.generateViewId()
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }

        val navHostContainer = FrameLayout(this).apply {
            id = navHostId
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        rootLayout.addView(navHostContainer)

        val buttonHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val buttonScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            addView(buttonHost)
        }
        rootLayout.addView(buttonScroll)

        setContentView(rootLayout)

        val navHostFragment = NavHostFragment()
        supportFragmentManager.beginTransaction()
            .replace(navHostId, navHostFragment)
            .commitNow()

        navController = navHostFragment.navController
        navController.graph = createNavGraph(navController)

        createActions(navController).forEach { (label, action) ->
            buttonHost.addView(
                Button(this).apply {
                    text = label
                    setOnClickListener { action() }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
            )
        }
    }

    protected abstract fun createNavGraph(navController: NavController): NavGraph

    protected abstract fun createActions(navController: NavController): List<Pair<String, () -> Unit>>
}
