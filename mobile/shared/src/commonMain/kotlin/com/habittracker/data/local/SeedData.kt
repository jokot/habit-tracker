package com.habittracker.data.local

import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.WantActivity

object SeedData {

    val identities: List<Identity> = listOf(
        Identity("00000000-0000-0000-0000-000000000001", "Reader", "Build a reading habit to expand knowledge and vocabulary", "📚"),
        Identity("00000000-0000-0000-0000-000000000002", "Builder", "Develop your craft as a software developer", "🔨"),
        Identity("00000000-0000-0000-0000-000000000003", "Athlete", "Build physical strength and endurance", "🏃"),
        Identity("00000000-0000-0000-0000-000000000004", "Writer", "Express yourself through consistent writing practice", "✍️"),
        Identity("00000000-0000-0000-0000-000000000005", "Learner", "Stay curious and keep learning every day", "🎓"),
        Identity("00000000-0000-0000-0000-000000000006", "Minimalist", "Simplify your space and digital life", "🌿"),
        Identity("00000000-0000-0000-0000-000000000007", "Devotee", "Deepen your spiritual practice", "🙏"),
        Identity("00000000-0000-0000-0000-000000000008", "Health-Conscious", "Build healthy daily habits for long-term wellness", "💪"),
    )

    val habitTemplates: Map<String, HabitTemplate> = listOf(
        HabitTemplate("10000000-0000-0000-0000-000000000001", "Read book / Kindle", "pages", 3.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000002", "Read article", "minutes", 5.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000003", "Read research paper", "minutes", 10.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000004", "Code project", "minutes", 15.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000005", "Write tests", "minutes", 10.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000006", "Learn new tech", "minutes", 15.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000007", "Review / refactor code", "minutes", 10.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000008", "Push up", "reps", 15.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000009", "Squat", "reps", 20.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000010", "Walk / run", "minutes", 10.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000011", "Cycling", "minutes", 10.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000012", "Stretching", "minutes", 5.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000013", "Plank", "seconds", 30.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000014", "Journaling", "minutes", 5.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000015", "Blog writing", "minutes", 15.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000016", "Creative writing", "minutes", 10.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000017", "Outline / draft", "minutes", 10.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000018", "Watch educational video", "minutes", 10.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000019", "Take online course", "minutes", 15.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000020", "Practice language", "minutes", 10.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000021", "Flashcard review", "minutes", 5.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000022", "Declutter space", "minutes", 5.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000023", "Organize items", "minutes", 5.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000024", "Digital cleanup", "minutes", 5.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000025", "Pray", "sessions", 1.0, 3),
        HabitTemplate("10000000-0000-0000-0000-000000000026", "Meditate", "minutes", 5.0, 2),
        HabitTemplate("10000000-0000-0000-0000-000000000027", "Gratitude journal", "entries", 3.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000028", "Drink water", "ml", 250.0, 8),
        HabitTemplate("10000000-0000-0000-0000-000000000029", "Sleep on time", "nights", 1.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000030", "Meal prep", "minutes", 10.0, 1),
        HabitTemplate("10000000-0000-0000-0000-000000000031", "No junk food day", "days", 1.0, 1),
    ).associateBy { it.id }

    val identityHabitMap: Map<String, List<String>> = mapOf(
        "00000000-0000-0000-0000-000000000001" to listOf("10000000-0000-0000-0000-000000000001", "10000000-0000-0000-0000-000000000002", "10000000-0000-0000-0000-000000000003"),
        "00000000-0000-0000-0000-000000000002" to listOf("10000000-0000-0000-0000-000000000001", "10000000-0000-0000-0000-000000000004", "10000000-0000-0000-0000-000000000005", "10000000-0000-0000-0000-000000000006", "10000000-0000-0000-0000-000000000007"),
        "00000000-0000-0000-0000-000000000003" to listOf("10000000-0000-0000-0000-000000000008", "10000000-0000-0000-0000-000000000009", "10000000-0000-0000-0000-000000000010", "10000000-0000-0000-0000-000000000011", "10000000-0000-0000-0000-000000000012", "10000000-0000-0000-0000-000000000013"),
        "00000000-0000-0000-0000-000000000004" to listOf("10000000-0000-0000-0000-000000000014", "10000000-0000-0000-0000-000000000015", "10000000-0000-0000-0000-000000000016", "10000000-0000-0000-0000-000000000017"),
        "00000000-0000-0000-0000-000000000005" to listOf("10000000-0000-0000-0000-000000000001", "10000000-0000-0000-0000-000000000002", "10000000-0000-0000-0000-000000000018", "10000000-0000-0000-0000-000000000019", "10000000-0000-0000-0000-000000000020", "10000000-0000-0000-0000-000000000021"),
        "00000000-0000-0000-0000-000000000006" to listOf("10000000-0000-0000-0000-000000000022", "10000000-0000-0000-0000-000000000023", "10000000-0000-0000-0000-000000000024"),
        "00000000-0000-0000-0000-000000000007" to listOf("10000000-0000-0000-0000-000000000025", "10000000-0000-0000-0000-000000000026", "10000000-0000-0000-0000-000000000027"),
        "00000000-0000-0000-0000-000000000008" to listOf("10000000-0000-0000-0000-000000000028", "10000000-0000-0000-0000-000000000029", "10000000-0000-0000-0000-000000000030", "10000000-0000-0000-0000-000000000031"),
    )

    val wantActivities: List<WantActivity> = listOf(
        WantActivity("20000000-0000-0000-0000-000000000001", "Scroll (reel/TikTok/short)", "minutes", 1.0),
        WantActivity("20000000-0000-0000-0000-000000000002", "Browse Twitter/X", "minutes", 0.5),
        WantActivity("20000000-0000-0000-0000-000000000003", "Browse Instagram feed", "minutes", 0.5),
        WantActivity("20000000-0000-0000-0000-000000000004", "YouTube long-form", "minutes", 0.1),
        WantActivity("20000000-0000-0000-0000-000000000005", "YouTube shorts", "minutes", 1.0),
        WantActivity("20000000-0000-0000-0000-000000000006", "Netflix / streaming", "minutes", 0.067),
        WantActivity("20000000-0000-0000-0000-000000000007", "Casual mobile game", "minutes", 0.2),
        WantActivity("20000000-0000-0000-0000-000000000008", "Valorant Deathmatch", "matches", 1.0),
        WantActivity("20000000-0000-0000-0000-000000000009", "Valorant Ranked", "matches", 3.0),
        WantActivity("20000000-0000-0000-0000-000000000010", "PC gaming session", "minutes", 0.1),
        WantActivity("20000000-0000-0000-0000-000000000011", "Online shopping browse", "minutes", 0.2),
        WantActivity("20000000-0000-0000-0000-000000000012", "Purchase session", "sessions", 2.0),
        WantActivity("20000000-0000-0000-0000-000000000013", "Junk food / fast food", "meals", 2.0),
        WantActivity("20000000-0000-0000-0000-000000000014", "Sugary drinks", "drinks", 1.0),
        WantActivity("20000000-0000-0000-0000-000000000015", "Donut / dessert", "pieces", 1.0),
    )
}
