package io.embrace.android.exampleapp.paradigms.data

import kotlin.random.Random

@Suppress("LargeClass")
object SampleData {

    val postAuthors: List<PostAuthor> = generateAuthors()

    val posts: List<Post> = generatePosts(authors = postAuthors, count = 240)

    val newsSections: List<NewsSection> = listOf(
        NewsSection("world", "World", accentSeed = 1001L),
        NewsSection("tech", "Technology", accentSeed = 1002L),
        NewsSection("business", "Business", accentSeed = 1003L),
        NewsSection("science", "Science", accentSeed = 1004L),
        NewsSection("opinion", "Opinion", accentSeed = 1005L),
        NewsSection("culture", "Arts & Culture", accentSeed = 1006L),
        NewsSection("sports", "Sports", accentSeed = 1007L),
        NewsSection("travel", "Travel", accentSeed = 1008L),
    )

    val articles: List<Article> = generateArticles(sections = newsSections, perSection = 14)

    val productCategories: List<ProductCategory> = listOf(
        ProductCategory("electronics", "Electronics", accentSeed = 2001L),
        ProductCategory("books", "Books", accentSeed = 2002L),
        ProductCategory("kitchen", "Kitchen", accentSeed = 2003L),
        ProductCategory("outdoors", "Outdoors", accentSeed = 2004L),
        ProductCategory("home", "Home & Garden", accentSeed = 2005L),
        ProductCategory("toys", "Toys & Games", accentSeed = 2006L),
        ProductCategory("beauty", "Beauty", accentSeed = 2007L),
        ProductCategory("office", "Office", accentSeed = 2008L),
    )

    val products: List<Product> = generateProducts(categories = productCategories, perCategory = 14)

    fun post(id: String): Post? = posts.firstOrNull { it.id == id }
    fun author(handle: String): PostAuthor? = postAuthors.firstOrNull { it.handle == handle }
    fun article(id: String): Article? = articles.firstOrNull { it.id == id }
    fun section(id: String): NewsSection? = newsSections.firstOrNull { it.id == id }
    fun product(id: String): Product? = products.firstOrNull { it.id == id }
    fun category(id: String): ProductCategory? = productCategories.firstOrNull { it.id == id }
    fun articlesIn(sectionId: String): List<Article> = articles.filter { it.sectionId == sectionId }
    fun productsIn(categoryId: String): List<Product> = products.filter { it.categoryId == categoryId }
}

private val AUTHOR_SEEDS: List<Triple<String, String, String>> = listOf(
    Triple("ada", "Ada Lovelace", "Notes on the Analytical Engine. Mostly correct."),
    Triple("linus", "Linus T.", "Kernel hacker. Opinions own, code GPL."),
    Triple("grace", "Grace Hopper", "Compilers. Nanoseconds. Ships."),
    Triple("alan", "Alan Turing", "Asking machines awkward questions."),
    Triple("margaret", "Margaret H.", "Apollo. Software engineering, the term."),
    Triple("bjarne", "Bjarne S.", "There are only two kinds of languages."),
    Triple("rich", "Rich H.", "Simple. Made. Easy."),
    Triple("jeff", "Jeff D.", "Distributed systems are mostly clocks."),
    Triple("sandi", "Sandi M.", "Practical object-oriented design."),
    Triple("kelsey", "Kelsey H.", "Production-grade, hand-rolled."),
    Triple("mira", "Mira K.", "Embedded ML and napping."),
    Triple("priya", "Priya R.", "Type theory in the wild."),
    Triple("satoshi", "Satoshi N.", "Idle hashes, mostly."),
    Triple("nina", "Nina A.", "Compiler internals, perf, opera."),
    Triple("oren", "Oren D.", "Systems. Disks. Birds."),
    Triple("yuki", "Yuki S.", "Distributed databases and matcha."),
    Triple("renee", "Renée P.", "Static analysis, garden tools."),
    Triple("hansel", "Hansel K.", "Async runtimes for breakfast."),
    Triple("brigitte", "Brigitte L.", "Real-time graphics; old games."),
    Triple("oluwa", "Oluwafemi O.", "ASTs and arepas."),
)

