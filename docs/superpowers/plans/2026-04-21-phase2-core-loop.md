# Phase 2: Core Habit Loop (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete Android core habit loop — auth, onboarding, log need habits to earn points, log want activities to spend points, view point balance, with 5-minute undo.

**Architecture:** Shared KMP module holds all domain models, repository interfaces, use cases, and SQLDelight implementations. Android app holds ViewModels (AndroidX), Compose screens, navigation (NavHost), and manual DI via AppContainer. No iOS work this phase.

**Tech Stack:** Kotlin Multiplatform, Compose + Material 3, AndroidX Navigation, AndroidX ViewModel, SQLDelight 2.0.2, supabase-kt 3.0.2, kotlinx-datetime 0.6.1

---

## File Map

**Shared module — new files:**
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseClientFactory.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PointBalance.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitWithProgress.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogHabitUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UndoHabitLogUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UndoWantLogUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/IsOnboardedUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentityUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt`

**Shared module — delete:**
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseProvider.kt`

**Shared module — test files:**
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitLogRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantActivityRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantLogRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogHabitUseCaseTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt`

**Android app — new files:**
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/HabitTrackerApplication.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/AppContainer.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/navigation/AppNavigation.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantScreen.kt`

**Android app — modified:**
- `gradle/libs.versions.toml`
- `mobile/androidApp/build.gradle.kts`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/MainActivity.kt`
- `mobile/androidApp/src/androidMain/AndroidManifest.xml`

---

## Task 1: Gradle — Add Navigation, Lifecycle, BuildConfig

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/androidApp/build.gradle.kts`

- [ ] **Step 1: Add versions and library entries to libs.versions.toml**

Open `gradle/libs.versions.toml`. Add after the existing `activity-compose` version:

```toml
[versions]
kotlin = "2.1.0"
agp = "8.7.3"
sqldelight = "2.0.2"
ktor = "2.3.12"
supabase = "3.0.2"
coroutines = "1.9.0"
datetime = "0.6.1"
compose-bom = "2024.12.01"
activity-compose = "1.9.3"
lifecycle = "2.8.7"
navigation-compose = "2.8.5"
```

Then add library entries (append after existing entries in `[libraries]`):

```toml
lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation-compose" }
```

- [ ] **Step 2: Run test to verify libs.versions.toml parses**

```bash
cd /path/to/worktree  # .worktrees/phase2-core-loop
./gradlew help --quiet
```

Expected: BUILD SUCCESSFUL (no TOML parse errors)

- [ ] **Step 3: Update androidApp/build.gradle.kts**

Replace `mobile/androidApp/build.gradle.kts` entirely:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
}

android {
    namespace = "com.habittracker.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.habittracker.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anon_key", "")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":mobile:shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 4: Sync and verify build**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
rtk git add gradle/libs.versions.toml mobile/androidApp/build.gradle.kts
rtk git commit -m "feat: add navigation, lifecycle, buildConfig to androidApp gradle"
```

---

## Task 2: SupabaseClientFactory + SupabaseAuthRepository

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseClientFactory.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt`
- Delete: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseProvider.kt`

- [ ] **Step 1: Create SupabaseClientFactory.kt**

```kotlin
package com.habittracker.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientFactory {
    fun create(url: String, key: String): SupabaseClient =
        createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Auth)
            install(Postgrest)
        }
}
```

- [ ] **Step 2: Create SupabaseAuthRepository.kt**

```kotlin
package com.habittracker.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class SupabaseAuthRepository(
    private val client: SupabaseClient,
) : AuthRepository {

    override suspend fun signUp(email: String, password: String): Result<UserSession> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val user = client.auth.currentSessionOrNull()?.user
            ?: error("Sign up returned no session")
        UserSession(userId = user.id, email = user.email ?: email)
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val user = client.auth.currentSessionOrNull()?.user
            ?: error("Sign in returned no session")
        UserSession(userId = user.id, email = user.email ?: email)
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }

    override fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id

    override fun isLoggedIn(): Boolean =
        client.auth.currentSessionOrNull() != null
}
```

- [ ] **Step 3: Delete SupabaseProvider.kt**

```bash
rm mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseProvider.kt
```

- [ ] **Step 4: Build shared module to verify no broken references**

```bash
./gradlew :mobile:shared:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL (no references to SupabaseProvider anywhere)

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt
rtk git commit -m "feat: add SupabaseClientFactory and SupabaseAuthRepository, remove SupabaseProvider"
```

---

## Task 3: SeedData + Repository Interfaces + Local Implementations

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitLogRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt`

- [ ] **Step 1: Create SeedData.kt**

```kotlin
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
```

- [ ] **Step 2: Create IdentityRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Identity

interface IdentityRepository {
    suspend fun getAllIdentities(): List<Identity>
    suspend fun upsertIdentities(identities: List<Identity>)
}
```

- [ ] **Step 3: Create LocalIdentityRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.Identity

class LocalIdentityRepository(
    private val db: HabitTrackerDatabase,
) : IdentityRepository {

    override suspend fun getAllIdentities(): List<Identity> =
        db.habitTrackerDatabaseQueries
            .getAllIdentities()
            .executeAsList()
            .map { Identity(id = it.id, name = it.name, description = it.description, icon = it.icon) }

    override suspend fun upsertIdentities(identities: List<Identity>) {
        identities.forEach { identity ->
            db.habitTrackerDatabaseQueries.upsertIdentity(
                id = identity.id,
                name = identity.name,
                description = identity.description,
                icon = identity.icon,
            )
        }
    }
}
```

- [ ] **Step 4: Create HabitRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit

interface HabitRepository {
    suspend fun getHabitsForUser(userId: String): List<Habit>
    suspend fun saveHabit(habit: Habit)
    suspend fun deleteHabit(habitId: String, userId: String)
}
```

- [ ] **Step 5: Create LocalHabitRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalHabit
import com.habittracker.domain.model.Habit
import kotlinx.datetime.Instant

class LocalHabitRepository(
    private val db: HabitTrackerDatabase,
) : HabitRepository {

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun saveHabit(habit: Habit) {
        db.habitTrackerDatabaseQueries.upsertHabit(
            id = habit.id,
            userId = habit.userId,
            templateId = habit.templateId,
            name = habit.name,
            unit = habit.unit,
            thresholdPerPoint = habit.thresholdPerPoint,
            dailyTarget = habit.dailyTarget.toLong(),
            createdAt = habit.createdAt.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        db.habitTrackerDatabaseQueries.deleteHabit(id = habitId, userId = userId)
    }
}

private fun LocalHabit.toDomain(): Habit = Habit(
    id = id,
    userId = userId,
    templateId = templateId,
    name = name,
    unit = unit,
    thresholdPerPoint = thresholdPerPoint,
    dailyTarget = dailyTarget.toInt(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)
```

- [ ] **Step 6: Create HabitLogRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Instant

interface HabitLogRepository {
    suspend fun insertLog(
        id: String,
        userId: String,
        habitId: String,
        quantity: Double,
        loggedAt: Instant,
    ): HabitLog

    suspend fun softDelete(logId: String, userId: String)

    suspend fun getActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): List<HabitLog>

    suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog>
}
```

- [ ] **Step 7: Create LocalHabitLogRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitLog as HabitLogEntity
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalHabitLogRepository(
    private val db: HabitTrackerDatabase,
) : HabitLogRepository {

    override suspend fun insertLog(
        id: String,
        userId: String,
        habitId: String,
        quantity: Double,
        loggedAt: Instant,
    ): HabitLog {
        db.habitTrackerDatabaseQueries.insertHabitLog(
            id = id,
            userId = userId,
            habitId = habitId,
            quantity = quantity,
            loggedAt = loggedAt.toEpochMilliseconds(),
            deletedAt = null,
            syncedAt = null,
        )
        return HabitLog(id = id, userId = userId, habitId = habitId, quantity = quantity, loggedAt = loggedAt)
    }

    override suspend fun softDelete(logId: String, userId: String) {
        db.habitTrackerDatabaseQueries.softDeleteHabitLog(
            deletedAt = Clock.System.now().toEpochMilliseconds(),
            id = logId,
            userId = userId,
        )
    }

    override suspend fun getActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): List<HabitLog> =
        db.habitTrackerDatabaseQueries
            .getActiveHabitLogsForHabitOnDay(
                userId = userId,
                habitId = habitId,
                loggedAt = dayStart.toEpochMilliseconds(),
                loggedAt_ = dayEnd.toEpochMilliseconds(),
            )
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> =
        db.habitTrackerDatabaseQueries
            .getAllActiveHabitLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }
}

private fun HabitLogEntity.toDomain(): HabitLog = HabitLog(
    id = id,
    userId = userId,
    habitId = habitId,
    quantity = quantity,
    loggedAt = Instant.fromEpochMilliseconds(loggedAt),
    deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) },
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
```

- [ ] **Step 8: Create WantActivityRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity

