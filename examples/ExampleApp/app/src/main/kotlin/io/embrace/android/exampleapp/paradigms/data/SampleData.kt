package io.embrace.android.exampleapp.paradigms.data

object SampleData {

    val postAuthors: List<PostAuthor> = listOf(
        PostAuthor(
            handle = "ada",
            displayName = "Ada Lovelace",
            bio = "Notes on the Analytical Engine. Mostly correct.",
            followerCount = 21_345,
            followingCount = 184,
        ),
        PostAuthor(
            handle = "linus",
            displayName = "Linus T.",
            bio = "Kernel hacker. Opinions own, code GPL.",
            followerCount = 982_133,
            followingCount = 12,
        ),
        PostAuthor(
            handle = "grace",
            displayName = "Grace Hopper",
            bio = "Compilers. Nanoseconds. Ships.",
            followerCount = 56_421,
            followingCount = 311,
        ),
    )

    val posts: List<Post> = listOf(
        Post("t1", "ada", "Ada Lovelace", "Wrote a loop that finally terminates. Big day.", 412, 17, 88),
        Post("t2", "linus", "Linus T.", "Reverted that patch. Again.", 9_812, 504, 1_233),
        Post("t3", "grace", "Grace Hopper", "It's easier to ask forgiveness than permission.", 7_344, 22, 412),
        Post("t4", "ada", "Ada Lovelace", "Counter-counterfactual: what if we just didn't?", 188, 9, 21),
        Post("t5", "linus", "Linus T.", "Talk is cheap. Show me the diff.", 22_104, 871, 4_002),
    )

    val newsSections: List<NewsSection> = listOf(
        NewsSection("world", "World"),
        NewsSection("tech", "Technology"),
        NewsSection("opinion", "Opinion"),
    )

    val articles: List<Article> = listOf(
        Article(
            id = "a1",
            sectionId = "world",
            headline = "Treaty Signed After Decade of Talks",
            byline = "By Staff",
            summary = "Negotiators reached agreement on a long-disputed clause.",
            body = "After ten rounds of negotiation, delegates announced a final text on Tuesday. " +
                "The agreement, if ratified, would reshape regional trade and immigration rules.",
        ),
        Article(
            id = "a2",
            sectionId = "tech",
            headline = "On-Device AI Models Hit New Memory Lows",
            byline = "By J. Reporter",
            summary = "Quantization advances push 7B-parameter models below 2GB.",
            body = "Researchers presented a quantization scheme that maintains accuracy while " +
                "halving memory footprint. Phone vendors are reportedly already integrating it.",
        ),
        Article(
            id = "a3",
            sectionId = "opinion",
            headline = "Why Navigation APIs Keep Getting Reinvented",
            byline = "By A Columnist",
            summary = "Each generation of UI framework rediscovers the back stack.",
            body = "From Activities to Fragments to Composables to typed key navigation, " +
                "the industry seems unable to stop reinventing how a user gets from one screen to the next.",
        ),
        Article(
            id = "a4",
            sectionId = "tech",
            headline = "Gradle 9 Lands With Faster Configuration",
            byline = "By Staff",
            summary = "Configuration cache becomes default; some plugins still incompatible.",
            body = "The new release prioritizes incremental builds and configuration cache adoption.",
        ),
    )

    val productCategories: List<ProductCategory> = listOf(
        ProductCategory("electronics", "Electronics"),
        ProductCategory("books", "Books"),
        ProductCategory("kitchen", "Kitchen"),
    )

    val products: List<Product> = listOf(
        Product(
            id = "p1",
            categoryId = "electronics",
            title = "USB-C Hub, 7-in-1",
            priceCents = 3499,
            rating = 4.4,
            reviewCount = 2_104,
            description = "HDMI, Ethernet, SD/microSD, two USB-A, USB-C PD passthrough.",
        ),
        Product(
            id = "p2",
            categoryId = "electronics",
            title = "Mechanical Keyboard, 75%",
            priceCents = 12999,
            rating = 4.7,
            reviewCount = 814,
            description = "Hot-swappable, wireless, RGB, with knob.",
        ),
        Product(
            id = "p3",
            categoryId = "books",
            title = "Effective Kotlin",
            priceCents = 4500,
            rating = 4.8,
            reviewCount = 312,
            description = "Best practices for writing clean, idiomatic Kotlin.",
        ),
        Product(
            id = "p4",
            categoryId = "kitchen",
            title = "Cast Iron Skillet, 12\"",
            priceCents = 5499,
            rating = 4.6,
            reviewCount = 9_302,
            description = "Pre-seasoned, made in the USA, lasts forever.",
        ),
    )

    fun post(id: String): Post? = posts.firstOrNull { it.id == id }
    fun author(handle: String): PostAuthor? = postAuthors.firstOrNull { it.handle == handle }
    fun article(id: String): Article? = articles.firstOrNull { it.id == id }
    fun section(id: String): NewsSection? = newsSections.firstOrNull { it.id == id }
    fun product(id: String): Product? = products.firstOrNull { it.id == id }
    fun category(id: String): ProductCategory? = productCategories.firstOrNull { it.id == id }
    fun articlesIn(sectionId: String): List<Article> = articles.filter { it.sectionId == sectionId }
    fun productsIn(categoryId: String): List<Product> = products.filter { it.categoryId == categoryId }
}