private val POST_OPENERS: List<String> = listOf(
    "Spent the morning",
    "Three hours in,",
    "Counter-intuitively,",
    "Hot take:",
    "Today I learned",
    "Ship it. Then",
    "Reverted that. Again.",
    "If you must,",
    "Quietly impressed by",
    "Honestly,",
    "Friendly reminder:",
    "Reading the spec,",
    "Late-night thought:",
    "Every time I touch this,",
    "There's a thing nobody says about",
    "Reading old PRs,",
    "Three good ideas,",
    "Mid-flight,",
    "Took me a week to admit it but",
    "If your team is debating",
)

private val POST_BODIES: List<String> = listOf(
    "the test that's been red since 2024 finally went green and I am suspicious of every assumption I held about it",
    "we keep discovering that the production deploy can in fact tolerate a 90 second clock skew, which is a load-bearing fact",
    "the docs lie. The header is not optional. Cap-locks: NOT OPTIONAL",
    "shipping a feature flag is six months of work and the actual feature is one afternoon",
    "if you can't measure it you can ship it anyway, you'll just feel bad about it",
    "every interesting bug eventually turns into a question about caching invalidation or about who owns the data",
    "I've decided that the right answer here is no, but very politely and with a long Notion doc",
    "there is no such thing as 'just' configuration, and people who say there is haven't read the YAML",
    "the linker error is the universe asking you, kindly, if you would please reconsider your choices",
    "we wrote a hand-tuned ring buffer because the off-the-shelf one allocated; the off-the-shelf one is now correct",
    "the migration plan looked great until we realized 'forwards-compatible' meant 'forwards from where we'd already moved'",
    "got a P1 from the on-call who wanted to know what the dashboard was supposed to look like; this is a documentation problem",
    "the hardest part of refactoring is convincing yourself you don't need to refactor the part that's working",
    "took the call. Pushed back politely. Came back with a doc. Won the day. Felt small",
    "today's lesson: the integration test that takes 4 minutes is the one that actually catches the regression",
    "the SDK API design rule is 'minimal surface area'; the implementation rule is 'every public symbol is regret'",
    "when a system is described as 'eventually consistent', the word doing the work is 'eventually'",
    "I've been told the right way to onboard new engineers is to give them a small bug; we have provided several",
    "every time someone says 'we'll fix it in v2' I age six months",
    "the actual best practice is the one your team will follow on a Friday at 5pm",
)

private val POST_HASHTAGS: List<String> = listOf(
    "#kotlin", "#android", "#architecture", "#observability", "#perf",
    "#latency", "#refactor", "#systems", "#opentelemetry", "#oncall",
)

private fun generateAuthors(): List<PostAuthor> = AUTHOR_SEEDS.mapIndexed { idx, (handle, name, bio) ->
    PostAuthor(
        handle = handle,
        displayName = name,
        bio = bio,
        followerCount = 1_000 + (idx * 4_113) % 980_000,
        followingCount = 12 + (idx * 31) % 480,
        avatar = ImageSource.Procedural(seed = 50_000L + idx, aspectRatio = 1f),
        coverImage = ImageSource.Procedural(seed = 60_000L + idx, aspectRatio = 3f / 1f),
        location = listOf("Brooklyn", "Berlin", "Bangalore", "Toronto", "Lagos", "Tokyo", "Lisbon")[idx % 7],
        joinedLabel = "Joined ${listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")[idx % 6]} 20${10 + idx % 16}",
        isVerified = idx % 3 == 0,
    )
}

