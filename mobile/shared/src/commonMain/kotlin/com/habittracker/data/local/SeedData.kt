package com.habittracker.data.local

import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.WantActivity

object SeedData {

    val identities: List<Identity> = listOf(
        Identity("00000000-0000-0000-0000-000000000001", "Reader",           "Build a reading habit to expand knowledge and vocabulary.",  icon = "menu_book",        hue = 30),
        Identity("00000000-0000-0000-0000-000000000002", "Builder",          "Develop your craft as a software developer.",                icon = "code",             hue = 225),
        Identity("00000000-0000-0000-0000-000000000003", "Athlete",          "Build physical strength and endurance.",                     icon = "directions_run",   hue = 5),
        Identity("00000000-0000-0000-0000-000000000004", "Writer",           "Express yourself through consistent writing practice.",      icon = "edit_note",        hue = 285),
        Identity("00000000-0000-0000-0000-000000000005", "Learner",          "Stay curious and keep learning every day.",                  icon = "school",           hue = 190),
        Identity("00000000-0000-0000-0000-000000000006", "Minimalist",       "Simplify your space and digital life.",                      icon = "eco",              hue = 130),
        Identity("00000000-0000-0000-0000-000000000007", "Devotee",          "Deepen your spiritual practice.",                            icon = "self_improvement", hue = 255),
        Identity("00000000-0000-0000-0000-000000000008", "Health-Conscious", "Build healthy daily habits for long-term wellness.",         icon = "favorite",         hue = 155),
        Identity("00000000-0000-0000-0000-000000000009", "Creator",          "Make things — visual, audio, or written art.",               icon = "palette",          hue = 315),
        Identity("00000000-0000-0000-0000-000000000010", "Saver",            "Build wealth and break expensive habits.",                   icon = "savings",          hue = 75),
        Identity("00000000-0000-0000-0000-000000000011", "Connector",        "Invest in people who matter.",                               icon = "handshake",        hue = 350),
        Identity("00000000-0000-0000-0000-000000000012", "Adventurer",       "Try new things on purpose.",                                 icon = "explore",          hue = 170),
        Identity("00000000-0000-0000-0000-000000000013", "Chef",             "Cook your way to better food and lower cost.",               icon = "restaurant",       hue = 45),
    )

