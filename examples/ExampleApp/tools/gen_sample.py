#!/usr/bin/env python3
"""
Generates sample JSON assets for the ExampleApp paradigm sample app.
Run from the repo root or anywhere; OUT is hard-coded.
"""
import json
import os
import random
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "Users/hansonho/work/embrace-android-sdk/examples/ExampleApp/app/src/main/assets/sample"
# Override: be explicit
OUT = Path("/Users/hansonho/work/embrace-android-sdk/examples/ExampleApp/app/src/main/assets/sample")
OUT.mkdir(parents=True, exist_ok=True)


def write_json(name, data):
    target = OUT / name
    with target.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"Wrote {target} ({len(data) if isinstance(data, list) else 'n/a'} entries, {target.stat().st_size} bytes)")


# ---------- AUTHORS ----------
AUTHOR_SEEDS = [
    ("ada", "Ada Lovelace", "Notes on the Analytical Engine. Mostly correct."),
    ("linus", "Linus T.", "Kernel hacker. Opinions own, code GPL."),
    ("grace", "Grace Hopper", "Compilers. Nanoseconds. Ships."),
    ("alan", "Alan Turing", "Asking machines awkward questions."),
    ("margaret", "Margaret H.", "Apollo. Software engineering, the term."),
    ("bjarne", "Bjarne S.", "There are only two kinds of languages."),
    ("rich", "Rich H.", "Simple. Made. Easy."),
    ("jeff", "Jeff D.", "Distributed systems are mostly clocks."),
    ("sandi", "Sandi M.", "Practical object-oriented design."),
    ("kelsey", "Kelsey H.", "Production-grade, hand-rolled."),
    ("mira", "Mira K.", "Embedded ML and napping."),
    ("priya", "Priya R.", "Type theory in the wild."),
    ("satoshi", "Satoshi N.", "Idle hashes, mostly."),
    ("nina", "Nina A.", "Compiler internals, perf, opera."),
    ("oren", "Oren D.", "Systems. Disks. Birds."),
    ("yuki", "Yuki S.", "Distributed databases and matcha."),
    ("renee", "Renée P.", "Static analysis, garden tools."),
    ("hansel", "Hansel K.", "Async runtimes for breakfast."),
    ("brigitte", "Brigitte L.", "Real-time graphics; old games."),
    ("oluwa", "Oluwafemi O.", "ASTs and arepas."),
]
LOCATIONS = ["Brooklyn", "Berlin", "Bangalore", "Toronto", "Lagos", "Tokyo", "Lisbon"]
JOIN_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun"]

authors = []
for i, (handle, name, bio) in enumerate(AUTHOR_SEEDS):
    authors.append({
        "handle": handle,
        "displayName": name,
        "bio": bio,
        "followerCount": 1000 + (i * 4113) % 980000,
        "followingCount": 12 + (i * 31) % 480,
        "avatar": {"type": "procedural", "seed": 50000 + i, "aspectRatio": 1.0},
        "coverImage": {"type": "procedural", "seed": 60000 + i, "aspectRatio": 3.0},
        "location": LOCATIONS[i % 7],
        "joinedLabel": f"Joined {JOIN_MONTHS[i % 6]} 20{(10 + i % 16):02d}",
        "isVerified": i % 3 == 0,
    })
write_json("authors.json", authors)

# ---------- POSTS ----------
POST_OPENERS = [
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
]

POST_BODIES = [
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
]

POST_HASHTAGS = ["#kotlin", "#android", "#architecture", "#observability", "#perf",
                 "#latency", "#refactor", "#systems", "#opentelemetry", "#oncall"]