private fun generatePosts(authors: List<PostAuthor>, count: Int): List<Post> = List(count) { i ->
    val rng = Random(seed = 1_000_000L + i)
    val author = authors[i % authors.size]
    val openers = (1..rng.nextInt(1, 4)).map { POST_OPENERS[rng.nextInt(POST_OPENERS.size)] }
    val bodies = (1..rng.nextInt(2, 5)).map { POST_BODIES[rng.nextInt(POST_BODIES.size)] }
    val combined = openers.zip(bodies + bodies).joinToString(separator = "\n\n") { (opener, body) ->
        "$opener $body."
    }
    val hashtags = (1..rng.nextInt(0, 4)).map { POST_HASHTAGS[rng.nextInt(POST_HASHTAGS.size)] }.distinct()
    val mentionPool = authors.filter { it.handle != author.handle }
    val mentions = (1..rng.nextInt(0, 3)).map { mentionPool[rng.nextInt(mentionPool.size)].handle }.distinct()
    val mediaCount = when (rng.nextInt(10)) {
        in 0..3 -> 0
        in 4..6 -> 1
        in 7..8 -> rng.nextInt(2, 4)
        else -> 1
    }
    val media = List(mediaCount) { mediaIdx ->
        val seed = 200_000L + i * 7L + mediaIdx
        if (mediaIdx == 0 && rng.nextInt(8) == 0) {
            MediaRef.Video(
                id = "v_${i}_$mediaIdx",
                source = VideoSource.Procedural(seed = seed, aspectRatio = 16f / 9f),
            )
        } else {
            MediaRef.Image(
                id = "i_${i}_$mediaIdx",
                source = ImageSource.Procedural(
                    seed = seed,
                    aspectRatio = listOf(1f, 4f / 5f, 16f / 9f, 3f / 4f)[mediaIdx % 4],
                ),
            )
        }
    }
    Post(
        id = "p$i",
        authorHandle = author.handle,
        authorDisplayName = author.displayName,
        body = combined + (if (hashtags.isNotEmpty()) "\n\n${hashtags.joinToString(" ")}" else ""),
        likeCount = 10 + rng.nextInt(45_000),
        replyCount = rng.nextInt(900),
        repostCount = rng.nextInt(8_000),
        media = media,
        timestampLabel = listOf("now", "5m", "1h", "3h", "1d", "2d", "1w")[rng.nextInt(7)],
        isPinned = i == 0 || (i % 47 == 0),
        isVerified = author.isVerified,
        mentions = mentions,
        hashtags = hashtags,
    )
}

private val ARTICLE_HEADLINE_PARTS: Map<String, List<List<String>>> = mapOf(
    "world" to listOf(
        listOf("Treaty", "Summit", "Agreement", "Crisis", "Diplomats", "Negotiators"),
        listOf("Reached", "Signed", "Stalled", "Reopened", "Confronted"),
        listOf("After Decade of Talks", "Despite Tensions", "Amid Protests", "in Late-Night Vote"),
    ),
    "tech" to listOf(
        listOf("On-Device AI", "Quantum Chips", "Open-Source Compilers", "Wearables", "Self-Driving"),
        listOf("Hit", "Reach", "Exceed", "Approach", "Quietly Pass"),
        listOf("New Memory Lows", "Daily Active Users", "Audit Milestones", "Power Targets"),
    ),
    "business" to listOf(
        listOf("Earnings", "Layoffs", "Mergers", "Spin-offs", "IPOs"),
        listOf("Beat", "Disappoint", "Surprise", "Match", "Exceed"),
        listOf("Estimates by 12%", "Analysts' Forecasts", "Last Year's Numbers"),
    ),
    "science" to listOf(
        listOf("Researchers", "Astronomers", "Biologists", "Physicists", "Engineers"),
        listOf("Find", "Confirm", "Suggest", "Observe", "Demonstrate"),
        listOf("New Phase Transition", "Habitable Zone Candidate", "CRISPR Variant", "Material Anomaly"),
    ),
    "opinion" to listOf(
        listOf("Why", "When", "How", "What If"),
        listOf("Navigation APIs", "Microservices", "Code Reviews", "Standups", "Configuration"),
        listOf("Keep Getting Reinvented", "Need a Rethink", "Should Be Smaller", "Are Mostly Politics"),
    ),
    "culture" to listOf(
        listOf("Festival", "Retrospective", "Album", "Novel", "Exhibition"),
        listOf("Opens", "Returns", "Closes", "Premieres", "Surprises"),
        listOf("To Mixed Reviews", "After Long Hiatus", "With Standing Ovation", "Quietly"),
    ),
    "sports" to listOf(
        listOf("Champions", "Underdogs", "Veterans", "Rookies"),
        listOf("Edge Out", "Dominate", "Stun", "Hold Off", "Outlast"),
        listOf("Rivals in Overtime", "Defending Title-holders", "Top Seed", "Visiting Squad"),
    ),
    "travel" to listOf(
        listOf("Hidden", "Crowded", "Quiet", "Remote", "Unlikely"),
        listOf("Cafés", "Hikes", "Train Routes", "Beaches", "Side Streets"),
        listOf("Of Lisbon", "Outside Kyoto", "Across Patagonia", "in Reykjavík"),
    ),
)