interface WantActivityRepository {
    suspend fun getWantActivities(userId: String): List<WantActivity>
    suspend fun saveWantActivity(activity: WantActivity, userId: String)
}
```

- [ ] **Step 9: Create LocalWantActivityRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalWantActivity
import com.habittracker.domain.model.WantActivity

class LocalWantActivityRepository(
    private val db: HabitTrackerDatabase,
) : WantActivityRepository {

    override suspend fun getWantActivities(userId: String): List<WantActivity> =
        db.habitTrackerDatabaseQueries
            .getWantActivitiesForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun saveWantActivity(activity: WantActivity, userId: String) {
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = activity.id,
            userId = if (activity.isCustom) userId else null,
            name = activity.name,
            unit = activity.unit,
            costPerUnit = activity.costPerUnit,
            isCustom = if (activity.isCustom) 1L else 0L,
        )
    }
}

private fun LocalWantActivity.toDomain(): WantActivity = WantActivity(
    id = id,
    name = name,
    unit = unit,
    costPerUnit = costPerUnit,
    isCustom = isCustom == 1L,
    createdByUserId = userId,
)
```

- [ ] **Step 10: Create WantLogRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Instant

interface WantLogRepository {
    suspend fun insertLog(
        id: String,
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
        loggedAt: Instant,
    ): WantLog

    suspend fun softDelete(logId: String, userId: String)

    suspend fun getAllActiveLogsForUser(userId: String): List<WantLog>
}
```

- [ ] **Step 11: Create LocalWantLogRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.WantLog as WantLogEntity
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalWantLogRepository(
    private val db: HabitTrackerDatabase,
) : WantLogRepository {

    override suspend fun insertLog(
        id: String,
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
        loggedAt: Instant,
    ): WantLog {
        db.habitTrackerDatabaseQueries.insertWantLog(
            id = id,
            userId = userId,
            activityId = activityId,
            quantity = quantity,
            deviceMode = deviceMode.toDbValue(),
            loggedAt = loggedAt.toEpochMilliseconds(),
            deletedAt = null,
            syncedAt = null,
        )
        return WantLog(id = id, userId = userId, activityId = activityId, quantity = quantity, deviceMode = deviceMode, loggedAt = loggedAt)
    }

    override suspend fun softDelete(logId: String, userId: String) {
        db.habitTrackerDatabaseQueries.softDeleteWantLog(
            deletedAt = Clock.System.now().toEpochMilliseconds(),
            id = logId,
            userId = userId,
        )
    }

    override suspend fun getAllActiveLogsForUser(userId: String): List<WantLog> =
        db.habitTrackerDatabaseQueries
            .getAllActiveWantLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }
}

private fun DeviceMode.toDbValue(): String = when (this) {
    DeviceMode.THIS_DEVICE -> "this_device"
    DeviceMode.OTHER -> "other"
}

private fun String.toDeviceMode(): DeviceMode = when (this) {
    "this_device" -> DeviceMode.THIS_DEVICE
    else -> DeviceMode.OTHER
}

private fun WantLogEntity.toDomain(): WantLog = WantLog(
    id = id,
    userId = userId,
    activityId = activityId,
    quantity = quantity,
    deviceMode = deviceMode.toDeviceMode(),
    loggedAt = Instant.fromEpochMilliseconds(loggedAt),
    deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) },
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
```

- [ ] **Step 12: Build shared module to verify all repositories compile**

```bash
./gradlew :mobile:shared:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 13: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/
rtk git commit -m "feat: add SeedData, repository interfaces, and SQLDelight implementations"
```

---

## Task 4: Domain Models + Use Cases + Tests

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PointBalance.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitWithProgress.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogHabitUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UndoHabitLogUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UndoWantLogUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/IsOnboardedUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentityUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitLogRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantActivityRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantLogRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogHabitUseCaseTest.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseTest.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt`

- [ ] **Step 1: Create PointBalance.kt**

```kotlin
package com.habittracker.domain.model

data class PointBalance(val earned: Int, val spent: Int) {
    val balance: Int get() = maxOf(0, earned - spent)
}
```

- [ ] **Step 2: Create HabitWithProgress.kt**

```kotlin
package com.habittracker.domain.model

data class HabitWithProgress(
    val habit: Habit,
    val pointsToday: Int,
) {
    val isGoalMet: Boolean get() = pointsToday >= habit.dailyTarget
    val progressFraction: Float get() = (pointsToday.toFloat() / habit.dailyTarget).coerceAtMost(1f)
    val progressText: String get() = "$pointsToday / ${habit.dailyTarget}"
}
```

- [ ] **Step 3: Create LogHabitUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LogHabitResult(val log: HabitLog, val pointsEarned: Int)

class LogHabitUseCase(
    private val habitLogRepository: HabitLogRepository,
    private val habitRepository: HabitRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, habitId: String, quantity: Double): Result<LogHabitResult> =
        runCatching {
            val habit = habitRepository.getHabitsForUser(userId)
                .firstOrNull { it.id == habitId }
                ?: error("Habit $habitId not found for user $userId")
            val now = Clock.System.now()
            val id = Uuid.random().toString()
            val log = habitLogRepository.insertLog(id, userId, habitId, quantity, now)
            val points = PointCalculator.pointsEarned(quantity, habit.thresholdPerPoint)
            LogHabitResult(log, points)
        }
}
```

- [ ] **Step 4: Create LogWantUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LogWantResult(val log: WantLog, val pointsSpent: Int)

class LogWantUseCase(
    private val wantLogRepository: WantLogRepository,
    private val wantActivityRepository: WantActivityRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
    ): Result<LogWantResult> = runCatching {
        val activity = wantActivityRepository.getWantActivities(userId)
            .firstOrNull { it.id == activityId }
            ?: error("Activity $activityId not found")
        val now = Clock.System.now()
        val id = Uuid.random().toString()
        val log = wantLogRepository.insertLog(id, userId, activityId, quantity, deviceMode, now)
        val points = PointCalculator.pointsSpent(quantity, activity.costPerUnit)
        LogWantResult(log, points)
    }
}
```

- [ ] **Step 5: Create UndoHabitLogUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository

class UndoHabitLogUseCase(private val repository: HabitLogRepository) {
    suspend fun execute(logId: String, userId: String): Result<Unit> =
        runCatching { repository.softDelete(logId, userId) }
}
```

- [ ] **Step 6: Create UndoWantLogUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantLogRepository

class UndoWantLogUseCase(private val repository: WantLogRepository) {
    suspend fun execute(logId: String, userId: String): Result<Unit> =
        runCatching { repository.softDelete(logId, userId) }
}
```

- [ ] **Step 7: Create GetPointBalanceUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.PointBalance
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn

class GetPointBalanceUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
) {
    suspend fun execute(userId: String): Result<PointBalance> = runCatching {
        val weekStart = currentWeekStart()
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }

        val earned = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStart }
            .sumOf { log ->
                habits[log.habitId]?.let {
                    PointCalculator.pointsEarned(log.quantity, it.thresholdPerPoint)
                } ?: 0
            }

        val spent = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStart }
            .sumOf { log ->
                activities[log.activityId]?.let {
                    PointCalculator.pointsSpent(log.quantity, it.costPerUnit)
                } ?: 0
            }

        PointBalance(earned, spent)
    }

    internal fun currentWeekStart(): Instant {
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.UTC).date
        val daysFromMonday = localDate.dayOfWeek.isoDayNumber - 1
        val monday = localDate.minus(daysFromMonday, DateTimeUnit.DAY)
        return monday.atStartOfDayIn(TimeZone.UTC)
    }
}
```

- [ ] **Step 8: Create IsOnboardedUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository

class IsOnboardedUseCase(private val habitRepository: HabitRepository) {
    suspend fun execute(userId: String): Boolean =
        habitRepository.getHabitsForUser(userId).isNotEmpty()
}
```

- [ ] **Step 9: Create GetHabitTemplatesForIdentityUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.HabitTemplate

class GetHabitTemplatesForIdentityUseCase {
    fun execute(identityId: String): List<HabitTemplate> =
        SeedData.identityHabitMap[identityId]
            ?.mapNotNull { SeedData.habitTemplates[it] }
            ?: emptyList()
}
```

- [ ] **Step 10: Create SetupUserHabitsUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitTemplate
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SetupUserHabitsUseCase(private val habitRepository: HabitRepository) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, templates: List<HabitTemplate>): Result<Unit> =
        runCatching {
            val now = Clock.System.now()
            templates.forEach { template ->
                habitRepository.saveHabit(
                    Habit(
                        id = Uuid.random().toString(),
                        userId = userId,
                        templateId = template.id,
                        name = template.name,
                        unit = template.unit,
                        thresholdPerPoint = template.defaultThreshold,
                        dailyTarget = template.defaultDailyTarget,
                        createdAt = now,
                    )
                )
            }
        }
}
```

- [ ] **Step 11: Create SetupUserWantActivitiesUseCase.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity

class SetupUserWantActivitiesUseCase(private val wantActivityRepository: WantActivityRepository) {
    suspend fun execute(userId: String, activities: List<WantActivity>): Result<Unit> =
        runCatching {
            activities.forEach { activity ->
                wantActivityRepository.saveWantActivity(activity, userId)
            }
        }
}
```

