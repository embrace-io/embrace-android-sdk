package io.embrace.android.exampleapp.paradigms

import android.content.Context
import io.embrace.android.exampleapp.paradigms.ecommerce.a2a.EcommerceA2ACategoriesActivity
import io.embrace.android.exampleapp.paradigms.ecommerce.fragments.EcommerceFragmentsActivity
import io.embrace.android.exampleapp.paradigms.ecommerce.nav3.EcommerceNav3Activity
import io.embrace.android.exampleapp.paradigms.ecommerce.navcompose.EcommerceNavComposeActivity
import io.embrace.android.exampleapp.paradigms.news.a2a.NewsA2ASectionsActivity
import io.embrace.android.exampleapp.paradigms.news.fragments.NewsFragmentsActivity
import io.embrace.android.exampleapp.paradigms.news.nav3.NewsNav3Activity
import io.embrace.android.exampleapp.paradigms.news.navcompose.NewsNavComposeActivity
import io.embrace.android.exampleapp.paradigms.social.a2a.SocialA2ATimelineActivity
import io.embrace.android.exampleapp.paradigms.social.fragments.SocialFragmentsActivity
import io.embrace.android.exampleapp.paradigms.social.nav3.SocialNav3Activity
import io.embrace.android.exampleapp.paradigms.social.navcompose.SocialNavComposeActivity

object ParadigmCatalog {

    val runs: List<ParadigmRun> = NavParadigm.entries.flatMap { paradigm ->
        NavStyle.entries.map { style ->
            ParadigmRun(paradigm, style, launchFor(paradigm, style))
        }
    }

    fun runsFor(paradigm: NavParadigm): List<ParadigmRun> = runs.filter { it.paradigm == paradigm }

    private fun launchFor(paradigm: NavParadigm, style: NavStyle): (Context) -> Unit = when (paradigm) {
        NavParadigm.SOCIAL -> socialLauncher(style)
        NavParadigm.NEWS -> newsLauncher(style)
        NavParadigm.ECOMMERCE -> ecommerceLauncher(style)
    }

    private fun socialLauncher(style: NavStyle): (Context) -> Unit = when (style) {
        NavStyle.ACTIVITY_TO_ACTIVITY -> { ctx ->
            ctx.startActivity(SocialA2ATimelineActivity.newIntent(ctx))
        }
        NavStyle.FRAGMENTS_PRE_24 -> { ctx ->
            ctx.startActivity(SocialFragmentsActivity.newIntent(ctx))
        }
        NavStyle.NAV_COMPOSE_28 -> { ctx ->
            ctx.startActivity(SocialNavComposeActivity.newIntent(ctx))
        }
        NavStyle.NAV3 -> { ctx ->
            ctx.startActivity(SocialNav3Activity.newIntent(ctx))
        }
    }

    private fun newsLauncher(style: NavStyle): (Context) -> Unit = when (style) {
        NavStyle.ACTIVITY_TO_ACTIVITY -> { ctx ->
            ctx.startActivity(NewsA2ASectionsActivity.newIntent(ctx))
        }
        NavStyle.FRAGMENTS_PRE_24 -> { ctx ->
            ctx.startActivity(NewsFragmentsActivity.newIntent(ctx))
        }
        NavStyle.NAV_COMPOSE_28 -> { ctx ->
            ctx.startActivity(NewsNavComposeActivity.newIntent(ctx))
        }
        NavStyle.NAV3 -> { ctx ->
            ctx.startActivity(NewsNav3Activity.newIntent(ctx))
        }
    }

    private fun ecommerceLauncher(style: NavStyle): (Context) -> Unit = when (style) {
        NavStyle.ACTIVITY_TO_ACTIVITY -> { ctx ->
            ctx.startActivity(EcommerceA2ACategoriesActivity.newIntent(ctx))
        }
        NavStyle.FRAGMENTS_PRE_24 -> { ctx ->
            ctx.startActivity(EcommerceFragmentsActivity.newIntent(ctx))
        }
        NavStyle.NAV_COMPOSE_28 -> { ctx ->
            ctx.startActivity(EcommerceNavComposeActivity.newIntent(ctx))
        }
        NavStyle.NAV3 -> { ctx ->
            ctx.startActivity(EcommerceNav3Activity.newIntent(ctx))
        }
    }
}