    val habitTemplates: Map<String, HabitTemplate> = listOf(
        // reader
        HabitTemplate("10000000-0000-0000-0000-000000000001", "Read book",            "pages", 3.0,  3, iconName = "menu_book"),
        HabitTemplate("10000000-0000-0000-0000-000000000002", "Read on Kindle",       "min",   10.0, 2, iconName = "menu_book"),
        HabitTemplate("10000000-0000-0000-0000-000000000003", "Read article",         "min",   5.0,  2, iconName = "article"),
        HabitTemplate("10000000-0000-0000-0000-000000000004", "Read research paper",  "min",   10.0, 1, iconName = "description"),
        HabitTemplate("10000000-0000-0000-0000-000000000005", "Audiobook listen",     "min",   15.0, 2, iconName = "headphones"),
        HabitTemplate("10000000-0000-0000-0000-000000000006", "Re-read passage",      "min",   5.0,  1, iconName = "menu_book"),
        // builder
        HabitTemplate("10000000-0000-0000-0000-000000000007", "Code project",        "min", 15.0, 3, iconName = "code"),
        HabitTemplate("10000000-0000-0000-0000-000000000008", "Write tests",         "min", 10.0, 2, iconName = "bug_report"),
        HabitTemplate("10000000-0000-0000-0000-000000000009", "Review code",         "min", 10.0, 1, iconName = "rate_review"),
        HabitTemplate("10000000-0000-0000-0000-000000000010", "Refactor code",       "min", 10.0, 1, iconName = "code"),
        HabitTemplate("10000000-0000-0000-0000-000000000011", "Read documentation",  "min", 10.0, 2, iconName = "menu_book"),
        HabitTemplate("10000000-0000-0000-0000-000000000012", "Pair programming",    "min", 30.0, 1, iconName = "groups"),
        HabitTemplate("10000000-0000-0000-0000-000000000013", "OSS contribution",    "min", 30.0, 1, iconName = "code"),
        HabitTemplate("10000000-0000-0000-0000-000000000014", "Solve coding puzzle", "puzzles", 1.0, 2, iconName = "extension"),
        // athlete
        HabitTemplate("10000000-0000-0000-0000-000000000015", "Push up",      "reps", 15.0, 3, iconName = "fitness_center"),
        HabitTemplate("10000000-0000-0000-0000-000000000016", "Squat",        "reps", 20.0, 3, iconName = "fitness_center"),
        HabitTemplate("10000000-0000-0000-0000-000000000017", "Pull up",      "reps", 5.0,  3, iconName = "fitness_center"),
        HabitTemplate("10000000-0000-0000-0000-000000000018", "Sit up",       "reps", 20.0, 2, iconName = "fitness_center"),
        HabitTemplate("10000000-0000-0000-0000-000000000019", "Walk",         "min",  10.0, 2, iconName = "directions_walk"),
        HabitTemplate("10000000-0000-0000-0000-000000000020", "Run",          "min",  10.0, 2, iconName = "directions_run"),
        HabitTemplate("10000000-0000-0000-0000-000000000021", "Cycling",      "min",  10.0, 2, iconName = "directions_bike"),
        HabitTemplate("10000000-0000-0000-0000-000000000022", "Swim",         "min",  10.0, 2, iconName = "pool"),
        HabitTemplate("10000000-0000-0000-0000-000000000023", "Stretching",   "min",  5.0,  2, iconName = "accessibility_new"),
        HabitTemplate("10000000-0000-0000-0000-000000000024", "Plank",        "sec",  30.0, 3, iconName = "timer"),
        HabitTemplate("10000000-0000-0000-0000-000000000025", "Yoga session", "min",  20.0, 1, iconName = "self_improvement"),
        HabitTemplate("10000000-0000-0000-0000-000000000026", "HIIT session", "min",  15.0, 1, iconName = "whatshot"),
        // writer
        HabitTemplate("10000000-0000-0000-0000-000000000027", "Journaling",       "min",   5.0,  2, iconName = "edit_note"),
        HabitTemplate("10000000-0000-0000-0000-000000000028", "Blog writing",     "min",   15.0, 2, iconName = "rss_feed"),
        HabitTemplate("10000000-0000-0000-0000-000000000029", "Creative writing", "min",   10.0, 2, iconName = "create"),
        HabitTemplate("10000000-0000-0000-0000-000000000030", "Outline",          "min",   10.0, 1, iconName = "description"),
        HabitTemplate("10000000-0000-0000-0000-000000000031", "Draft",            "min",   10.0, 1, iconName = "edit"),
        HabitTemplate("10000000-0000-0000-0000-000000000032", "Edit / proofread", "min",   10.0, 1, iconName = "spellcheck"),
        HabitTemplate("10000000-0000-0000-0000-000000000033", "Morning pages",    "pages", 3.0,  1, iconName = "edit_note"),
        HabitTemplate("10000000-0000-0000-0000-000000000034", "Newsletter",       "min",   30.0, 1, iconName = "email"),
        // learner
        HabitTemplate("10000000-0000-0000-0000-000000000035", "Watch educational video", "min",      10.0, 2, iconName = "smart_display"),
        HabitTemplate("10000000-0000-0000-0000-000000000036", "Take online course",      "min",      15.0, 2, iconName = "cast"),
        HabitTemplate("10000000-0000-0000-0000-000000000037", "Practice language",       "min",      10.0, 2, iconName = "language"),
        HabitTemplate("10000000-0000-0000-0000-000000000038", "Flashcard review",        "cards",    5.0,  3, iconName = "style"),
        HabitTemplate("10000000-0000-0000-0000-000000000039", "Listen to podcast",       "min",      20.0, 2, iconName = "podcasts"),
        HabitTemplate("10000000-0000-0000-0000-000000000040", "Take notes",              "min",      5.0,  2, iconName = "edit_note"),
        HabitTemplate("10000000-0000-0000-0000-000000000041", "Solve practice problem",  "problems", 1.0,  3, iconName = "calculate"),
        HabitTemplate("10000000-0000-0000-0000-000000000042", "Watch documentary",       "min",      30.0, 1, iconName = "movie"),
        // minimalist
        HabitTemplate("10000000-0000-0000-0000-000000000043", "Declutter space",   "min",   5.0, 1, iconName = "cleaning_services"),
        HabitTemplate("10000000-0000-0000-0000-000000000044", "Organize items",    "min",   5.0, 1, iconName = "inventory"),
        HabitTemplate("10000000-0000-0000-0000-000000000045", "Digital cleanup",   "min",   5.0, 1, iconName = "delete_sweep"),
        HabitTemplate("10000000-0000-0000-0000-000000000046", "Donate item",       "items", 1.0, 1, iconName = "volunteer_activism"),
        HabitTemplate("10000000-0000-0000-0000-000000000047", "Inbox zero",        "min",   5.0, 1, iconName = "inbox"),
        HabitTemplate("10000000-0000-0000-0000-000000000048", "Unsubscribe email", "items", 1.0, 3, iconName = "unsubscribe"),
        HabitTemplate("10000000-0000-0000-0000-000000000049", "One-in-one-out",    "items", 1.0, 1, iconName = "swap_horiz"),
        // devotee
        HabitTemplate("10000000-0000-0000-0000-000000000050", "Pray",              "sessions", 1.0,  3, iconName = "self_improvement"),
        HabitTemplate("10000000-0000-0000-0000-000000000051", "Meditate",          "min",      5.0,  2, iconName = "self_improvement"),
        HabitTemplate("10000000-0000-0000-0000-000000000052", "Gratitude journal", "entries",  3.0,  1, iconName = "spa"),
        HabitTemplate("10000000-0000-0000-0000-000000000053", "Read scripture",    "min",      10.0, 1, iconName = "menu_book"),
        HabitTemplate("10000000-0000-0000-0000-000000000054", "Reflection",        "min",      5.0,  2, iconName = "psychology"),
        HabitTemplate("10000000-0000-0000-0000-000000000055", "Acts of service",   "acts",     1.0,  1, iconName = "volunteer_activism"),
        HabitTemplate("10000000-0000-0000-0000-000000000056", "Sermon / lecture",  "min",      20.0, 1, iconName = "headphones"),
        // health
        HabitTemplate("10000000-0000-0000-0000-000000000057", "Drink water",      "ml",       250.0, 8, iconName = "water_drop"),
        HabitTemplate("10000000-0000-0000-0000-000000000058", "Sleep on time",    "nights",   1.0,   1, iconName = "bedtime"),
        HabitTemplate("10000000-0000-0000-0000-000000000059", "Meal prep",        "min",      10.0,  1, iconName = "kitchen"),
        HabitTemplate("10000000-0000-0000-0000-000000000060", "No junk food day", "days",     1.0,   1, iconName = "no_food"),
        HabitTemplate("10000000-0000-0000-0000-000000000061", "Take vitamins",    "times",    1.0,   1, iconName = "medication"),
        HabitTemplate("10000000-0000-0000-0000-000000000062", "Eat vegetables",   "servings", 1.0,   3, iconName = "eco"),
        HabitTemplate("10000000-0000-0000-0000-000000000063", "Walk after meal",  "min",      10.0,  2, iconName = "directions_walk"),
        HabitTemplate("10000000-0000-0000-0000-000000000064", "Track meals",      "meals",    1.0,   3, iconName = "restaurant"),
        // creator
        HabitTemplate("10000000-0000-0000-0000-000000000065", "Sketch / draw",  "min",    15.0, 1, iconName = "brush"),
        HabitTemplate("10000000-0000-0000-0000-000000000066", "Music practice", "min",    15.0, 2, iconName = "music_note"),
        HabitTemplate("10000000-0000-0000-0000-000000000067", "Photography",    "photos", 1.0,  5, iconName = "photo_camera"),
        HabitTemplate("10000000-0000-0000-0000-000000000068", "Edit creation",  "min",    15.0, 1, iconName = "edit"),
        HabitTemplate("10000000-0000-0000-0000-000000000069", "Share work",     "posts",  1.0,  1, iconName = "share"),
        // saver
        HabitTemplate("10000000-0000-0000-0000-000000000070", "No-spend day",        "days",  1.0, 1, iconName = "block"),
        HabitTemplate("10000000-0000-0000-0000-000000000071", "Track expenses",      "min",   5.0, 1, iconName = "account_balance"),
        HabitTemplate("10000000-0000-0000-0000-000000000072", "Cook at home",        "meals", 1.0, 2, iconName = "kitchen"),
        HabitTemplate("10000000-0000-0000-0000-000000000073", "Compare prices",      "min",   5.0, 1, iconName = "price_check"),
        HabitTemplate("10000000-0000-0000-0000-000000000074", "Cancel subscription", "items", 1.0, 1, iconName = "money_off"),
        // connector
        HabitTemplate("10000000-0000-0000-0000-000000000075", "Call family",    "calls",         1.0,  1, iconName = "phone"),
        HabitTemplate("10000000-0000-0000-0000-000000000076", "Message friend", "messages",      1.0,  3, iconName = "chat"),
        HabitTemplate("10000000-0000-0000-0000-000000000077", "Plan meetup",    "min",           10.0, 1, iconName = "event_note"),
        HabitTemplate("10000000-0000-0000-0000-000000000078", "Send thank-you", "notes",         1.0,  1, iconName = "mail_outline"),
        HabitTemplate("10000000-0000-0000-0000-000000000079", "Active listen",  "conversations", 1.0,  1, iconName = "hearing"),
        // adventurer
        HabitTemplate("10000000-0000-0000-0000-000000000080", "New route walk",  "walks",  1.0,  1, iconName = "explore"),
        HabitTemplate("10000000-0000-0000-0000-000000000081", "Try new food",    "meals",  1.0,  1, iconName = "restaurant"),
        HabitTemplate("10000000-0000-0000-0000-000000000082", "Visit new place", "places", 1.0,  1, iconName = "place"),
        HabitTemplate("10000000-0000-0000-0000-000000000083", "Learn new skill", "min",    15.0, 1, iconName = "lightbulb"),
        HabitTemplate("10000000-0000-0000-0000-000000000084", "Outdoor time",    "min",    30.0, 1, iconName = "park"),
        // chef
        HabitTemplate("10000000-0000-0000-0000-000000000085", "Cook from scratch",           "meals",   1.0,  2, iconName = "kitchen"),
        HabitTemplate("10000000-0000-0000-0000-000000000086", "Try new recipe",              "recipes", 1.0,  1, iconName = "menu_book"),
        HabitTemplate("10000000-0000-0000-0000-000000000087", "Meal prep batch",             "min",     30.0, 1, iconName = "kitchen"),
        HabitTemplate("10000000-0000-0000-0000-000000000088", "Use produce before spoiling", "items",   1.0,  3, iconName = "eco"),
    ).associateBy { it.id }