- [ ] **Step 12: Write failing tests — create FakeHabitRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit

class FakeHabitRepository : HabitRepository {
    val habits = mutableListOf<Habit>()

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        habits.filter { it.userId == userId }

    override suspend fun saveHabit(habit: Habit) {
        habits.removeAll { it.id == habit.id }
        habits.add(habit)
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        habits.removeAll { it.id == habitId && it.userId == userId }
    }
}
```

- [ ] **Step 13: Create FakeHabitLogRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeHabitLogRepository : HabitLogRepository {
    private val _logs = mutableListOf<HabitLog>()
    val logs: List<HabitLog> get() = _logs

    override suspend fun insertLog(
        id: String, userId: String, habitId: String,
        quantity: Double, loggedAt: Instant,
    ): HabitLog {
        val log = HabitLog(id = id, userId = userId, habitId = habitId, quantity = quantity, loggedAt = loggedAt)
        _logs.add(log)
        return log
    }

    override suspend fun softDelete(logId: String, userId: String) {
        val i = _logs.indexOfFirst { it.id == logId && it.userId == userId }
        if (i >= 0) _logs[i] = _logs[i].copy(deletedAt = Clock.System.now())
    }

    override suspend fun getActiveLogsForHabitOnDay(
        userId: String, habitId: String, dayStart: Instant, dayEnd: Instant,
    ): List<HabitLog> = _logs.filter {
        it.isActive && it.userId == userId && it.habitId == habitId
            && it.loggedAt >= dayStart && it.loggedAt < dayEnd
    }

    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> =
        _logs.filter { it.isActive && it.userId == userId }
}
```

- [ ] **Step 14: Create FakeWantActivityRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity

class FakeWantActivityRepository : WantActivityRepository {
    val activities = mutableListOf<WantActivity>()

    override suspend fun getWantActivities(userId: String): List<WantActivity> = activities

    override suspend fun saveWantActivity(activity: WantActivity, userId: String) {
        activities.removeAll { it.id == activity.id }
        activities.add(activity)
    }
}
```

- [ ] **Step 15: Create FakeWantLogRepository.kt**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeWantLogRepository : WantLogRepository {
    private val _logs = mutableListOf<WantLog>()
    val logs: List<WantLog> get() = _logs

    override suspend fun insertLog(
        id: String, userId: String, activityId: String,
        quantity: Double, deviceMode: DeviceMode, loggedAt: Instant,
    ): WantLog {
        val log = WantLog(id = id, userId = userId, activityId = activityId, quantity = quantity, deviceMode = deviceMode, loggedAt = loggedAt)
        _logs.add(log)
        return log
    }

    override suspend fun softDelete(logId: String, userId: String) {
        val i = _logs.indexOfFirst { it.id == logId && it.userId == userId }
        if (i >= 0) _logs[i] = _logs[i].copy(deletedAt = Clock.System.now())
    }

    override suspend fun getAllActiveLogsForUser(userId: String): List<WantLog> =
        _logs.filter { it.isActive && it.userId == userId }
}
```

- [ ] **Step 16: Create LogHabitUseCaseTest.kt and run — expect FAIL (use cases not compiled yet)**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogHabitUseCaseTest {
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val useCase = LogHabitUseCase(habitLogRepo, habitRepo)
    private val userId = "user1"

    private fun makeHabit(id: String, threshold: Double) = Habit(
        id = id, userId = userId, templateId = "tpl", name = "Read",
        unit = "pages", thresholdPerPoint = threshold, dailyTarget = 3,
        createdAt = Clock.System.now(),
    )

    @Test
    fun `returns correct points earned`() = runTest {
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        val result = useCase.execute(userId, "h1", 9.0).getOrThrow()
        assertEquals(3, result.pointsEarned)
        assertEquals(9.0, result.log.quantity)
    }

    @Test
    fun `returns zero points below threshold`() = runTest {
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        val result = useCase.execute(userId, "h1", 2.0).getOrThrow()
        assertEquals(0, result.pointsEarned)
    }

    @Test
    fun `fails for unknown habit`() = runTest {
        val result = useCase.execute(userId, "unknown", 5.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun `log is stored in repository`() = runTest {
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        useCase.execute(userId, "h1", 6.0).getOrThrow()
        assertEquals(1, habitLogRepo.logs.size)
        assertEquals("h1", habitLogRepo.logs.first().habitId)
    }
}
```

Run: `./gradlew :mobile:shared:allTests`
Expected: FAIL (LogHabitUseCase not yet created — but since we created it in Step 3, it should PASS)

- [ ] **Step 17: Create LogWantUseCaseTest.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogWantUseCaseTest {
    private val activityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val useCase = LogWantUseCase(wantLogRepo, activityRepo)
    private val userId = "user1"

    @Test
    fun `returns correct points spent for youtube`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 0.1))
        val result = useCase.execute(userId, "a1", 30.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(3, result.pointsSpent)
    }

    @Test
    fun `records device mode correctly`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1.0))
        val result = useCase.execute(userId, "a1", 10.0, DeviceMode.THIS_DEVICE).getOrThrow()
        assertEquals(DeviceMode.THIS_DEVICE, result.log.deviceMode)
    }

    @Test
    fun `fails for unknown activity`() = runTest {
        val result = useCase.execute(userId, "unknown", 10.0, DeviceMode.OTHER)
        assertTrue(result.isFailure)
    }

    @Test
    fun `zero points for quantity below cost threshold`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 0.1))
        val result = useCase.execute(userId, "a1", 5.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(0, result.pointsSpent)
    }
}
```

- [ ] **Step 18: Create GetPointBalanceUseCaseTest.kt**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class GetPointBalanceUseCaseTest {
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val habitRepo = FakeHabitRepository()
    private val activityRepo = FakeWantActivityRepository()
    private val useCase = GetPointBalanceUseCase(habitLogRepo, wantLogRepo, habitRepo, activityRepo)
    private val userId = "user1"

    private fun weekStart() = useCase.currentWeekStart()

    @Test
    fun `balance is zero with no logs`() = runTest {
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(0, result.earned)
        assertEquals(0, result.spent)
        assertEquals(0, result.balance)
    }

    @Test
    fun `computes earned from habit logs this week`() = runTest {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now()))
        habitLogRepo.insertLog("l1", userId, "h1", 9.0, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.earned)
    }

    @Test
    fun `computes spent from want logs this week`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        wantLogRepo.insertLog("l1", userId, "a1", 30.0, DeviceMode.OTHER, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.spent)
    }

    @Test
    fun `balance is earned minus spent`() = runTest {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now()))
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        habitLogRepo.insertLog("l1", userId, "h1", 9.0, Clock.System.now())
        wantLogRepo.insertLog("l2", userId, "a1", 30.0, DeviceMode.OTHER, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.earned)
        assertEquals(3, result.spent)
        assertEquals(0, result.balance)
    }

    @Test
    fun `balance never goes below zero`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1.0))
        wantLogRepo.insertLog("l1", userId, "a1", 10.0, DeviceMode.OTHER, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(0, result.balance)
    }

    @Test
    fun `excludes logs from before this week`() = runTest {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now()))
        val lastWeek = weekStart() - 1.days
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, lastWeek)
        habitLogRepo.insertLog("l2", userId, "h1", 3.0, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(1, result.earned)
    }
}
```

- [ ] **Step 19: Run all tests**

```bash
./gradlew :mobile:shared:allTests
```

Expected: BUILD SUCCESSFUL, all tests pass (15 existing + 12 new = 27 tests)

- [ ] **Step 20: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/
rtk git add mobile/shared/src/commonTest/kotlin/com/habittracker/
rtk git commit -m "feat: add domain models, use cases, and tests (27 tests passing)"
```

---

## Task 5: AppContainer + Application + Navigation Skeleton

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/HabitTrackerApplication.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/AppContainer.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/navigation/AppNavigation.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/MainActivity.kt`
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Create HabitTrackerApplication.kt**

```kotlin
package com.habittracker.android

import android.app.Application

class HabitTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 2: Create AppContainer.kt**