posts = []
for i in range(240):
    rng = random.Random(1_000_000 + i)
    author = authors[i % len(authors)]
    n_openers = rng.randint(1, 3)
    openers = [POST_OPENERS[rng.randrange(len(POST_OPENERS))] for _ in range(n_openers)]
    n_bodies = rng.randint(2, 4)
    bodies = [POST_BODIES[rng.randrange(len(POST_BODIES))] for _ in range(n_bodies)]
    bodies_padded = bodies + bodies
    pairs = list(zip(openers, bodies_padded[:len(openers)]))
    combined = "\n\n".join(f"{op} {bd}." for op, bd in pairs)

    n_hashtags = rng.randint(0, 3)
    hashtags = list({POST_HASHTAGS[rng.randrange(len(POST_HASHTAGS))] for _ in range(n_hashtags)})

    n_mentions = rng.randint(0, 2)
    mention_pool = [a for a in authors if a["handle"] != author["handle"]]
    mentions = list({mention_pool[rng.randrange(len(mention_pool))]["handle"] for _ in range(n_mentions)})

    n = rng.randint(0, 9)
    if n <= 3:
        media_count = 0
    elif n <= 6:
        media_count = 1
    elif n <= 8:
        media_count = rng.randint(2, 3)
    else:
        media_count = 1

    media = []
    for mi in range(media_count):
        seed = 200000 + i * 7 + mi
        if mi == 0 and rng.randrange(8) == 0:
            media.append({
                "type": "video",
                "id": f"v_{i}_{mi}",
                "source": {
                    "type": "procedural",
                    "seed": seed,
                    "aspectRatio": 16.0 / 9.0,
                    "durationMs": 30000,
                },
            })
        else:
            aspects = [1.0, 0.8, 16.0 / 9.0, 0.75]
            media.append({
                "type": "image",
                "id": f"i_{i}_{mi}",
                "source": {
                    "type": "procedural",
                    "seed": seed,
                    "aspectRatio": aspects[mi % 4],
                },
            })

    body = combined + (("\n\n" + " ".join(hashtags)) if hashtags else "")
    posts.append({
        "id": f"p{i}",
        "authorHandle": author["handle"],
        "authorDisplayName": author["displayName"],
        "authorAvatar": author["avatar"],
        "body": body,
        "likeCount": 10 + rng.randint(0, 44999),
        "replyCount": rng.randint(0, 899),
        "repostCount": rng.randint(0, 7999),
        "media": media,
        "timestampLabel": ["now", "5m", "1h", "3h", "1d", "2d", "1w"][rng.randrange(7)],
        "isPinned": i == 0 or i % 47 == 0,
        "isVerified": author["isVerified"],
        "mentions": mentions,
        "hashtags": hashtags,
    })
write_json("posts.json", posts)

# ---------- SECTIONS ----------
sections = [
    {"id": "world", "title": "World", "accentSeed": 1001},
    {"id": "tech", "title": "Technology", "accentSeed": 1002},
    {"id": "business", "title": "Business", "accentSeed": 1003},
    {"id": "science", "title": "Science", "accentSeed": 1004},
    {"id": "opinion", "title": "Opinion", "accentSeed": 1005},
    {"id": "culture", "title": "Arts & Culture", "accentSeed": 1006},
    {"id": "sports", "title": "Sports", "accentSeed": 1007},
    {"id": "travel", "title": "Travel", "accentSeed": 1008},
]
write_json("sections.json", sections)

# ---------- ARTICLES ----------
ARTICLE_HEADLINE_PARTS = {
    "world": (
        ["Treaty", "Summit", "Agreement", "Crisis", "Diplomats", "Negotiators"],
        ["Reached", "Signed", "Stalled", "Reopened", "Confronted"],
        ["After Decade of Talks", "Despite Tensions", "Amid Protests", "in Late-Night Vote"],
    ),
    "tech": (
        ["On-Device AI", "Quantum Chips", "Open-Source Compilers", "Wearables", "Self-Driving"],
        ["Hit", "Reach", "Exceed", "Approach", "Quietly Pass"],
        ["New Memory Lows", "Daily Active Users", "Audit Milestones", "Power Targets"],
    ),
    "business": (
        ["Earnings", "Layoffs", "Mergers", "Spin-offs", "IPOs"],
        ["Beat", "Disappoint", "Surprise", "Match", "Exceed"],
        ["Estimates by 12%", "Analysts' Forecasts", "Last Year's Numbers"],
    ),
    "science": (
        ["Researchers", "Astronomers", "Biologists", "Physicists", "Engineers"],
        ["Find", "Confirm", "Suggest", "Observe", "Demonstrate"],
        ["New Phase Transition", "Habitable Zone Candidate", "CRISPR Variant", "Material Anomaly"],
    ),
    "opinion": (
        ["Why", "When", "How", "What If"],
        ["Navigation APIs", "Microservices", "Code Reviews", "Standups", "Configuration"],
        ["Keep Getting Reinvented", "Need a Rethink", "Should Be Smaller", "Are Mostly Politics"],
    ),
    "culture": (
        ["Festival", "Retrospective", "Album", "Novel", "Exhibition"],
        ["Opens", "Returns", "Closes", "Premieres", "Surprises"],
        ["To Mixed Reviews", "After Long Hiatus", "With Standing Ovation", "Quietly"],
    ),
    "sports": (
        ["Champions", "Underdogs", "Veterans", "Rookies"],
        ["Edge Out", "Dominate", "Stun", "Hold Off", "Outlast"],
        ["Rivals in Overtime", "Defending Title-holders", "Top Seed", "Visiting Squad"],
    ),
    "travel": (
        ["Hidden", "Crowded", "Quiet", "Remote", "Unlikely"],
        ["Cafés", "Hikes", "Train Routes", "Beaches", "Side Streets"],
        ["Of Lisbon", "Outside Kyoto", "Across Patagonia", "in Reykjavík"],
    ),
}