private val ARTICLE_PARAGRAPH_TEMPLATES: List<String> = listOf(
    "After ten rounds of negotiation, delegates announced a final text on Tuesday. The agreement, if ratified, would reshape regional trade and immigration rules in ways that have already prompted strong reactions from advocacy groups and industry coalitions.",
    "Researchers presented a quantization scheme that maintains accuracy while halving memory footprint. Several phone vendors are reportedly already integrating it into upcoming releases, and the academic community is watching to see whether the gains hold up under scrutiny.",
    "From Activities to Fragments to Composables to typed key navigation, the industry seems unable to stop reinventing how a user gets from one screen to the next. The latest generation argues — convincingly, if not uniquely — that simpler primitives win in the long run.",
    "The new release prioritizes incremental builds and configuration cache adoption. Several long-standing plugin authors say the migration is mostly mechanical; others have flagged subtle interactions with custom transforms that may require more attention than vendors initially expected.",
    "On a clear morning the route is unrecognizable from any of the photographs. Locals will tell you that the photographs were taken in autumn, when the leaves do all of the work; in summer, the trees keep their secrets and the tourists keep moving.",
    "Investors had braced for a soft quarter, but management's commentary on margins and free cash flow surprised even the more optimistic analysts. Whether the trend persists into the second half of the year is the question on everyone's mind.",
    "The exhibition, which opens on the second floor of the museum's east wing, juxtaposes large-format photographs from the early 1980s with the sketches that informed them. The effect is meditative; the catalog is, by general agreement, exquisite.",
    "Engineers have spent decades trying to make this work; the latest attempt may finally have the right combination of materials and manufacturing tolerances to be commercially viable. The first production run is expected to ship in early 2027.",
    "It is, in the end, a question about defaults: about who decides what happens when the user does nothing in particular. The new framework's answer is not entirely satisfying, but it is at least explicit, which is more than most of its predecessors managed.",
    "By the seventh inning the game had settled into a rhythm; by the ninth it had broken free of any rhythm at all. The home crowd, which had grown used to disappointment, did not know quite what to do with itself.",
    "The team published its preprint last week and has since fielded a steady stream of questions from peer reviewers and journalists. Most of the answers, they say, will require running the experiment several more times — a process that will take months.",
    "There is a long-standing argument in the open-source world about who is responsible for security maintenance after a project's original author has moved on. This week's incident is unlikely to settle that argument, but it has given a new generation of maintainers a worked example.",
)

private val ARTICLE_HTML_BLOCKS: List<String> = listOf(
    "<h2>Background</h2><p>The story behind the headline is, as ever, more complicated than the headline lets on. Several of the central facts have been disputed by the parties named in this account.</p>",
    "<blockquote>\"It was the simplest version of the proposal that anyone had any chance of accepting,\" one person familiar with the discussions said.</blockquote>",
    "<h2>What's next</h2><p>Implementation is expected to begin in the coming weeks, though no firm timeline has been published. Observers caution that previous timelines have slipped.</p>",
    "<ul><li>The agreement covers tariffs and labor mobility.</li><li>Ratification requires three of the five signatories.</li><li>The text becomes provisional after the first ratification.</li></ul>",
    "<h2>Methodology</h2><p>The reporting in this article draws on interviews with seven people directly involved in the discussions, four of whom requested anonymity to discuss matters that remain non-public.</p>",
)

