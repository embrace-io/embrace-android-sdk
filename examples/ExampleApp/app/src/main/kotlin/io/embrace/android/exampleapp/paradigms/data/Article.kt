package io.embrace.android.exampleapp.paradigms.data

data class Article(
    val id: String,
    val sectionId: String,
    val headline: String,
    val byline: String,
    val summary: String,
    val body: String,
)

data class NewsSection(
    val id: String,
    val title: String,
)