```kotlin
package com.habittracker.android

import android.content.Context
import com.habittracker.data.local.DatabaseDriverFactory
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.SeedData
import com.habittracker.data.remote.SupabaseClientFactory
import com.habittracker.data.repository.LocalHabitLogRepository
import com.habittracker.data.repository.LocalHabitRepository
import com.habittracker.data.repository.LocalIdentityRepository
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.SupabaseAuthRepository
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentityUseCase
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.IsOnboardedUseCase
import com.habittracker.domain.usecase.LogHabitUseCase
import com.habittracker.domain.usecase.LogWantUseCase
import com.habittracker.domain.usecase.SetupUserHabitsUseCase
import com.habittracker.domain.usecase.SetupUserWantActivitiesUseCase
import com.habittracker.domain.usecase.UndoHabitLogUseCase
import com.habittracker.domain.usecase.UndoWantLogUseCase

class AppContainer(context: Context) {

    private val supabase = SupabaseClientFactory.create(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
    )

    private val db = HabitTrackerDatabase(DatabaseDriverFactory(context).createDriver())

    val authRepository = SupabaseAuthRepository(supabase)
    val identityRepository = LocalIdentityRepository(db)
    val habitRepository = LocalHabitRepository(db)
    val habitLogRepository = LocalHabitLogRepository(db)
    val wantActivityRepository = LocalWantActivityRepository(db)
    val wantLogRepository = LocalWantLogRepository(db)

    val logHabitUseCase = LogHabitUseCase(habitLogRepository, habitRepository)
    val logWantUseCase = LogWantUseCase(wantLogRepository, wantActivityRepository)
    val undoHabitLogUseCase = UndoHabitLogUseCase(habitLogRepository)
    val undoWantLogUseCase = UndoWantLogUseCase(wantLogRepository)
    val getPointBalanceUseCase = GetPointBalanceUseCase(habitLogRepository, wantLogRepository, habitRepository, wantActivityRepository)
    val isOnboardedUseCase = IsOnboardedUseCase(habitRepository)
    val getHabitTemplatesForIdentityUseCase = GetHabitTemplatesForIdentityUseCase()
    val setupUserHabitsUseCase = SetupUserHabitsUseCase(habitRepository)
    val setupUserWantActivitiesUseCase = SetupUserWantActivitiesUseCase(wantActivityRepository)

    suspend fun seedLocalDataIfEmpty() {
        if (identityRepository.getAllIdentities().isEmpty()) {
            identityRepository.upsertIdentities(SeedData.identities)
        }
        val userId = authRepository.currentUserId()
        if (userId != null && wantActivityRepository.getWantActivities(userId).isEmpty()) {
            SeedData.wantActivities.forEach { activity ->
                wantActivityRepository.saveWantActivity(activity, userId)
            }
        }
    }
}
```

- [ ] **Step 3: Create AppNavigation.kt**

```kotlin
package com.habittracker.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habittracker.android.AppContainer
import com.habittracker.android.ui.auth.AuthScreen
import com.habittracker.android.ui.auth.AuthViewModel
import com.habittracker.android.ui.home.HomeScreen
import com.habittracker.android.ui.home.HomeViewModel
import com.habittracker.android.ui.log.LogHabitScreen
import com.habittracker.android.ui.log.LogHabitViewModel
import com.habittracker.android.ui.log.LogWantScreen
import com.habittracker.android.ui.log.LogWantViewModel
import com.habittracker.android.ui.onboarding.OnboardingScreen
import com.habittracker.android.ui.onboarding.OnboardingViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object LogHabit : Screen("log_habit/{habitId}") {
        const val ARG = "habitId"
        fun route(habitId: String) = "log_habit/$habitId"
    }
    object LogWant : Screen("log_want/{activityId}") {
        const val ARG = "activityId"
        fun route(activityId: String) = "log_want/$activityId"
    }
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Auth.route) {

        composable(Screen.Auth.route) {
            val vm = viewModel { AuthViewModel(container) }
            AuthScreen(
                viewModel = vm,
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Onboarding.route) {
            val vm = viewModel { OnboardingViewModel(container) }
            OnboardingScreen(
                viewModel = vm,
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Home.route) {
            val vm = viewModel { HomeViewModel(container) }
            HomeScreen(
                viewModel = vm,
                onLogHabit = { habitId -> navController.navigate(Screen.LogHabit.route(habitId)) },
                onLogWant = { activityId -> navController.navigate(Screen.LogWant.route(activityId)) },
            )
        }

        composable(
            route = Screen.LogHabit.route,
            arguments = listOf(navArgument(Screen.LogHabit.ARG) { type = NavType.StringType }),
        ) { backStack ->
            val habitId = backStack.arguments?.getString(Screen.LogHabit.ARG) ?: return@composable
            val vm = viewModel(key = habitId) { LogHabitViewModel(habitId, container) }
            LogHabitScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.LogWant.route,
            arguments = listOf(navArgument(Screen.LogWant.ARG) { type = NavType.StringType }),
        ) { backStack ->
            val activityId = backStack.arguments?.getString(Screen.LogWant.ARG) ?: return@composable
            val vm = viewModel(key = activityId) { LogWantViewModel(activityId, container) }
            LogWantScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
```

- [ ] **Step 4: Update MainActivity.kt**

```kotlin
package com.habittracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.habittracker.android.ui.navigation.AppNavigation
import com.habittracker.android.ui.theme.HabitTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HabitTrackerApplication).container
        setContent {
            HabitTrackerTheme {
                AppNavigation(container = container)
            }
        }
    }
}
```

- [ ] **Step 5: Update AndroidManifest.xml — add android:name**

Replace the `<application` opening tag to add the name attribute:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:name=".HabitTrackerApplication"
        android:allowBackup="true"
        android:label="Habit Tracker"
        android:theme="@style/Theme.HabitTracker">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: Build to verify navigation + DI compiles**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL (Auth/Onboarding/Home/Log screens referenced but not yet created — expect unresolved reference errors. Create stub files for each screen to unblock build.)

Create stub files (will be replaced in Tasks 6-10):

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthViewModel.kt`:
```kotlin
package com.habittracker.android.ui.auth
import androidx.lifecycle.ViewModel
import com.habittracker.android.AppContainer
class AuthViewModel(container: AppContainer) : ViewModel()
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthScreen.kt`:
```kotlin
package com.habittracker.android.ui.auth
import androidx.compose.runtime.Composable
@Composable fun AuthScreen(viewModel: AuthViewModel, onNavigateToOnboarding: () -> Unit, onNavigateToHome: () -> Unit) {}
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingViewModel.kt`:
```kotlin
package com.habittracker.android.ui.onboarding
import androidx.lifecycle.ViewModel
import com.habittracker.android.AppContainer
class OnboardingViewModel(container: AppContainer) : ViewModel()
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingScreen.kt`:
```kotlin
package com.habittracker.android.ui.onboarding
import androidx.compose.runtime.Composable
@Composable fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit) {}
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt`:
```kotlin
package com.habittracker.android.ui.home
import androidx.lifecycle.ViewModel
import com.habittracker.android.AppContainer
class HomeViewModel(container: AppContainer) : ViewModel()
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeScreen.kt`:
```kotlin
package com.habittracker.android.ui.home
import androidx.compose.runtime.Composable
@Composable fun HomeScreen(viewModel: HomeViewModel, onLogHabit: (String) -> Unit, onLogWant: (String) -> Unit) {}
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitViewModel.kt`:
```kotlin
package com.habittracker.android.ui.log
import androidx.lifecycle.ViewModel
import com.habittracker.android.AppContainer
class LogHabitViewModel(habitId: String, container: AppContainer) : ViewModel()
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitScreen.kt`:
```kotlin
package com.habittracker.android.ui.log
import androidx.compose.runtime.Composable
@Composable fun LogHabitScreen(viewModel: LogHabitViewModel, onBack: () -> Unit) {}
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantViewModel.kt`:
```kotlin
package com.habittracker.android.ui.log
import androidx.lifecycle.ViewModel
import com.habittracker.android.AppContainer
class LogWantViewModel(activityId: String, container: AppContainer) : ViewModel()
```

`mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantScreen.kt`:
```kotlin
package com.habittracker.android.ui.log
import androidx.compose.runtime.Composable
@Composable fun LogWantScreen(viewModel: LogWantViewModel, onBack: () -> Unit) {}
```

Run again: `./gradlew :mobile:androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/
rtk git commit -m "feat: add AppContainer, Application, navigation skeleton with stub screens"
```

---

## Task 6: Auth Screen + ViewModel

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthScreen.kt`