ARTICLE_PARAGRAPHS = [
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
]

ARTICLE_HTML_BLOCKS = [
    "<h2>Background</h2><p>The story behind the headline is, as ever, more complicated than the headline lets on. Several of the central facts have been disputed by the parties named in this account.</p>",
    "<blockquote>\"It was the simplest version of the proposal that anyone had any chance of accepting,\" one person familiar with the discussions said.</blockquote>",
    "<h2>What's next</h2><p>Implementation is expected to begin in the coming weeks, though no firm timeline has been published. Observers caution that previous timelines have slipped.</p>",
    "<ul><li>The agreement covers tariffs and labor mobility.</li><li>Ratification requires three of the five signatories.</li><li>The text becomes provisional after the first ratification.</li></ul>",
    "<h2>Methodology</h2><p>The reporting in this article draws on interviews with seven people directly involved in the discussions, four of whom requested anonymity to discuss matters that remain non-public.</p>",
]

WEB_HTML_TEMPLATE = """<html><head><style>
body {{ font-family: -apple-system, sans-serif; line-height: 1.6; color: #1a1a1a; padding: 16px; }}
h2 {{ font-size: 1.2em; margin-top: 1.4em; }}
blockquote {{ border-left: 3px solid #888; margin: 1em 0; padding-left: 12px; color: #555; font-style: italic; }}
ul {{ padding-left: 20px; }}
</style></head>
<body>
<p><strong>{headline}.</strong> {opener}</p>
{html_body}
<p><em>Reporting and analysis from our newsroom.</em></p>
</body></html>"""

BYLINES = ["M. Reporter", "J. Correspondent", "Staff", "A. Bureau Chief", "K. Stringer"]
PUBLISH_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

articles = []
for section in sections:
    parts = ARTICLE_HEADLINE_PARTS.get(section["id"], ARTICLE_HEADLINE_PARTS["opinion"])
    for idx in range(14):
        seed = section["accentSeed"] * 100 + idx
        rng = random.Random(seed)
        headline_words = [
            parts[0][rng.randrange(len(parts[0]))],
            parts[1][rng.randrange(len(parts[1]))],
            parts[2][rng.randrange(len(parts[2]))],
        ]
        headline = " ".join(headline_words)

        n_paragraphs = 6 + rng.randint(0, 7)
        paragraphs = [ARTICLE_PARAGRAPHS[rng.randrange(len(ARTICLE_PARAGRAPHS))] for _ in range(n_paragraphs)]

        n_html = 2 + rng.randint(0, 2)
        html_blocks = [ARTICLE_HTML_BLOCKS[rng.randrange(len(ARTICLE_HTML_BLOCKS))] for _ in range(n_html)]
        web_html = WEB_HTML_TEMPLATE.format(
            headline=headline,
            opener=paragraphs[0] if paragraphs else "",
            html_body="\n".join(html_blocks),
        )

        byline = "By " + BYLINES[rng.randrange(len(BYLINES))]
        publish_day = 1 + rng.randint(0, 27)
        publish_month = PUBLISH_MONTHS[rng.randrange(len(PUBLISH_MONTHS))]
        summary = (paragraphs[0][:160] if paragraphs else "") + "…"

        n_extra_tags = rng.randint(0, 2)
        extra_tags = [f"topic-{rng.randrange(20)}" for _ in range(n_extra_tags)]
        tags = [section["title"]] + extra_tags

        pull_quote = None
        if rng.randrange(3) == 0 and paragraphs:
            sentence = paragraphs[rng.randrange(len(paragraphs))].split(". ")[0]
            pull_quote = sentence

        article = {
            "id": f"{section['id']}_{idx}",
            "sectionId": section["id"],
            "headline": headline,
            "byline": byline,
            "summary": summary,
            "body": "\n\n".join(paragraphs),
            "heroImage": {"type": "procedural", "seed": seed * 13, "aspectRatio": 16.0 / 9.0},
            "paragraphs": paragraphs,
            "publishedAtIso": f"{publish_month} {publish_day}, 2026",
            "readTimeMinutes": 3 + rng.randint(0, 11),
            "tags": tags,
        }
        if rng.randrange(3) > 0:
            article["webContentHtml"] = web_html
        if pull_quote is not None:
            article["pullQuote"] = pull_quote
        articles.append(article)
