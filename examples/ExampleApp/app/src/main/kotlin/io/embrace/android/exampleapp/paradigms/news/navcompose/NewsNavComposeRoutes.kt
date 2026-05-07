package io.embrace.android.exampleapp.paradigms.news.navcompose

import kotlinx.serialization.Serializable

internal sealed interface NewsRoute {

    @Serializable
    object Sections : NewsRoute

    @Serializable
    data class ArticleList(val sectionId: String) : NewsRoute

    @Serializable
    data class ArticleDetail(val articleId: String) : NewsRoute
}