- [ ] **Step 1: Write full AuthViewModel.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthNavigation {
    object ToOnboarding : AuthNavigation
    object ToHome : AuthNavigation
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<AuthNavigation>()
    val navigation: SharedFlow<AuthNavigation> = _navigation.asSharedFlow()

    init {
        viewModelScope.launch {
            if (container.authRepository.isLoggedIn()) {
                val userId = container.authRepository.currentUserId() ?: return@launch
                container.seedLocalDataIfEmpty()
                if (container.isOnboardedUseCase.execute(userId)) {
                    _navigation.emit(AuthNavigation.ToHome)
                } else {
                    _navigation.emit(AuthNavigation.ToOnboarding)
                }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun toggleSignUp() {
        _uiState.value = _uiState.value.copy(isSignUp = !_uiState.value.isSignUp, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password required")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = if (state.isSignUp) {
                container.authRepository.signUp(state.email.trim(), state.password)
            } else {
                container.authRepository.signIn(state.email.trim(), state.password)
            }
            result.onSuccess { session ->
                container.seedLocalDataIfEmpty()
                if (container.isOnboardedUseCase.execute(session.userId)) {
                    _navigation.emit(AuthNavigation.ToHome)
                } else {
                    _navigation.emit(AuthNavigation.ToOnboarding)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Auth failed")
            }
        }
    }
}
```

- [ ] **Step 2: Write full AuthScreen.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.habittracker.android.ui.theme.Spacing

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { nav ->
            when (nav) {
                AuthNavigation.ToOnboarding -> onNavigateToOnboarding()
                AuthNavigation.ToHome -> onNavigateToHome()
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xxl)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (uiState.isSignUp) "Create Account" else "Sign In",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.isSignUp) "Sign Up" else "Sign In")
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(onClick = viewModel::toggleSignUp) {
                    Text(
                        if (uiState.isSignUp) "Already have an account? Sign In"
                        else "Don't have an account? Sign Up"
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify auth UI compiles**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/
rtk git commit -m "feat: add Auth screen and ViewModel with sign in/up flow"
```

---

## Task 7: Onboarding Screen + ViewModel

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Write full OnboardingViewModel.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { IDENTITY, HABITS, WANTS }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.IDENTITY,
    val identities: List<Identity> = SeedData.identities,
    val selectedIdentityId: String? = null,
    val habitTemplates: List<HabitTemplate> = emptyList(),
    val selectedTemplateIds: Set<String> = emptySet(),
    val wantActivities: List<WantActivity> = SeedData.wantActivities,
    val selectedActivityIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>()
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun selectIdentity(identityId: String) {
        val templates = container.getHabitTemplatesForIdentityUseCase.execute(identityId)
        _uiState.value = _uiState.value.copy(
            selectedIdentityId = identityId,
            habitTemplates = templates,
            selectedTemplateIds = emptySet(),
        )
    }

    fun continueFromIdentity() {
        if (_uiState.value.selectedIdentityId == null) return
        _uiState.value = _uiState.value.copy(step = OnboardingStep.HABITS)
    }

    fun toggleHabit(templateId: String) {
        val current = _uiState.value.selectedTemplateIds.toMutableSet()
        if (current.contains(templateId)) current.remove(templateId) else current.add(templateId)
        _uiState.value = _uiState.value.copy(selectedTemplateIds = current)
    }

    fun continueFromHabits() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.WANTS)
    }

    fun toggleWantActivity(activityId: String) {
        val current = _uiState.value.selectedActivityIds.toMutableSet()
        if (current.contains(activityId)) current.remove(activityId) else current.add(activityId)
        _uiState.value = _uiState.value.copy(selectedActivityIds = current)
    }

    fun finish() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = container.authRepository.currentUserId() ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Not logged in")
                return@launch
            }
            val state = _uiState.value
            val selectedTemplates = state.habitTemplates.filter { it.id in state.selectedTemplateIds }
            val selectedActivities = state.wantActivities.filter { it.id in state.selectedActivityIds }

            container.setupUserHabitsUseCase.execute(userId, selectedTemplates)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            container.setupUserWantActivitiesUseCase.execute(userId, selectedActivities)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            _finished.emit(Unit)
        }
    }
}
```

- [ ] **Step 2: Write full OnboardingScreen.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.WantActivity

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.finished.collect { onFinished() }
    }

    Scaffold { padding ->
        when (uiState.step) {
            OnboardingStep.IDENTITY -> IdentityStep(
                identities = uiState.identities,
                selectedId = uiState.selectedIdentityId,
                onSelect = viewModel::selectIdentity,
                onContinue = viewModel::continueFromIdentity,
                modifier = Modifier.padding(padding),
            )
            OnboardingStep.HABITS -> HabitsStep(
                templates = uiState.habitTemplates,
                selectedIds = uiState.selectedTemplateIds,
                onToggle = viewModel::toggleHabit,
                onContinue = viewModel::continueFromHabits,
                onSkip = viewModel::continueFromHabits,
                modifier = Modifier.padding(padding),
            )
            OnboardingStep.WANTS -> WantsStep(
                activities = uiState.wantActivities,
                selectedIds = uiState.selectedActivityIds,
                onToggle = viewModel::toggleWantActivity,
                onFinish = viewModel::finish,
                onSkip = viewModel::finish,
                isLoading = uiState.isLoading,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun IdentityStep(
    identities: List<Identity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
    ) {
        Text("Who do you want to become?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(identities) { identity ->
                val selected = identity.id == selectedId
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(identity.id) },
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.xl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(identity.icon, style = MaterialTheme.typography.headlineMedium)
                        Column(modifier = Modifier.padding(start = Spacing.md)) {
                            Text(identity.name, style = MaterialTheme.typography.titleMedium)
                            Text(identity.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = onContinue,
            enabled = selectedId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun HabitsStep(
    templates: List<HabitTemplate>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.xl)) {
        Text("Recommended habits", style = MaterialTheme.typography.headlineSmall)
        Text("Pick 2–3 to start. Less is more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(templates) { template ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(template.id) }.padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = template.id in selectedIds, onCheckedChange = { onToggle(template.id) })
                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Start with ${template.defaultThreshold.toInt()} ${template.unit} = 1 point",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
    }
}

@Composable
private fun WantsStep(
    activities: List<WantActivity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.xl)) {
        Text("What do you do for fun?", style = MaterialTheme.typography.headlineSmall)
        Text("These cost points. Earn them first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(activities) { activity ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(activity.id) }.padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = activity.id in selectedIds, onCheckedChange = { onToggle(activity.id) })
                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                        Text(activity.name, style = MaterialTheme.typography.bodyLarge)
                        val costText = if (activity.costPerUnit >= 1.0) {
                            "${activity.costPerUnit.toInt()} pt / ${activity.unit}"
                        } else {
                            "1 pt / ${(1.0 / activity.costPerUnit).toInt()} ${activity.unit}"
                        }
                        Text(costText, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onFinish, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Get Started")
        }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onSkip, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Skip")
        }
    }
}
```

- [ ] **Step 3: Build to verify onboarding UI compiles**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/
rtk git commit -m "feat: add Onboarding screen and ViewModel (identity → habits → wants)"
```

---

## Task 8: Home Screen + ViewModel

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeScreen.kt`

- [ ] **Step 1: Write full HomeViewModel.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.PointCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

data class HomeUiState(
    val habitsWithProgress: List<HabitWithProgress> = emptyList(),
    val pointBalance: PointBalance = PointBalance(0, 0),
    val wantActivities: List<WantActivity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val userId = container.authRepository.currentUserId() ?: return@launch
            val habits = container.habitRepository.getHabitsForUser(userId)

            val now = Clock.System.now()
            val todayDate = now.toLocalDateTime(TimeZone.UTC).date
            val dayStart = todayDate.atStartOfDayIn(TimeZone.UTC)
            val dayEnd = dayStart + 1.days

            val habitsWithProgress = habits.map { habit ->
                val logsToday = container.habitLogRepository
                    .getActiveLogsForHabitOnDay(userId, habit.id, dayStart, dayEnd)
                val pointsToday = logsToday.sumOf {
                    PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
                }
                HabitWithProgress(habit, pointsToday)
            }

            val balance = container.getPointBalanceUseCase.execute(userId)
                .getOrDefault(PointBalance(0, 0))

            val activities = container.wantActivityRepository.getWantActivities(userId)

            _uiState.value = HomeUiState(
                habitsWithProgress = habitsWithProgress,
                pointBalance = balance,
                wantActivities = activities,
                isLoading = false,
            )
        }
    }
}
```

- [ ] **Step 2: Write full HomeScreen.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.android.ui.theme.streakCompleteColor
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogHabit: (String) -> Unit,
    onLogWant: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Habit Tracker") })
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Spacer(Modifier.height(Spacing.md))
                PointBalanceCard(
                    earned = uiState.pointBalance.earned,
                    spent = uiState.pointBalance.spent,
                    balance = uiState.pointBalance.balance,
                )
                Spacer(Modifier.height(Spacing.xl))
                Text("Today's Habits", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
            }

            if (uiState.habitsWithProgress.isEmpty()) {
                item {
                    Text(
                        "No habits yet. Complete onboarding to add habits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.habitsWithProgress) { hwp ->
                    HabitCard(habitWithProgress = hwp, onClick = { onLogHabit(hwp.habit.id) })
                }
            }

            if (uiState.wantActivities.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.xl))
                    HorizontalDivider()
                    Spacer(Modifier.height(Spacing.md))
                    Text("Want Activities", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                }
                items(uiState.wantActivities) { activity ->
                    WantActivityRow(activity = activity, onClick = { onLogWant(activity.id) })
                }
            }

            item { Spacer(Modifier.height(Spacing.xxxl)) }
        }
    }
}

@Composable
private fun PointBalanceCard(earned: Int, spent: Int, balance: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Text("Point Balance", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(Spacing.sm))
            Text("$balance pts", style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text("Earned: $earned  ·  Spent: $spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun HabitCard(habitWithProgress: HabitWithProgress, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(habitWithProgress.habit.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    habitWithProgress.progressText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (habitWithProgress.isGoalMet) streakCompleteColor()
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { habitWithProgress.progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (habitWithProgress.isGoalMet) streakCompleteColor()
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Threshold: ${habitWithProgress.habit.thresholdPerPoint.toInt()} ${habitWithProgress.habit.unit} = 1 pt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WantActivityRow(activity: WantActivity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(activity.name, style = MaterialTheme.typography.bodyLarge)
        val costText = if (activity.costPerUnit >= 1.0) {
            "${activity.costPerUnit.toInt()} pt/${activity.unit}"
        } else {
            "1 pt/${(1.0 / activity.costPerUnit).toInt()} ${activity.unit}"
        }
        Text(costText, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 3: Build to verify home UI compiles**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/
rtk git commit -m "feat: add Home screen with habit progress and point balance"
```