write_json("articles.json", articles)

# ---------- CATEGORIES ----------
categories = [
    {"id": "electronics", "title": "Electronics", "accentSeed": 2001},
    {"id": "books", "title": "Books", "accentSeed": 2002},
    {"id": "kitchen", "title": "Kitchen", "accentSeed": 2003},
    {"id": "outdoors", "title": "Outdoors", "accentSeed": 2004},
    {"id": "home", "title": "Home & Garden", "accentSeed": 2005},
    {"id": "toys", "title": "Toys & Games", "accentSeed": 2006},
    {"id": "beauty", "title": "Beauty", "accentSeed": 2007},
    {"id": "office", "title": "Office", "accentSeed": 2008},
]
write_json("categories.json", categories)

# ---------- PRODUCTS ----------
PRODUCT_NAME_PARTS = {
    "electronics": (
        ["Wireless", "USB-C", "Bluetooth", "Mechanical", "RGB", "Gaming", "Compact", "Portable"],
        ["Hub", "Keyboard", "Mouse", "Headphones", "Speaker", "Charger", "Webcam", "Microphone"],
        ["Pro", "Mini", "X1", "Lite", "Plus", "Ultra"],
    ),
    "books": (
        ["Effective", "Concurrent", "Modern", "Pragmatic", "Functional", "Hands-On"],
        ["Kotlin", "Rust", "TypeScript", "Go", "Python", "Software Design"],
        ["for Engineers", "for Teams", "in Practice", "Second Edition", "Cookbook"],
    ),
    "kitchen": (
        ["Cast Iron", "Stainless", "Carbon Steel", "Copper", "Enameled"],
        ["Skillet", "Saucepan", "Dutch Oven", "Wok", "Stockpot", "Sauté Pan"],
        ["10\"", "12\"", "8 qt", "6 qt", "with Lid"],
    ),
    "outdoors": (
        ["Trail", "Alpine", "Backcountry", "Ultralight", "All-Weather"],
        ["Backpack", "Tent", "Sleeping Bag", "Trekking Poles", "Headlamp", "Stove"],
        ["65L", "2-Person", "0°F", "Carbon Fiber", "Compact"],
    ),
    "home": (
        ["Linen", "Organic Cotton", "Down", "Memory Foam", "Bamboo"],
        ["Sheet Set", "Comforter", "Pillow", "Throw Blanket", "Mattress Topper"],
        ["Queen", "King", "Twin", "Hypoallergenic"],
    ),
    "toys": (
        ["Wooden", "Educational", "STEM", "Sensory", "Cooperative"],
        ["Blocks", "Puzzle", "Board Game", "Marble Run", "Modeling Set"],
        ["Ages 5+", "Ages 8+", "Family Edition", "Classic"],
    ),
    "beauty": (
        ["Hydrating", "Vitamin C", "Retinol", "Niacinamide", "SPF 50"],
        ["Serum", "Moisturizer", "Cleanser", "Sunscreen", "Eye Cream"],
        ["30ml", "50ml", "Travel Size", "Refill"],
    ),
    "office": (
        ["Standing", "Ergonomic", "Adjustable", "Modular", "Compact"],
        ["Desk", "Chair", "Monitor Arm", "Footrest", "Lamp", "Organizer"],
        ["Walnut", "Black", "White", "L-Shape", "USB-C"],
    ),
}