private fun generateArticles(sections: List<NewsSection>, perSection: Int): List<Article> =
    sections.flatMap { section ->
        val parts = ARTICLE_HEADLINE_PARTS[section.id]
            ?: ARTICLE_HEADLINE_PARTS.getValue("opinion")
        List(perSection) { idx ->
            val seed = (section.accentSeed * 100L) + idx
            val rng = Random(seed)
            val headline = listOf(
                parts[0][rng.nextInt(parts[0].size)],
                parts[1][rng.nextInt(parts[1].size)],
                parts[2][rng.nextInt(parts[2].size)],
            ).joinToString(" ")
            val paragraphCount = 6 + rng.nextInt(8)
            val paragraphs = List(paragraphCount) {
                ARTICLE_PARAGRAPH_TEMPLATES[rng.nextInt(ARTICLE_PARAGRAPH_TEMPLATES.size)]
            }
            val htmlBlockCount = 2 + rng.nextInt(3)
            val htmlBody = (1..htmlBlockCount).joinToString("\n") {
                ARTICLE_HTML_BLOCKS[rng.nextInt(ARTICLE_HTML_BLOCKS.size)]
            }
            val webHtml = """
                <html><head><style>
                body { font-family: -apple-system, sans-serif; line-height: 1.6; color: #1a1a1a; padding: 16px; }
                h2 { font-size: 1.2em; margin-top: 1.4em; }
                blockquote { border-left: 3px solid #888; margin: 1em 0; padding-left: 12px; color: #555; font-style: italic; }
                ul { padding-left: 20px; }
                </style></head>
                <body>
                <p><strong>${headline}.</strong> ${paragraphs.firstOrNull().orEmpty()}</p>
                $htmlBody
                <p><em>Reporting and analysis from our newsroom.</em></p>
                </body></html>
            """.trimIndent()
            val byline = "By " + listOf(
                "M. Reporter",
                "J. Correspondent",
                "Staff",
                "A. Bureau Chief",
                "K. Stringer",
            )[rng.nextInt(5)]
            val publishDay = 1 + rng.nextInt(28)
            val publishMonth = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[rng.nextInt(12)]
            Article(
                id = "${section.id}_$idx",
                sectionId = section.id,
                headline = headline,
                byline = byline,
                summary = paragraphs.first().take(160) + "…",
                body = paragraphs.joinToString("\n\n"),
                heroImage = ImageSource.Procedural(
                    seed = seed * 13L,
                    aspectRatio = 16f / 9f,
                ),
                paragraphs = paragraphs,
                webContentHtml = if (rng.nextInt(3) > 0) webHtml else null,
                relatedArticleIds = emptyList(),
                publishedAtIso = "$publishMonth $publishDay, 2026",
                readTimeMinutes = 3 + rng.nextInt(12),
                tags = listOf(section.title) + List(rng.nextInt(3)) { "topic-${rng.nextInt(20)}" },
                pullQuote = if (rng.nextInt(3) == 0) {
                    paragraphs[rng.nextInt(paragraphs.size)].split(". ").firstOrNull()
                } else {
                    null
                },
            )
        }
    }

private val PRODUCT_NAME_PARTS: Map<String, List<List<String>>> = mapOf(
    "electronics" to listOf(
        listOf("Wireless", "USB-C", "Bluetooth", "Mechanical", "RGB", "Gaming", "Compact", "Portable"),
        listOf("Hub", "Keyboard", "Mouse", "Headphones", "Speaker", "Charger", "Webcam", "Microphone"),
        listOf("Pro", "Mini", "X1", "Lite", "Plus", "Ultra"),
    ),
    "books" to listOf(
        listOf("Effective", "Concurrent", "Modern", "Pragmatic", "Functional", "Hands-On"),
        listOf("Kotlin", "Rust", "TypeScript", "Go", "Python", "Software Design"),
        listOf("for Engineers", "for Teams", "in Practice", "Second Edition", "Cookbook"),
    ),
    "kitchen" to listOf(
        listOf("Cast Iron", "Stainless", "Carbon Steel", "Copper", "Enameled"),
        listOf("Skillet", "Saucepan", "Dutch Oven", "Wok", "Stockpot", "Sauté Pan"),
        listOf("10\"", "12\"", "8 qt", "6 qt", "with Lid"),
    ),
    "outdoors" to listOf(
        listOf("Trail", "Alpine", "Backcountry", "Ultralight", "All-Weather"),
        listOf("Backpack", "Tent", "Sleeping Bag", "Trekking Poles", "Headlamp", "Stove"),
        listOf("65L", "2-Person", "0°F", "Carbon Fiber", "Compact"),
    ),
    "home" to listOf(
        listOf("Linen", "Organic Cotton", "Down", "Memory Foam", "Bamboo"),
        listOf("Sheet Set", "Comforter", "Pillow", "Throw Blanket", "Mattress Topper"),
        listOf("Queen", "King", "Twin", "Hypoallergenic"),
    ),
    "toys" to listOf(
        listOf("Wooden", "Educational", "STEM", "Sensory", "Cooperative"),
        listOf("Blocks", "Puzzle", "Board Game", "Marble Run", "Modeling Set"),
        listOf("Ages 5+", "Ages 8+", "Family Edition", "Classic"),
    ),
    "beauty" to listOf(
        listOf("Hydrating", "Vitamin C", "Retinol", "Niacinamide", "SPF 50"),
        listOf("Serum", "Moisturizer", "Cleanser", "Sunscreen", "Eye Cream"),
        listOf("30ml", "50ml", "Travel Size", "Refill"),
    ),
    "office" to listOf(
        listOf("Standing", "Ergonomic", "Adjustable", "Modular", "Compact"),
        listOf("Desk", "Chair", "Monitor Arm", "Footrest", "Lamp", "Organizer"),
        listOf("Walnut", "Black", "White", "L-Shape", "USB-C"),
    ),
)