---

## Task 8.5: Guest Mode — LocalUserIdProvider + Navigation Rework

**Motivation:** Spec §8.3 revised — auth is optional. App must work fully offline without sign-in. Auth is reachable from Home via "Sign in to sync" CTA. `currentUserId()` returns the Supabase `auth.uid()` if logged in, else a persisted `localUserId`.

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/LocalUserIdStore.kt` (expect)
- Create: `mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/LocalUserIdStore.android.kt` (actual — SharedPreferences)
- Create: `mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/LocalUserIdStore.ios.kt` (actual — NSUserDefaults)
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/UserIdentityProvider.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/navigation/AppNavigation.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/onboarding/OnboardingViewModel.kt`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` (add `migrateLocalUserIdTo`, `clearUserData` queries)
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/*` (add migrate/clear hooks on each repo)

### Step 1: `LocalUserIdStore` expect

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/LocalUserIdStore.kt`:

```kotlin
package com.habittracker.data.local

expect class LocalUserIdStore {
    fun getOrCreate(): String
}
```

### Step 2: Android actual (SharedPreferences)

`mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/LocalUserIdStore.android.kt`:

```kotlin
package com.habittracker.data.local

import android.content.Context
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class LocalUserIdStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @OptIn(ExperimentalUuidApi::class)
    actual fun getOrCreate(): String {
        val existing = prefs.getString(KEY_LOCAL_USER_ID, null)
        if (existing != null) return existing
        val fresh = Uuid.random().toString()
        prefs.edit().putString(KEY_LOCAL_USER_ID, fresh).apply()
        return fresh
    }

    private companion object {
        const val PREFS_NAME = "habit_tracker_identity"
        const val KEY_LOCAL_USER_ID = "local_user_id"
    }
}
```

### Step 3: iOS actual (NSUserDefaults)

`mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/LocalUserIdStore.ios.kt`:

```kotlin
package com.habittracker.data.local

import platform.Foundation.NSUserDefaults
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class LocalUserIdStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    @OptIn(ExperimentalUuidApi::class)
    actual fun getOrCreate(): String {
        val existing = defaults.stringForKey(KEY)
        if (existing != null) return existing
        val fresh = Uuid.random().toString()
        defaults.setObject(fresh, KEY)
        return fresh
    }

    private companion object {
        const val KEY = "habit_tracker.local_user_id"
    }
}
```

### Step 4: `UserIdentityProvider`

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/UserIdentityProvider.kt`:

```kotlin
package com.habittracker.domain

import com.habittracker.data.local.LocalUserIdStore
import com.habittracker.data.repository.AuthRepository

class UserIdentityProvider(
    private val authRepository: AuthRepository,
    private val localUserIdStore: LocalUserIdStore,
) {
    fun currentUserId(): String =
        authRepository.currentUserId() ?: localUserIdStore.getOrCreate()

    fun localUserId(): String = localUserIdStore.getOrCreate()

    fun isAuthenticated(): Boolean = authRepository.isLoggedIn()
}
```

### Step 5: Add SQLDelight migrate + clear queries

Append to `HabitTrackerDatabase.sq`:

```sql
-- User id migration (guest → authenticated)
migrateHabitsUserId:
UPDATE LocalHabit SET userId = ? WHERE userId = ?;

migrateHabitLogsUserId:
UPDATE HabitLog SET userId = ? WHERE userId = ?;

migrateWantLogsUserId:
UPDATE WantLog SET userId = ? WHERE userId = ?;

migrateWantActivitiesUserId:
UPDATE LocalWantActivity SET userId = ? WHERE userId = ?;

-- Logout wipe
clearHabitsForUser:
DELETE FROM LocalHabit WHERE userId = ?;

clearHabitLogsForUser:
DELETE FROM HabitLog WHERE userId = ?;

clearWantLogsForUser:
DELETE FROM WantLog WHERE userId = ?;

clearCustomWantActivitiesForUser:
DELETE FROM LocalWantActivity WHERE userId = ?;
```

### Step 6: Repo hooks

Add to `HabitRepository` interface:
```kotlin
suspend fun migrateUserId(oldUserId: String, newUserId: String)
suspend fun clearForUser(userId: String)
```

Add to `HabitLogRepository`, `WantLogRepository`, `WantActivityRepository` the same pair. `IdentityRepository` is user-agnostic (seed data) — skip.

`LocalHabitRepository` impl:
```kotlin
override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
    db.habitTrackerDatabaseQueries.migrateHabitsUserId(newUserId, oldUserId)
}

override suspend fun clearForUser(userId: String) {
    db.habitTrackerDatabaseQueries.clearHabitsForUser(userId)
}
```

Mirror pattern on `LocalHabitLogRepository`, `LocalWantLogRepository`, `LocalWantActivityRepository`. `FakeHabitRepository` / `FakeHabitLogRepository` / `FakeWantActivityRepository` / `FakeWantLogRepository` in commonTest also add trivial implementations.

### Step 7: AppContainer rework

Replace the existing `AppContainer`:

```kotlin
package com.habittracker.android

import android.content.Context
import com.habittracker.data.local.DatabaseDriverFactory
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalUserIdStore
import com.habittracker.data.local.SeedData
import com.habittracker.data.remote.SupabaseClientFactory
import com.habittracker.data.repository.LocalHabitLogRepository
import com.habittracker.data.repository.LocalHabitRepository
import com.habittracker.data.repository.LocalIdentityRepository
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.SupabaseAuthRepository
import com.habittracker.domain.UserIdentityProvider
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentityUseCase
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.IsOnboardedUseCase
import com.habittracker.domain.usecase.LogHabitUseCase
import com.habittracker.domain.usecase.LogWantUseCase
import com.habittracker.domain.usecase.SetupUserHabitsUseCase
import com.habittracker.domain.usecase.SetupUserWantActivitiesUseCase
import com.habittracker.domain.usecase.UndoHabitLogUseCase
import com.habittracker.domain.usecase.UndoWantLogUseCase

class AppContainer(context: Context) {

    private val supabase = SupabaseClientFactory.create(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
    )

    private val db = HabitTrackerDatabase(DatabaseDriverFactory(context).createDriver())
    private val localUserIdStore = LocalUserIdStore(context)

    val authRepository = SupabaseAuthRepository(supabase)
    val identityRepository = LocalIdentityRepository(db)
    val habitRepository = LocalHabitRepository(db)
    val habitLogRepository = LocalHabitLogRepository(db)
    val wantActivityRepository = LocalWantActivityRepository(db)
    val wantLogRepository = LocalWantLogRepository(db)

    val userIdentityProvider = UserIdentityProvider(authRepository, localUserIdStore)

    val logHabitUseCase = LogHabitUseCase(habitLogRepository, habitRepository)
    val logWantUseCase = LogWantUseCase(wantLogRepository, wantActivityRepository)
    val undoHabitLogUseCase = UndoHabitLogUseCase(habitLogRepository)
    val undoWantLogUseCase = UndoWantLogUseCase(wantLogRepository)
    val getPointBalanceUseCase = GetPointBalanceUseCase(habitLogRepository, wantLogRepository, habitRepository, wantActivityRepository)
    val isOnboardedUseCase = IsOnboardedUseCase(habitRepository)
    val getHabitTemplatesForIdentityUseCase = GetHabitTemplatesForIdentityUseCase()
    val setupUserHabitsUseCase = SetupUserHabitsUseCase(habitRepository)
    val setupUserWantActivitiesUseCase = SetupUserWantActivitiesUseCase(wantActivityRepository)

    fun currentUserId(): String = userIdentityProvider.currentUserId()
    fun isAuthenticated(): Boolean = userIdentityProvider.isAuthenticated()

    suspend fun seedLocalDataIfEmpty() {
        if (identityRepository.getAllIdentities().isEmpty()) {
            identityRepository.upsertIdentities(SeedData.identities)
        }
        val userId = currentUserId()
        if (wantActivityRepository.getWantActivities(userId).isEmpty()) {
            SeedData.wantActivities.forEach { activity ->
                wantActivityRepository.saveWantActivity(activity, userId)
            }
        }
    }

    /** Guest → authenticated: rewrite user_id on all local rows from local → auth. Call after successful sign-in/sign-up. */
    suspend fun migrateLocalToAuthenticated(authUserId: String) {
        val localId = userIdentityProvider.localUserId()
        if (localId == authUserId) return
        habitRepository.migrateUserId(localId, authUserId)
        habitLogRepository.migrateUserId(localId, authUserId)
        wantLogRepository.migrateUserId(localId, authUserId)
        wantActivityRepository.migrateUserId(localId, authUserId)
    }

    /** Authenticated → guest: wipe everything owned by auth user. Call inside logout flow after unsynced rows are handled. */
    suspend fun clearAuthenticatedUserData(authUserId: String) {
        habitRepository.clearForUser(authUserId)
        habitLogRepository.clearForUser(authUserId)
        wantLogRepository.clearForUser(authUserId)
        wantActivityRepository.clearForUser(authUserId)
    }
}
```

