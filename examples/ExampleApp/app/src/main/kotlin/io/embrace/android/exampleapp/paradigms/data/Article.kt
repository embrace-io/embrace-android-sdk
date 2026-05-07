package io.embrace.android.exampleapp.paradigms.data

data class Article(
    val id: String,
    val sectionId: String,
    val headline: String,
    val byline: String,
    val summary: String,
    val body: String,
    val heroImage: ImageSource? = null,
    val paragraphs: List<String> = emptyList(),
    val webContentHtml: String? = null,
    val relatedArticleIds: List<String> = emptyList(),
    val publishedAtIso: String = "",
    val readTimeMinutes: Int = 0,
    val tags: List<String> = emptyList(),
    val pullQuote: String? = null,
)

data class NewsSection(
    val id: String,
    val title: String,
    val accentSeed: Long = 0L,
)