private val PRODUCT_BULLETS: List<String> = listOf(
    "Engineered for daily use; survives commutes, kitchens, and the occasional toddler.",
    "Tested against industry standards and a few that we made up internally.",
    "Ships in fully-recyclable packaging; the cardboard is more durable than the box you remember.",
    "Designed in collaboration with people who actually use the product, not just spec it.",
    "Replaces three things you currently own and don't really like.",
    "Quiet operation, even at maximum load; reviewers consistently mention this.",
    "Comes with a real warranty, not the kind that requires a magnifying glass.",
    "Compatible with most accessories on the market, by intention rather than coincidence.",
    "Available in three colors, all of which are surprisingly tasteful.",
    "Heavy enough to feel substantial; light enough to carry without thinking about it.",
)

private val PRODUCT_DESCRIPTIONS: List<String> = listOf(
    "An everyday workhorse that punches above its price tag. After a few weeks you'll forget you bought it, in the best possible sense.",
    "Designed for the long haul. Materials, construction, and even the included accessories are picked to last beyond the warranty.",
    "If you've been meaning to replace the version you've had since 2019, this is the upgrade that justifies the upgrade.",
    "Quietly excellent. Doesn't shout about features; just does the thing it's supposed to do, all the time, without complaint.",
    "Built around the boring requirements that the cheaper versions tend to skip. Reviewers notice these things; you will too.",
)

private fun generateProducts(categories: List<ProductCategory>, perCategory: Int): List<Product> =
    categories.flatMap { category ->
        val parts = PRODUCT_NAME_PARTS.getValue(category.id)
        List(perCategory) { idx ->
            val seed = (category.accentSeed * 100L) + idx
            val rng = Random(seed)
            val title = listOf(
                parts[0][rng.nextInt(parts[0].size)],
                parts[1][rng.nextInt(parts[1].size)],
                parts[2][rng.nextInt(parts[2].size)],
            ).joinToString(" ")
            val galleryCount = 3 + rng.nextInt(4)
            val gallery = List(galleryCount) { gi ->
                ImageSource.Procedural(
                    seed = seed + gi * 17L,
                    aspectRatio = 1f,
                )
            }
            val bullets = List(5 + rng.nextInt(4)) { PRODUCT_BULLETS[rng.nextInt(PRODUCT_BULLETS.size)] }
                .distinct()
            val specifications: List<Pair<String, String>> = listOf(
                "Brand" to listOf("Apex", "Hearth", "North", "Lumen", "Granite", "Field")[rng.nextInt(6)],
                "Model" to "M-${1000 + rng.nextInt(8999)}",
                "Weight" to "${(0.4 + rng.nextDouble() * 4.6).format1()} kg",
                "Dimensions" to "${10 + rng.nextInt(40)} × ${10 + rng.nextInt(30)} × ${5 + rng.nextInt(20)} cm",
                "Warranty" to "${1 + rng.nextInt(5)} year limited",
                "Country of Origin" to listOf("USA", "Vietnam", "China", "Germany", "Mexico", "Portugal")[rng.nextInt(6)],
            )
            val priceCents = 999L + (rng.nextInt(50_000)).toLong()
            val originalPrice = if (rng.nextInt(3) == 0) priceCents + 500L + rng.nextInt(8_000) else null
            Product(
                id = "${category.id}_$idx",
                categoryId = category.id,
                title = title,
                priceCents = priceCents,
                rating = (3.6 + rng.nextDouble() * 1.4).format1().toDouble(),
                reviewCount = 12 + rng.nextInt(24_000),
                description = PRODUCT_DESCRIPTIONS[rng.nextInt(PRODUCT_DESCRIPTIONS.size)],
                thumbnail = gallery.firstOrNull(),
                gallery = gallery,
                bullets = bullets,
                specifications = specifications,
                deliveryEta = listOf(
                    "Delivery tomorrow",
                    "Delivery in 2 days",
                    "Delivery by Friday",
                    "Delivery in 3-5 days",
                )[rng.nextInt(4)],
                isPrime = rng.nextInt(3) > 0,
                originalPriceCents = originalPrice,
                brand = specifications.first { it.first == "Brand" }.second,
            )
        }
    }

private fun Double.format1(): String = "%.1f".format(this)
