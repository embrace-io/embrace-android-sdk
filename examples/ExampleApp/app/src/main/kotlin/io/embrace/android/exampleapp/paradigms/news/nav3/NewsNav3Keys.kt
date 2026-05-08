package io.embrace.android.exampleapp.paradigms.news.nav3

internal sealed interface NewsNav3Key {
    data object Sections : NewsNav3Key
    data class ArticleList(val sectionId: String) : NewsNav3Key
    data class ArticleDetail(val articleId: String) : NewsNav3Key
}