    val identityHabitMap: Map<String, List<String>> = mapOf(
        // Reader (01) — own 6 templates
        "00000000-0000-0000-0000-000000000001" to listOf(
            "10000000-0000-0000-0000-000000000001",  // Read book
            "10000000-0000-0000-0000-000000000002",  // Read on Kindle
            "10000000-0000-0000-0000-000000000003",  // Read article (alsoFor learner)
            "10000000-0000-0000-0000-000000000004",  // Read research paper
            "10000000-0000-0000-0000-000000000005",  // Audiobook listen
            "10000000-0000-0000-0000-000000000006",  // Re-read passage
        ),
        // Builder (02) — own 8
        "00000000-0000-0000-0000-000000000002" to listOf(
            "10000000-0000-0000-0000-000000000007",
            "10000000-0000-0000-0000-000000000008",
            "10000000-0000-0000-0000-000000000009",
            "10000000-0000-0000-0000-000000000010",
            "10000000-0000-0000-0000-000000000011",
            "10000000-0000-0000-0000-000000000012",
            "10000000-0000-0000-0000-000000000013",
            "10000000-0000-0000-0000-000000000014",
        ),
        // Athlete (03) — own 12 + Walk-after-meal (63, alsoFor athlete) from health bucket
        "00000000-0000-0000-0000-000000000003" to listOf(
            "10000000-0000-0000-0000-000000000015",
            "10000000-0000-0000-0000-000000000016",
            "10000000-0000-0000-0000-000000000017",
            "10000000-0000-0000-0000-000000000018",
            "10000000-0000-0000-0000-000000000019",  // Walk (alsoFor health)
            "10000000-0000-0000-0000-000000000020",
            "10000000-0000-0000-0000-000000000021",
            "10000000-0000-0000-0000-000000000022",
            "10000000-0000-0000-0000-000000000023",
            "10000000-0000-0000-0000-000000000024",
            "10000000-0000-0000-0000-000000000025",
            "10000000-0000-0000-0000-000000000026",
            "10000000-0000-0000-0000-000000000063",  // Walk after meal (alsoFor athlete, primary health)
        ),
        // Writer (04)
        "00000000-0000-0000-0000-000000000004" to listOf(
            "10000000-0000-0000-0000-000000000027",
            "10000000-0000-0000-0000-000000000028",
            "10000000-0000-0000-0000-000000000029",
            "10000000-0000-0000-0000-000000000030",
            "10000000-0000-0000-0000-000000000031",
            "10000000-0000-0000-0000-000000000032",
            "10000000-0000-0000-0000-000000000033",
            "10000000-0000-0000-0000-000000000034",
        ),
        // Learner (05) — own 8 + Read-article (03) shared from reader
        "00000000-0000-0000-0000-000000000005" to listOf(
            "10000000-0000-0000-0000-000000000003",  // Read article (alsoFor learner)
            "10000000-0000-0000-0000-000000000035",
            "10000000-0000-0000-0000-000000000036",
            "10000000-0000-0000-0000-000000000037",
            "10000000-0000-0000-0000-000000000038",
            "10000000-0000-0000-0000-000000000039",
            "10000000-0000-0000-0000-000000000040",
            "10000000-0000-0000-0000-000000000041",
            "10000000-0000-0000-0000-000000000042",
        ),
        // Minimalist (06)
        "00000000-0000-0000-0000-000000000006" to listOf(
            "10000000-0000-0000-0000-000000000043",
            "10000000-0000-0000-0000-000000000044",
            "10000000-0000-0000-0000-000000000045",
            "10000000-0000-0000-0000-000000000046",
            "10000000-0000-0000-0000-000000000047",
            "10000000-0000-0000-0000-000000000048",
            "10000000-0000-0000-0000-000000000049",
        ),
        // Devotee (07)
        "00000000-0000-0000-0000-000000000007" to listOf(
            "10000000-0000-0000-0000-000000000050",
            "10000000-0000-0000-0000-000000000051",
            "10000000-0000-0000-0000-000000000052",
            "10000000-0000-0000-0000-000000000053",
            "10000000-0000-0000-0000-000000000054",
            "10000000-0000-0000-0000-000000000055",
            "10000000-0000-0000-0000-000000000056",
        ),
        // Health-Conscious (08) — own 8 + Walk (19, alsoFor health) from athlete
        "00000000-0000-0000-0000-000000000008" to listOf(
            "10000000-0000-0000-0000-000000000019",  // Walk (alsoFor health, primary athlete)
            "10000000-0000-0000-0000-000000000057",
            "10000000-0000-0000-0000-000000000058",
            "10000000-0000-0000-0000-000000000059",
            "10000000-0000-0000-0000-000000000060",
            "10000000-0000-0000-0000-000000000061",
            "10000000-0000-0000-0000-000000000062",
            "10000000-0000-0000-0000-000000000063",
            "10000000-0000-0000-0000-000000000064",
        ),
        // Creator (09)
        "00000000-0000-0000-0000-000000000009" to listOf(
            "10000000-0000-0000-0000-000000000065",
            "10000000-0000-0000-0000-000000000066",
            "10000000-0000-0000-0000-000000000067",
            "10000000-0000-0000-0000-000000000068",
            "10000000-0000-0000-0000-000000000069",
        ),
        // Saver (10) — own 5 + Meal prep (59, alsoFor saver)
        "00000000-0000-0000-0000-000000000010" to listOf(
            "10000000-0000-0000-0000-000000000059",  // Meal prep (alsoFor saver, primary health)
            "10000000-0000-0000-0000-000000000070",
            "10000000-0000-0000-0000-000000000071",
            "10000000-0000-0000-0000-000000000072",
            "10000000-0000-0000-0000-000000000073",
            "10000000-0000-0000-0000-000000000074",
        ),
        // Connector (11)
        "00000000-0000-0000-0000-000000000011" to listOf(
            "10000000-0000-0000-0000-000000000075",
            "10000000-0000-0000-0000-000000000076",
            "10000000-0000-0000-0000-000000000077",
            "10000000-0000-0000-0000-000000000078",
            "10000000-0000-0000-0000-000000000079",
        ),
        // Adventurer (12)
        "00000000-0000-0000-0000-000000000012" to listOf(
            "10000000-0000-0000-0000-000000000080",
            "10000000-0000-0000-0000-000000000081",
            "10000000-0000-0000-0000-000000000082",
            "10000000-0000-0000-0000-000000000083",
            "10000000-0000-0000-0000-000000000084",
        ),
        // Chef (13) — own 4 + Meal prep (59, alsoFor chef)
        "00000000-0000-0000-0000-000000000013" to listOf(
            "10000000-0000-0000-0000-000000000059",  // Meal prep (alsoFor chef)
            "10000000-0000-0000-0000-000000000085",
            "10000000-0000-0000-0000-000000000086",
            "10000000-0000-0000-0000-000000000087",
            "10000000-0000-0000-0000-000000000088",
        ),
    )