### Step 8: Dynamic startDestination in AppNavigation

Replace the NavHost construction:

```kotlin
@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.seedLocalDataIfEmpty()
        val userId = container.currentUserId()
        startDestination = if (container.isOnboardedUseCase.execute(userId)) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }

    val start = startDestination ?: return  // composing a loader is fine too
    NavHost(navController = navController, startDestination = start) {
        composable(Screen.Auth.route) {
            val vm = viewModel { AuthViewModel(container) }
            AuthScreen(
                viewModel = vm,
                onSuccess = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Onboarding.route) { /* unchanged */ }
        composable(Screen.Home.route) {
            val vm = viewModel { HomeViewModel(container) }
            HomeScreen(
                viewModel = vm,
                onLogHabit = { habitId -> navController.navigate(Screen.LogHabit.route(habitId)) },
                onLogWant = { activityId -> navController.navigate(Screen.LogWant.route(activityId)) },
                onSignIn = { navController.navigate(Screen.Auth.route) },
            )
        }
        composable(Screen.LogHabit.route, ...) { /* unchanged */ }
        composable(Screen.LogWant.route, ...) { /* unchanged */ }
    }
}
```

Imports to add: `androidx.compose.runtime.remember`, `mutableStateOf`, `setValue`, `getValue`, `LaunchedEffect`. Drop the `AuthNavigation` collection block (Auth is no longer the entry point).

### Step 9: AuthViewModel rework

- Drop the `init {}` auto-navigate block.
- Replace `AuthNavigation` sealed interface with `sealed interface AuthEvent { object Success : AuthEvent }`.
- On successful sign-in/sign-up:
  - call `container.migrateLocalToAuthenticated(session.userId)` (merges guest data into the authenticated account).
  - call `container.seedLocalDataIfEmpty()` (safety net — want activities may not have seeded under the new user id).
  - emit `AuthEvent.Success` → screen calls `onSuccess()` → pops back to Home.
- `AuthScreen` signature: `onSuccess: () -> Unit, onBack: () -> Unit`.

### Step 10: Home sign-in CTA

- `HomeScreen` signature adds `onSignIn: () -> Unit`.
- In the `TopAppBar`, show an "IconButton / TextButton: Sign in" action when `uiState.isAuthenticated == false`. When authenticated, show email + sign-out (sign-out UI is Phase 4 scope — for Phase 2 leave a placeholder "Sign out — coming in Phase 4" that just logs).
- `HomeViewModel.HomeUiState` gains `isAuthenticated: Boolean = false`. Populate from `container.isAuthenticated()` inside `load()`.

### Step 11: OnboardingViewModel rework

Replace `val userId = container.authRepository.currentUserId() ?: run { ...error... }` with `val userId = container.currentUserId()`. The "Not logged in" error path goes away — guest flow is supported.

### Step 12: Build + test + commit

```bash
./gradlew :mobile:shared:compileDebugKotlinAndroid
./gradlew :mobile:androidApp:assembleDebug
./gradlew :mobile:shared:allTests
git add mobile/shared/src mobile/androidApp/src
git commit -m "feat: guest mode — optional auth with local user id, migration on sign-in"
```

**Logout UI is deferred to Phase 4** (sync layer owns the push-before-wipe + confirmation dialog). Phase 2 ships with sign-in-only; sign-out path stays as a TODO.

---

## Task 9: Log Habit Screen + ViewModel

> **SUPERSEDED in later UX passes.** The dedicated Log Habit screen is gone. Current flow lives entirely on Home via `HomeViewModel.tapHabit` / `cancelPending`:
>
> 1. First pass (commit 2ae89be): dedicated screen removed, tapping a habit card instant-logged `threshold_per_point` with a 5-minute bottom-bar undo.
> 2. Second pass (commit c3bd32e): switched to a **3-second tap-to-commit batch**. Each tap bumps an in-card `×N` counter and resets a 3-second countdown with an inline Cancel. Commit writes one log with `quantity = threshold × N`; snackbar shows `"+N pts — Habit name"`. Cancel drops the pending state, no log written. Post-commit undo removed entirely — the 3s window is the only reversal affordance.
>
> Task 9's original file layout (LogHabitScreen / LogHabitViewModel) is deleted; see git history at commit 8acb379 for the superseded code. `LogHabitUseCase` (shared module) is unchanged — it still computes points from quantity + handles the daily cap + returns `LogHabitStatus`.

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitScreen.kt`

> **Guest-mode retrofit:** Replace every `container.authRepository.currentUserId() ?: return…` with `container.currentUserId()`. Auth is optional (Task 8.5); `currentUserId()` always returns a non-null id (falls back to `localUserId`).

- [ ] **Step 1: Write full LogHabitViewModel.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UndoState(val logId: String, val secondsRemaining: Int)

sealed interface LogHabitUiState {
    object Idle : LogHabitUiState
    object Loading : LogHabitUiState
    data class Success(val pointsEarned: Int, val logId: String) : LogHabitUiState
    data class Error(val message: String) : LogHabitUiState
}

class LogHabitViewModel(
    private val habitId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _habit = MutableStateFlow<Habit?>(null)
    val habit: StateFlow<Habit?> = _habit.asStateFlow()

    private val _quantityInput = MutableStateFlow("")
    val quantityInput: StateFlow<String> = _quantityInput.asStateFlow()

    private val _uiState = MutableStateFlow<LogHabitUiState>(LogHabitUiState.Idle)
    val uiState: StateFlow<LogHabitUiState> = _uiState.asStateFlow()

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            val userId = container.authRepository.currentUserId() ?: return@launch
            _habit.value = container.habitRepository.getHabitsForUser(userId)
                .firstOrNull { it.id == habitId }
        }
    }

    fun onQuantityChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _quantityInput.value = value
        }
    }

    fun log() {
        val userId = container.authRepository.currentUserId() ?: return
        val quantity = _quantityInput.value.toDoubleOrNull() ?: run {
            _uiState.value = LogHabitUiState.Error("Enter a valid number")
            return
        }
        if (quantity <= 0) {
            _uiState.value = LogHabitUiState.Error("Quantity must be greater than 0")
            return
        }
        viewModelScope.launch {
            _uiState.value = LogHabitUiState.Loading
            container.logHabitUseCase.execute(userId, habitId, quantity)
                .onSuccess { result ->
                    _uiState.value = LogHabitUiState.Success(result.pointsEarned, result.log.id)
                    startUndoTimer(result.log.id)
                }
                .onFailure { e ->
                    _uiState.value = LogHabitUiState.Error(e.message ?: "Failed to log")
                }
        }
    }

    fun undo(logId: String) {
        val userId = container.authRepository.currentUserId() ?: return
        viewModelScope.launch {
            container.undoHabitLogUseCase.execute(logId, userId)
            undoJob?.cancel()
            _undoState.value = null
            _uiState.value = LogHabitUiState.Idle
            _quantityInput.value = ""
        }
    }

    private fun startUndoTimer(logId: String) {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            for (seconds in 300 downTo 0) {
                _undoState.value = UndoState(logId, seconds)
                delay(1000L)
            }
            _undoState.value = null
        }
    }
}
```

- [ ] **Step 2: Write full LogHabitScreen.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.android.ui.theme.streakCompleteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogHabitScreen(viewModel: LogHabitViewModel, onBack: () -> Unit) {
    val habit by viewModel.habit.collectAsState()
    val quantityInput by viewModel.quantityInput.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val undoState by viewModel.undoState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit?.name ?: "Log Habit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xxl)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            habit?.let { h ->
                Text(h.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Threshold: ${h.thresholdPerPoint.toInt()} ${h.unit} = 1 point",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xxl))

                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text("How many ${h.unit}?") },
                    placeholder = { Text("${h.thresholdPerPoint.toInt()}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.log() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                when (val state = uiState) {
                    is LogHabitUiState.Error -> {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is LogHabitUiState.Success -> {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            if (state.pointsEarned > 0) "+${state.pointsEarned} pts earned!"
                            else "Logged (below threshold, 0 pts)",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.pointsEarned > 0) streakCompleteColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {}
                }

                Spacer(Modifier.height(Spacing.xl))

                if (uiState is LogHabitUiState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = viewModel::log,
                        enabled = uiState !is LogHabitUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Log")
                    }
                }

                undoState?.let { undo ->
                    Spacer(Modifier.height(Spacing.md))
                    val mins = undo.secondsRemaining / 60
                    val secs = undo.secondsRemaining % 60
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Undo available: %d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { viewModel.undo(undo.logId) }) {
                            Text("Undo")
                        }
                    }
                }
            } ?: CircularProgressIndicator()
        }
    }
}
```

- [ ] **Step 3: Build to verify log habit UI compiles**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitViewModel.kt
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogHabitScreen.kt
rtk git commit -m "feat: add Log Habit screen with point display and 5-min undo"
```