PRODUCT_BULLETS = [
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
]

PRODUCT_DESCRIPTIONS = [
    "An everyday workhorse that punches above its price tag. After a few weeks you'll forget you bought it, in the best possible sense.",
    "Designed for the long haul. Materials, construction, and even the included accessories are picked to last beyond the warranty.",
    "If you've been meaning to replace the version you've had since 2019, this is the upgrade that justifies the upgrade.",
    "Quietly excellent. Doesn't shout about features; just does the thing it's supposed to do, all the time, without complaint.",
    "Built around the boring requirements that the cheaper versions tend to skip. Reviewers notice these things; you will too.",
]

BRANDS = ["Apex", "Hearth", "North", "Lumen", "Granite", "Field"]
DELIVERY_OPTIONS = ["Delivery tomorrow", "Delivery in 2 days", "Delivery by Friday", "Delivery in 3-5 days"]
COUNTRIES = ["USA", "Vietnam", "China", "Germany", "Mexico", "Portugal"]

products = []
for category in categories:
    parts = PRODUCT_NAME_PARTS[category["id"]]
    for idx in range(14):
        seed = category["accentSeed"] * 100 + idx
        rng = random.Random(seed)
        title_words = [
            parts[0][rng.randrange(len(parts[0]))],
            parts[1][rng.randrange(len(parts[1]))],
            parts[2][rng.randrange(len(parts[2]))],
        ]
        title = " ".join(title_words)

        gallery_count = 3 + rng.randint(0, 3)
        gallery = [
            {"type": "procedural", "seed": seed + gi * 17, "aspectRatio": 1.0}
            for gi in range(gallery_count)
        ]
        thumbnail = gallery[0] if gallery else None

        n_bullets = 5 + rng.randint(0, 3)
        bullets = list({PRODUCT_BULLETS[rng.randrange(len(PRODUCT_BULLETS))] for _ in range(n_bullets)})

        brand = BRANDS[rng.randrange(len(BRANDS))]
        weight = 0.4 + rng.random() * 4.6
        dim_w = 10 + rng.randrange(40)
        dim_d = 10 + rng.randrange(30)
        dim_h = 5 + rng.randrange(20)
        warranty = 1 + rng.randrange(5)
        country = COUNTRIES[rng.randrange(len(COUNTRIES))]
        specifications = [
            {"key": "Brand", "value": brand},
            {"key": "Model", "value": f"M-{1000 + rng.randrange(8999)}"},
            {"key": "Weight", "value": f"{weight:.1f} kg"},
            {"key": "Dimensions", "value": f"{dim_w} × {dim_d} × {dim_h} cm"},
            {"key": "Warranty", "value": f"{warranty} year limited"},
            {"key": "Country of Origin", "value": country},
        ]

        price_cents = 999 + rng.randrange(50000)
        rating = round(3.6 + rng.random() * 1.4, 1)
        delivery_eta = DELIVERY_OPTIONS[rng.randrange(len(DELIVERY_OPTIONS))]
        is_prime = rng.randrange(3) > 0

        product = {
            "id": f"{category['id']}_{idx}",
            "categoryId": category["id"],
            "title": title,
            "priceCents": price_cents,
            "rating": rating,
            "reviewCount": 12 + rng.randrange(24000),
            "description": PRODUCT_DESCRIPTIONS[rng.randrange(len(PRODUCT_DESCRIPTIONS))],
            "gallery": gallery,
            "bullets": bullets,
            "specifications": specifications,
            "deliveryEta": delivery_eta,
            "isPrime": is_prime,
            "brand": brand,
        }
        if thumbnail is not None:
            product["thumbnail"] = thumbnail
        if rng.randrange(3) == 0:
            product["originalPriceCents"] = price_cents + 500 + rng.randrange(8000)
        products.append(product)
write_json("products.json", products)

print(f"\nTotal: {len(authors)} authors, {len(posts)} posts, {len(sections)} sections, {len(articles)} articles, {len(categories)} categories, {len(products)} products")