    val wantActivities: List<WantActivity> = listOf(
        WantActivity("20000000-0000-0000-0000-000000000001", "TikTok",          "min",     unitsPerPoint =  1, iconKey = "play_circle"),
        WantActivity("20000000-0000-0000-0000-000000000002", "YouTube Shorts",  "min",     unitsPerPoint =  1, iconKey = "smart_display"),
        WantActivity("20000000-0000-0000-0000-000000000003", "YouTube",         "min",     unitsPerPoint = 10, iconKey = "smart_display"),
        WantActivity("20000000-0000-0000-0000-000000000004", "Netflix",         "min",     unitsPerPoint = 15, iconKey = "local_movies"),
        WantActivity("20000000-0000-0000-0000-000000000005", "Twitter/X",       "min",     unitsPerPoint =  2, iconKey = "chat_bubble"),
        WantActivity("20000000-0000-0000-0000-000000000006", "Instagram",       "min",     unitsPerPoint =  2, iconKey = "photo_camera"),
        WantActivity("20000000-0000-0000-0000-000000000007", "Reddit",          "min",     unitsPerPoint =  2, iconKey = "forum"),
        WantActivity("20000000-0000-0000-0000-000000000008", "Gaming",          "min",     unitsPerPoint = 10, iconKey = "sports_esports"),
        WantActivity("20000000-0000-0000-0000-000000000009", "Online shopping", "min",     unitsPerPoint =  5, iconKey = "shopping_bag"),
        WantActivity("20000000-0000-0000-0000-000000000010", "Junk food",       "meal",    unitsPerPoint =  1, iconKey = "restaurant"),
        WantActivity("20000000-0000-0000-0000-000000000011", "Snacks",          "serving", unitsPerPoint =  1, iconKey = "restaurant"),
        WantActivity("20000000-0000-0000-0000-000000000012", "Sweets",          "piece",   unitsPerPoint =  1, iconKey = "cake"),
        WantActivity("20000000-0000-0000-0000-000000000013", "Sugary drinks",   "drink",   unitsPerPoint =  1, iconKey = "local_drink"),
        WantActivity("20000000-0000-0000-0000-000000000014", "Coffee",          "cup",     unitsPerPoint =  1, iconKey = "local_cafe"),
    )
}