---

## Task 10: Log Want Screen + ViewModel

> **SUPERSEDED.** Dedicated Log Want screen is gone — spending lives on Home via `HomeViewModel.tapWant` / `cancelPendingWant` using the same 3-second batch pattern as habits (see Task 9 note). `DeviceMode` toggle UI removed in Phase 2; default `OTHER` is hard-coded until Phase 5 adds the overlay flow back. `LogWantUseCase` (shared module) unchanged — still enforces no-negative-balance lock and returns `InsufficientPointsException` on overdraw. Original screen code in git history at commit 6240952.

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantScreen.kt`

> **Guest-mode retrofit:** Replace every `container.authRepository.currentUserId() ?: return…` with `container.currentUserId()`. Same rationale as Task 9.

- [ ] **Step 1: Write full LogWantViewModel.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LogWantUiState {
    object Idle : LogWantUiState
    object Loading : LogWantUiState
    data class Success(val pointsSpent: Int, val logId: String) : LogWantUiState
    data class Error(val message: String) : LogWantUiState
}

class LogWantViewModel(
    private val activityId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _activity = MutableStateFlow<WantActivity?>(null)
    val activity: StateFlow<WantActivity?> = _activity.asStateFlow()

    private val _quantityInput = MutableStateFlow("")
    val quantityInput: StateFlow<String> = _quantityInput.asStateFlow()

    private val _deviceMode = MutableStateFlow(DeviceMode.OTHER)
    val deviceMode: StateFlow<DeviceMode> = _deviceMode.asStateFlow()

    private val _uiState = MutableStateFlow<LogWantUiState>(LogWantUiState.Idle)
    val uiState: StateFlow<LogWantUiState> = _uiState.asStateFlow()

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            val userId = container.authRepository.currentUserId() ?: return@launch
            _activity.value = container.wantActivityRepository.getWantActivities(userId)
                .firstOrNull { it.id == activityId }
        }
    }

    fun onQuantityChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _quantityInput.value = value
        }
    }

    fun onDeviceModeChange(mode: DeviceMode) {
        _deviceMode.value = mode
    }

    fun log() {
        val userId = container.authRepository.currentUserId() ?: return
        val quantity = _quantityInput.value.toDoubleOrNull() ?: run {
            _uiState.value = LogWantUiState.Error("Enter a valid number")
            return
        }
        if (quantity <= 0) {
            _uiState.value = LogWantUiState.Error("Quantity must be greater than 0")
            return
        }
        viewModelScope.launch {
            _uiState.value = LogWantUiState.Loading
            container.logWantUseCase.execute(userId, activityId, quantity, _deviceMode.value)
                .onSuccess { result ->
                    _uiState.value = LogWantUiState.Success(result.pointsSpent, result.log.id)
                    startUndoTimer(result.log.id)
                }
                .onFailure { e ->
                    _uiState.value = LogWantUiState.Error(e.message ?: "Failed to log")
                }
        }
    }

    fun undo(logId: String) {
        val userId = container.authRepository.currentUserId() ?: return
        viewModelScope.launch {
            container.undoWantLogUseCase.execute(logId, userId)
            undoJob?.cancel()
            _undoState.value = null
            _uiState.value = LogWantUiState.Idle
            _quantityInput.value = ""
        }
    }

    private fun startUndoTimer(logId: String) {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            for (seconds in 300 downTo 0) {
                _undoState.value = UndoState(logId, seconds)
                delay(1000L)
            }
            _undoState.value = null
        }
    }
}
```

- [ ] **Step 2: Write full LogWantScreen.kt**

Replace the stub with:

```kotlin
package com.habittracker.android.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.domain.model.DeviceMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWantScreen(viewModel: LogWantViewModel, onBack: () -> Unit) {
    val activity by viewModel.activity.collectAsState()
    val quantityInput by viewModel.quantityInput.collectAsState()
    val deviceMode by viewModel.deviceMode.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val undoState by viewModel.undoState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activity?.name ?: "Log Want") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xxl)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            activity?.let { act ->
                Text(act.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.sm))
                val rateText = if (act.costPerUnit >= 1.0) {
                    "${act.costPerUnit.toInt()} pt per ${act.unit}"
                } else {
                    "1 pt per ${(1.0 / act.costPerUnit).toInt()} ${act.unit}"
                }
                Text(rateText, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(Spacing.xxl))

                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text("How many ${act.unit}?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.log() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Spacing.xl))

                Text("Device", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    FilterChip(
                        selected = deviceMode == DeviceMode.THIS_DEVICE,
                        onClick = { viewModel.onDeviceModeChange(DeviceMode.THIS_DEVICE) },
                        label = { Text("This device") },
                    )
                    FilterChip(
                        selected = deviceMode == DeviceMode.OTHER,
                        onClick = { viewModel.onDeviceModeChange(DeviceMode.OTHER) },
                        label = { Text("Other / physical") },
                    )
                }

                when (val state = uiState) {
                    is LogWantUiState.Error -> {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is LogWantUiState.Success -> {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "-${state.pointsSpent} pts spent",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {}
                }

                Spacer(Modifier.height(Spacing.xl))

                if (uiState is LogWantUiState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = viewModel::log,
                        enabled = uiState !is LogWantUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Log")
                    }
                }

                undoState?.let { undo ->
                    Spacer(Modifier.height(Spacing.md))
                    val mins = undo.secondsRemaining / 60
                    val secs = undo.secondsRemaining % 60
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Undo available: %d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { viewModel.undo(undo.logId) }) {
                            Text("Undo")
                        }
                    }
                }
            } ?: CircularProgressIndicator()
        }
    }
}
```

- [ ] **Step 3: Run full test suite + build APK**

```bash
./gradlew :mobile:shared:allTests
```

Expected: BUILD SUCCESSFUL, 27 tests pass

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantViewModel.kt
rtk git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/log/LogWantScreen.kt
rtk git commit -m "feat: add Log Want screen with device mode toggle and 5-min undo"
```

---

## End-to-End Verification Checklist

After all 10 tasks complete, verify manually on Android emulator/device:

- [ ] App launches → Auth screen shown
- [ ] Sign up with test email → navigates to Onboarding
- [ ] Identity picker shows 8 identities → select one → Continue
- [ ] Habit picker shows habits for selected identity → pick 2 → Continue
- [ ] Want activity picker shows 15 activities → pick some → Get Started
- [ ] Home screen shows point balance (0 pts) and today's habits with empty progress bars
- [ ] Tap a habit → Log Habit screen → enter quantity → Log → points earned shown
- [ ] Undo button visible with countdown timer → tap Undo → log removed, balance returns
- [ ] Tap want activity → Log Want screen → enter quantity → select device mode → Log → points spent shown
- [ ] Undo want log within 5 min → balance restored
- [ ] Return to Home → point balance updated
- [ ] Kill app and relaunch → Home screen shown (skips auth), data persists

---

## Phase 4 follow-ups from Phase 2

Deferred auth/sync work; implement in Phase 4:

1. **Reactive auth state propagation (landed late in Phase 2).** `AppContainer` now exposes `authState: StateFlow<AuthState>` and `refreshAuthState()`. After sign-in, `AuthViewModel` calls `refreshAuthState()` and HomeViewModel `flatMapLatest`es on `authState` so the Home re-subscribes with the new user id. Keep this pattern for Phase 4 sign-out: after `clearAuthenticatedUserData` + session clear, call `refreshAuthState()` so Home reverts to guest view.
2. **Email-confirmation UX.** `SupabaseAuthRepository.signUp` currently throws `"Sign up returned no session"` when the Supabase project has email confirmation enabled. Replace with a `SignUpResult` sealed type: `SignedIn(UserSession)` | `ConfirmationRequired(email)`. `AuthViewModel` surfaces a "Check your email to confirm your account" state; no migration happens until the user confirms + signs in. Dev shortcut for Phase 2 was disabling "Confirm email" in the Supabase dashboard.
3. **Google OAuth.** Add `supabase-oauth` dep, Android deep-link handler for return URI (`com.habittracker.android://auth-callback`), "Continue with Google" primary button on AuthScreen (above email/password fields). Reuses the same migrate → refreshAuthState → navigate flow on success.
4. **Logout UI.** Confirmation dialog, push-unsynced-rows guard, session clear, `clearAuthenticatedUserData` (already wired in AppContainer), `refreshAuthState` → Home reverts to guest.
5. **Sync push/pull.** Worker that reads rows where `synced_at IS NULL`, pushes to Supabase under `auth.uid()`, stamps `synced_at` on success. Pull since `last_pull_timestamp` and merge by `id`.
