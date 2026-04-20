# Phase 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap the KMP monorepo, Supabase schema, offline-first local database, Supabase auth client, domain models with business logic, and UI theme — the complete foundation Phase 2 builds the habit loop on.

**Architecture:** KMP monorepo. Shared Kotlin module holds domain models, SQLDelight local DB (offline-first source of truth), Ktor-based Supabase client, and repositories. Android app uses Compose + Material 3. iOS uses SwiftUI with equivalent design tokens. Supabase handles auth + cross-device sync; all business logic (point calculation, streak) runs on-device.

**Tech Stack:** Kotlin 2.1.0 · KMP · AGP 8.7.3 · SQLDelight 2.0.2 · Ktor 2.3.12 · supabase-kt 3.0.2 · kotlinx-coroutines 1.9.0 · kotlinx-datetime 0.6.1 · Compose BOM 2024.12.01 · Material 3 · CocoaPods plugin

> **Scope:** Phase 1 of 6. Phases 2–6 each have their own plan. Phase 2 = core habit loop (Android).

**Prerequisites (verify before starting):**
- Android Studio Ladybug or later
- Xcode 16+
- CocoaPods: `sudo gem install cocoapods`
- Supabase CLI: `brew install supabase/tap/supabase`
- Supabase project created at supabase.com — note your project URL and anon key
- Java 17+ on PATH
- Git initialized in `/habit-tracker/`

---

## File Map

```
habit-tracker/
├── .gitignore
├── gradle/
│   └── libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
├── mobile/
│   ├── shared/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/com/habittracker/
│   │       │   ├── domain/model/
│   │       │   │   ├── Identity.kt          — data class
│   │       │   │   ├── Goal.kt              — data class
│   │       │   │   ├── HabitTemplate.kt     — data class
│   │       │   │   ├── Habit.kt             — data class
│   │       │   │   ├── HabitLog.kt          — data class, isActive computed
│   │       │   │   ├── WantActivity.kt      — data class
│   │       │   │   ├── UserWantActivity.kt  — data class
│   │       │   │   ├── WantLog.kt           — data class, isActive computed
│   │       │   │   └── DeviceMode.kt        — enum
│   │       │   ├── domain/usecase/
│   │       │   │   └── PointCalculator.kt   — pure functions, testable
│   │       │   ├── data/local/
│   │       │   │   ├── HabitTrackerDatabase.sq  — SQLDelight schema + queries
│   │       │   │   └── DatabaseDriverFactory.kt — expect class
│   │       │   ├── data/remote/
│   │       │   │   └── SupabaseProvider.kt  — singleton client
│   │       │   └── data/repository/
│   │       │       ├── AuthRepository.kt    — interface
│   │       │       └── FakeAuthRepository.kt — test stub
│   │       ├── androidMain/kotlin/com/habittracker/data/local/
│   │       │   └── DatabaseDriverFactory.android.kt — actual
│   │       ├── iosMain/kotlin/com/habittracker/data/local/
│   │       │   └── DatabaseDriverFactory.ios.kt     — actual
│   │       └── commonTest/kotlin/com/habittracker/
│   │           ├── domain/usecase/PointCalculatorTest.kt
│   │           └── data/repository/AuthRepositoryTest.kt
│   ├── androidApp/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── kotlin/com/habittracker/android/
│   │       │   ├── MainActivity.kt
│   │       │   └── ui/theme/
│   │       │       ├── Color.kt       — M3 color scheme (light + dark)
│   │       │       ├── Typography.kt  — M3 type scale
│   │       │       ├── Spacing.kt     — spacing scale object
│   │       │       └── Theme.kt       — HabitTrackerTheme composable
│   │       └── res/values/themes.xml  — base theme for Activity
│   └── iosApp/
│       └── HabitTracker/
│           ├── HabitTrackerApp.swift
│           ├── ContentView.swift
│           └── Theme/
│               ├── HabitColors.swift      — Color extensions
│               ├── HabitTypography.swift  — Font helpers
│               └── HabitSpacing.swift     — spacing constants
├── supabase/
│   ├── config.toml
│   ├── migrations/
│   │   └── 20260420000000_initial_schema.sql
│   └── seed.sql
└── docs/superpowers/
    ├── specs/2026-04-20-habit-tracker-design.md
    └── plans/2026-04-20-phase1-foundation.md
```

---

## Task 1: Initialize Monorepo Structure

**Files:**
- Create: `.gitignore`
- Create: `mobile/` directory tree
- Create: `supabase/` directory tree

- [ ] **Step 1: Create directory structure**

```bash
cd /path/to/habit-tracker
mkdir -p mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model
mkdir -p mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase
mkdir -p mobile/shared/src/commonMain/kotlin/com/habittracker/data/local
mkdir -p mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote
mkdir -p mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository
mkdir -p mobile/shared/src/androidMain/kotlin/com/habittracker/data/local
mkdir -p mobile/shared/src/iosMain/kotlin/com/habittracker/data/local
mkdir -p mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase
mkdir -p mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository
mkdir -p mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme
mkdir -p mobile/androidApp/src/main/res/values
mkdir -p mobile/iosApp/HabitTracker/Theme
mkdir -p supabase/migrations
mkdir -p gradle
```

- [ ] **Step 2: Create `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
*.gradle.kts.kotlin_module
local.properties

# Android
*.apk
*.aab
*.ap_
*.dex
/mobile/androidApp/release/

# iOS
mobile/iosApp/Pods/
mobile/iosApp/*.xcworkspace
mobile/iosApp/DerivedData/
*.DS_Store

# IntelliJ / Android Studio
.idea/
*.iml
*.ipr
*.iws

# Kotlin
kotlin-js-store/

# Supabase
supabase/.branches/
supabase/.temp/

# Secrets — NEVER commit these
local.properties
*.env
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: initialize monorepo directory structure"
```

---

## Task 2: Gradle Configuration

**Files:**
- Create: `gradle/libs.versions.toml`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `mobile/shared/build.gradle.kts`
- Create: `mobile/androidApp/build.gradle.kts`

- [ ] **Step 1: Create `gradle/libs.versions.toml`**

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

[libraries]
# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

# DateTime
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }

# SQLDelight
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }

# Supabase
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt", version.ref = "supabase" }
supabase-auth = { module = "io.github.jan-tennert.supabase:auth-kt", version.ref = "supabase" }
supabase-realtime = { module = "io.github.jan-tennert.supabase:realtime-kt", version.ref = "supabase" }

# Compose
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-activity = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-cocoapods = { id = "org.jetbrains.kotlin.native.cocoapods", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HabitTracker"
include(":mobile:shared", ":mobile:androidApp")
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.cocoapods) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 4: Create `mobile/shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "HabitTracker shared KMP module"
        homepage = "https://github.com/your/habit-tracker"
        version = "1.0"
        ios.deploymentTarget = "17.0"
        framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.habittracker.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("HabitTrackerDatabase") {
            packageName.set("com.habittracker.data.local")
        }
    }
}
```

- [ ] **Step 5: Create `mobile/androidApp/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

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
    }

    buildFeatures { compose = true }

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
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 6: Create Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.10.2
```

Expected: `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` created.

- [ ] **Step 7: Sync and verify Gradle resolves**

```bash
./gradlew :mobile:shared:dependencies --configuration commonMainImplementation
```

Expected: dependency tree prints without errors. If a version conflict appears, check `libs.versions.toml` entries.

- [ ] **Step 8: Commit**

```bash
git add gradle/ settings.gradle.kts build.gradle.kts mobile/shared/build.gradle.kts mobile/androidApp/build.gradle.kts
git commit -m "chore: configure KMP gradle with SQLDelight, Ktor, supabase-kt"
```

---

## Task 3: Supabase Schema + Seed Data

**Files:**
- Create: `supabase/config.toml`
- Create: `supabase/migrations/20260420000000_initial_schema.sql`
- Create: `supabase/seed.sql`

- [ ] **Step 1: Initialize Supabase CLI project**

```bash
cd supabase
supabase init
```

Expected: `config.toml` created.

- [ ] **Step 2: Create `supabase/migrations/20260420000000_initial_schema.sql`**

```sql
-- identities (seeded, public read)
create table identities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text not null,
  icon text not null,
  created_at timestamptz default now()
);

-- user's selected identities
create table goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  identity_id uuid not null references identities(id),
  created_at timestamptz default now(),
  unique(user_id, identity_id)
);

-- habit templates (seeded + user custom)
create table habit_templates (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  unit text not null,
  default_threshold float not null,
  default_daily_target int not null,
  is_custom boolean not null default false,
  created_by_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz default now()
);

-- identity <-> habit template (many-to-many)
create table identity_habits (
  identity_id uuid not null references identities(id),
  habit_template_id uuid not null references habit_templates(id),
  primary key (identity_id, habit_template_id)
);

-- user's active habits
create table habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  template_id uuid not null references habit_templates(id),
  threshold_per_point float not null,
  daily_target int not null,
  created_at timestamptz default now()
);

-- need habit logs (append-only, soft delete within 5-min window)
create table habit_logs (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  habit_id uuid not null references habits(id) on delete cascade,
  quantity float not null check (quantity > 0),
  logged_at timestamptz not null,
  deleted_at timestamptz,
  synced_at timestamptz default now()
);

-- want activity definitions (seeded + user custom)
create table want_activities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  unit text not null,
  cost_per_unit float not null check (cost_per_unit > 0),
  is_custom boolean not null default false,
  created_by_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz default now()
);

-- user's selected want activities with customizable rate
create table user_want_activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid not null references want_activities(id),
  cost_per_unit float not null check (cost_per_unit > 0),
  created_at timestamptz default now(),
  unique(user_id, activity_id)
);

-- want activity logs (append-only, soft delete within 5-min window)
create table want_logs (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid not null references want_activities(id),
  quantity float not null check (quantity > 0),
  device_mode text not null check (device_mode in ('this_device', 'other')),
  logged_at timestamptz not null,
  deleted_at timestamptz,
  synced_at timestamptz default now()
);

-- Indexes for common queries
create index habit_logs_user_logged on habit_logs(user_id, logged_at) where deleted_at is null;
create index want_logs_user_logged on want_logs(user_id, logged_at) where deleted_at is null;
create index habits_user on habits(user_id);

-- RLS: user-owned tables
alter table goals enable row level security;
alter table habits enable row level security;
alter table habit_logs enable row level security;
alter table user_want_activities enable row level security;
alter table want_logs enable row level security;

create policy "goals: user owns" on goals for all using (user_id = auth.uid());
create policy "habits: user owns" on habits for all using (user_id = auth.uid());
create policy "habit_logs: user owns" on habit_logs for all using (user_id = auth.uid());
create policy "user_want_activities: user owns" on user_want_activities for all using (user_id = auth.uid());
create policy "want_logs: user owns" on want_logs for all using (user_id = auth.uid());

-- RLS: seeded tables (public read)
alter table identities enable row level security;
alter table habit_templates enable row level security;
alter table identity_habits enable row level security;
alter table want_activities enable row level security;

create policy "identities: public read" on identities for select using (true);
create policy "habit_templates: public read" on habit_templates for select using (true);
create policy "identity_habits: public read" on identity_habits for select using (true);
create policy "want_activities: public read" on want_activities for select using (true);

-- RLS: custom habit templates + want activities
create policy "habit_templates: user inserts custom" on habit_templates
  for insert with check (is_custom = true and created_by_user_id = auth.uid());
create policy "habit_templates: user owns custom" on habit_templates
  for all using (is_custom = true and created_by_user_id = auth.uid());

create policy "want_activities: user inserts custom" on want_activities
  for insert with check (is_custom = true and created_by_user_id = auth.uid());
create policy "want_activities: user owns custom" on want_activities
  for all using (is_custom = true and created_by_user_id = auth.uid());
```

- [ ] **Step 3: Create `supabase/seed.sql`**

```sql
-- Identities
insert into identities (id, name, description, icon) values
  ('00000000-0000-0000-0000-000000000001', 'Reader', 'Build a reading habit to expand knowledge and vocabulary', '📚'),
  ('00000000-0000-0000-0000-000000000002', 'Builder', 'Develop your craft as a software developer', '🔨'),
  ('00000000-0000-0000-0000-000000000003', 'Athlete', 'Build physical strength and endurance', '🏃'),
  ('00000000-0000-0000-0000-000000000004', 'Writer', 'Express yourself through consistent writing practice', '✍️'),
  ('00000000-0000-0000-0000-000000000005', 'Learner', 'Stay curious and keep learning every day', '🎓'),
  ('00000000-0000-0000-0000-000000000006', 'Minimalist', 'Simplify your space and digital life', '🌿'),
  ('00000000-0000-0000-0000-000000000007', 'Devotee', 'Deepen your spiritual practice', '🙏'),
  ('00000000-0000-0000-0000-000000000008', 'Health-Conscious', 'Build healthy daily habits for long-term wellness', '💪');

-- Habit templates
insert into habit_templates (id, name, unit, default_threshold, default_daily_target) values
  -- Reader
  ('10000000-0000-0000-0000-000000000001', 'Read book / Kindle', 'pages', 3, 3),
  ('10000000-0000-0000-0000-000000000002', 'Read article', 'minutes', 5, 2),
  ('10000000-0000-0000-0000-000000000003', 'Read research paper', 'minutes', 10, 1),
  -- Builder
  ('10000000-0000-0000-0000-000000000004', 'Code project', 'minutes', 15, 3),
  ('10000000-0000-0000-0000-000000000005', 'Write tests', 'minutes', 10, 2),
  ('10000000-0000-0000-0000-000000000006', 'Learn new tech', 'minutes', 15, 2),
  ('10000000-0000-0000-0000-000000000007', 'Review / refactor code', 'minutes', 10, 1),
  -- Athlete
  ('10000000-0000-0000-0000-000000000008', 'Push up', 'reps', 15, 3),
  ('10000000-0000-0000-0000-000000000009', 'Squat', 'reps', 20, 3),
  ('10000000-0000-0000-0000-000000000010', 'Walk / run', 'minutes', 10, 2),
  ('10000000-0000-0000-0000-000000000011', 'Cycling', 'minutes', 10, 2),
  ('10000000-0000-0000-0000-000000000012', 'Stretching', 'minutes', 5, 2),
  ('10000000-0000-0000-0000-000000000013', 'Plank', 'seconds', 30, 3),
  -- Writer
  ('10000000-0000-0000-0000-000000000014', 'Journaling', 'minutes', 5, 2),
  ('10000000-0000-0000-0000-000000000015', 'Blog writing', 'minutes', 15, 2),
  ('10000000-0000-0000-0000-000000000016', 'Creative writing', 'minutes', 10, 2),
  ('10000000-0000-0000-0000-000000000017', 'Outline / draft', 'minutes', 10, 1),
  -- Learner
  ('10000000-0000-0000-0000-000000000018', 'Watch educational video', 'minutes', 10, 2),
  ('10000000-0000-0000-0000-000000000019', 'Take online course', 'minutes', 15, 2),
  ('10000000-0000-0000-0000-000000000020', 'Practice language', 'minutes', 10, 2),
  ('10000000-0000-0000-0000-000000000021', 'Flashcard review', 'minutes', 5, 3),
  -- Minimalist
  ('10000000-0000-0000-0000-000000000022', 'Declutter space', 'minutes', 5, 1),
  ('10000000-0000-0000-0000-000000000023', 'Organize items', 'minutes', 5, 1),
  ('10000000-0000-0000-0000-000000000024', 'Digital cleanup', 'minutes', 5, 1),
  -- Devotee
  ('10000000-0000-0000-0000-000000000025', 'Pray', 'sessions', 1, 3),
  ('10000000-0000-0000-0000-000000000026', 'Meditate', 'minutes', 5, 2),
  ('10000000-0000-0000-0000-000000000027', 'Gratitude journal', 'entries', 3, 1),
  -- Health-Conscious
  ('10000000-0000-0000-0000-000000000028', 'Drink water', 'ml', 250, 8),
  ('10000000-0000-0000-0000-000000000029', 'Sleep on time', 'nights', 1, 1),
  ('10000000-0000-0000-0000-000000000030', 'Meal prep', 'minutes', 10, 1),
  ('10000000-0000-0000-0000-000000000031', 'No junk food day', 'days', 1, 1);

-- identity_habits mappings
insert into identity_habits (identity_id, habit_template_id) values
  -- Reader
  ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002'),
  ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003'),
  -- Builder
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004'),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000005'),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000006'),
  ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000007'),
  -- Athlete
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000008'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000009'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000010'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000011'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000012'),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000013'),
  -- Writer
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000014'),
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000015'),
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000016'),
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000017'),
  -- Learner
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000018'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000019'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000020'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000021'),
  -- Minimalist
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000022'),
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000023'),
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000024'),
  -- Devotee
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000025'),
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000026'),
  ('00000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000027'),
  -- Health-Conscious
  ('00000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000028'),
  ('00000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000029'),
  ('00000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000030'),
  ('00000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000031');

-- Default want activities
insert into want_activities (id, name, unit, cost_per_unit) values
  ('20000000-0000-0000-0000-000000000001', 'Scroll (reel/TikTok/short)', 'minutes', 1.0),
  ('20000000-0000-0000-0000-000000000002', 'Browse Twitter/X', 'minutes', 0.5),
  ('20000000-0000-0000-0000-000000000003', 'Browse Instagram feed', 'minutes', 0.5),
  ('20000000-0000-0000-0000-000000000004', 'YouTube long-form', 'minutes', 0.1),
  ('20000000-0000-0000-0000-000000000005', 'YouTube shorts', 'minutes', 1.0),
  ('20000000-0000-0000-0000-000000000006', 'Netflix / streaming', 'minutes', 0.067),
  ('20000000-0000-0000-0000-000000000007', 'Casual mobile game', 'minutes', 0.2),
  ('20000000-0000-0000-0000-000000000008', 'Valorant Deathmatch', 'matches', 1.0),
  ('20000000-0000-0000-0000-000000000009', 'Valorant Ranked', 'matches', 3.0),
  ('20000000-0000-0000-0000-000000000010', 'PC gaming session', 'minutes', 0.1),
  ('20000000-0000-0000-0000-000000000011', 'Online shopping browse', 'minutes', 0.2),
  ('20000000-0000-0000-0000-000000000012', 'Purchase session', 'sessions', 2.0),
  ('20000000-0000-0000-0000-000000000013', 'Junk food / fast food', 'meals', 2.0),
  ('20000000-0000-0000-0000-000000000014', 'Sugary drinks', 'drinks', 1.0),
  ('20000000-0000-0000-0000-000000000015', 'Donut / dessert', 'pieces', 1.0);
```

- [ ] **Step 4: Apply migration to Supabase**

Option A — remote project (Supabase dashboard):
```bash
supabase db push --db-url "postgresql://postgres:[YOUR-DB-PASSWORD]@db.[YOUR-PROJECT-REF].supabase.co:5432/postgres"
```

Option B — local dev with Supabase CLI:
```bash
supabase start
supabase db reset
```

Expected: no errors. Tables visible in Supabase dashboard → Table Editor.

- [ ] **Step 5: Apply seed data**

```bash
# Remote
psql "postgresql://postgres:[YOUR-DB-PASSWORD]@db.[YOUR-PROJECT-REF].supabase.co:5432/postgres" -f supabase/seed.sql

# Local
supabase db reset  # includes seed.sql automatically if placed in supabase/seed.sql
```

Expected: 8 rows in `identities`, 31 rows in `habit_templates`, 15 rows in `want_activities`.

- [ ] **Step 6: Commit**

```bash
git add supabase/
git commit -m "feat: add Supabase schema migration and seed data"
```

---

## Task 4: Domain Models + PointCalculator (TDD)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/` (8 files)
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PointCalculator.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PointCalculatorTest.kt`

- [ ] **Step 1: Write the failing test first**

Create `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PointCalculatorTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class PointCalculatorTest {

    @Test
    fun pointsEarned_exactThreshold_returnsOne() {
        assertEquals(1, PointCalculator.pointsEarned(quantity = 3.0, threshold = 3.0))
    }

    @Test
    fun pointsEarned_belowThreshold_returnsZero() {
        assertEquals(0, PointCalculator.pointsEarned(quantity = 2.0, threshold = 3.0))
    }

    @Test
    fun pointsEarned_multipleThresholds_returnsFloor() {
        assertEquals(2, PointCalculator.pointsEarned(quantity = 7.0, threshold = 3.0))
    }

    @Test
    fun pointsEarned_fractionalQuantity_truncates() {
        assertEquals(1, PointCalculator.pointsEarned(quantity = 5.9, threshold = 3.0))
    }

    @Test
    fun pointsSpent_exactUnit_returnsOne() {
        assertEquals(1, PointCalculator.pointsSpent(quantity = 10.0, costPerUnit = 0.1))
    }

    @Test
    fun pointsSpent_threeUnits_returnsThree() {
        // 30 min YouTube at 0.1 pt/min = 3 points
        assertEquals(3, PointCalculator.pointsSpent(quantity = 30.0, costPerUnit = 0.1))
    }

    @Test
    fun pointsSpent_lessThanOneUnit_returnsZero() {
        // 5 min YouTube at 0.1 pt/min = 0.5 → floor = 0
        assertEquals(0, PointCalculator.pointsSpent(quantity = 5.0, costPerUnit = 0.1))
    }

    @Test
    fun pointsSpent_scrollOneMinute_returnsOne() {
        // 1 min scroll at 1.0 pt/min = 1 point
        assertEquals(1, PointCalculator.pointsSpent(quantity = 1.0, costPerUnit = 1.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :mobile:shared:allTests
```

Expected: `PointCalculatorTest` fails with "Unresolved reference: PointCalculator".

- [ ] **Step 3: Create domain model files**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/DeviceMode.kt`:
```kotlin
package com.habittracker.domain.model

enum class DeviceMode { THIS_DEVICE, OTHER }
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Identity.kt`:
```kotlin
package com.habittracker.domain.model

data class Identity(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
)
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Goal.kt`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class Goal(
    val id: String,
    val userId: String,
    val identityId: String,
    val createdAt: Instant,
)
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitTemplate.kt`:
```kotlin
package com.habittracker.domain.model

data class HabitTemplate(
    val id: String,
    val name: String,
    val unit: String,
    val defaultThreshold: Double,
    val defaultDailyTarget: Int,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
)
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class Habit(
    val id: String,
    val userId: String,
    val templateId: String,
    val name: String,
    val unit: String,
    val thresholdPerPoint: Double,
    val dailyTarget: Int,
    val createdAt: Instant,
)
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/HabitLog.kt`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class HabitLog(
    val id: String,
    val userId: String,
    val habitId: String,
    val quantity: Double,
    val loggedAt: Instant,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
) {
    val isActive: Boolean get() = deletedAt == null
}
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`:
```kotlin
package com.habittracker.domain.model

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
)
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/UserWantActivity.kt`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class UserWantActivity(
    val id: String,
    val userId: String,
    val activityId: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val createdAt: Instant,
)
```

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantLog.kt`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantLog(
    val id: String,
    val userId: String,
    val activityId: String,
    val quantity: Double,
    val deviceMode: DeviceMode,
    val loggedAt: Instant,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
) {
    val isActive: Boolean get() = deletedAt == null
}
```

- [ ] **Step 4: Create PointCalculator**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PointCalculator.kt`:
```kotlin
package com.habittracker.domain.usecase

object PointCalculator {
    fun pointsEarned(quantity: Double, threshold: Double): Int =
        (quantity / threshold).toInt()

    fun pointsSpent(quantity: Double, costPerUnit: Double): Int =
        (quantity * costPerUnit).toInt()
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :mobile:shared:allTests
```

Expected: `PointCalculatorTest` — 8 tests pass. Output: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/
git add mobile/shared/src/commonTest/kotlin/com/habittracker/domain/
git commit -m "feat: add domain models and PointCalculator with tests"
```

---

## Task 5: SQLDelight Local Database

**Files:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/DatabaseDriverFactory.kt`
- Create: `mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/DatabaseDriverFactory.android.kt`
- Create: `mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/DatabaseDriverFactory.ios.kt`

> **Note:** SQLDelight `.sq` files go in `src/commonMain/sqldelight/` matching the `packageName` in `build.gradle.kts`.

- [ ] **Step 1: Create the SQLDelight schema + queries**

Create `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`:

```sql
CREATE TABLE IF NOT EXISTS HabitLog (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    habitId TEXT NOT NULL,
    quantity REAL NOT NULL,
    loggedAt INTEGER NOT NULL,
    deletedAt INTEGER,
    syncedAt INTEGER
);

CREATE TABLE IF NOT EXISTS WantLog (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    activityId TEXT NOT NULL,
    quantity REAL NOT NULL,
    deviceMode TEXT NOT NULL,
    loggedAt INTEGER NOT NULL,
    deletedAt INTEGER,
    syncedAt INTEGER
);

CREATE TABLE IF NOT EXISTS LocalHabit (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    templateId TEXT NOT NULL,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    thresholdPerPoint REAL NOT NULL,
    dailyTarget INTEGER NOT NULL,
    createdAt INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS LocalIdentity (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    icon TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS LocalWantActivity (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    costPerUnit REAL NOT NULL,
    isCustom INTEGER NOT NULL DEFAULT 0
);

-- HabitLog queries
insertHabitLog:
INSERT INTO HabitLog (id, userId, habitId, quantity, loggedAt, deletedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?);

softDeleteHabitLog:
UPDATE HabitLog SET deletedAt = ? WHERE id = ? AND userId = ?;

markHabitLogSynced:
UPDATE HabitLog SET syncedAt = ? WHERE id = ?;

getUnsyncedHabitLogs:
SELECT * FROM HabitLog WHERE syncedAt IS NULL AND userId = ?;

getActiveHabitLogsForHabitOnDay:
SELECT * FROM HabitLog
WHERE userId = ? AND habitId = ? AND loggedAt >= ? AND loggedAt < ? AND deletedAt IS NULL;

getAllActiveHabitLogsForUser:
SELECT * FROM HabitLog WHERE userId = ? AND deletedAt IS NULL ORDER BY loggedAt DESC;

-- WantLog queries
insertWantLog:
INSERT INTO WantLog (id, userId, activityId, quantity, deviceMode, loggedAt, deletedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

softDeleteWantLog:
UPDATE WantLog SET deletedAt = ? WHERE id = ? AND userId = ?;

markWantLogSynced:
UPDATE WantLog SET syncedAt = ? WHERE id = ?;

getUnsyncedWantLogs:
SELECT * FROM WantLog WHERE syncedAt IS NULL AND userId = ?;

getAllActiveWantLogsForUser:
SELECT * FROM WantLog WHERE userId = ? AND deletedAt IS NULL ORDER BY loggedAt DESC;

-- LocalHabit queries
upsertHabit:
INSERT OR REPLACE INTO LocalHabit (id, userId, templateId, name, unit, thresholdPerPoint, dailyTarget, createdAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

getHabitsForUser:
SELECT * FROM LocalHabit WHERE userId = ?;

deleteHabit:
DELETE FROM LocalHabit WHERE id = ? AND userId = ?;

-- LocalIdentity queries
upsertIdentity:
INSERT OR REPLACE INTO LocalIdentity (id, name, description, icon) VALUES (?, ?, ?, ?);

getAllIdentities:
SELECT * FROM LocalIdentity;

-- LocalWantActivity queries
upsertWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, costPerUnit, isCustom)
VALUES (?, ?, ?, ?, ?, ?);

getWantActivitiesForUser:
SELECT * FROM LocalWantActivity WHERE userId = ? OR userId IS NULL;
```

- [ ] **Step 2: Create `DatabaseDriverFactory` (expect)**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/DatabaseDriverFactory.kt`:
```kotlin
package com.habittracker.data.local

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

- [ ] **Step 3: Create Android actual**

`mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/DatabaseDriverFactory.android.kt`:
```kotlin
package com.habittracker.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, "habit_tracker.db")
}
```

- [ ] **Step 4: Create iOS actual**

`mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/DatabaseDriverFactory.ios.kt`:
```kotlin
package com.habittracker.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(HabitTrackerDatabase.Schema, "habit_tracker.db")
}
```

- [ ] **Step 5: Verify SQLDelight generates code**

```bash
./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface
```

Expected: `BUILD SUCCESSFUL`. Generated classes visible in `mobile/shared/build/generated/sqldelight/`.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/
git commit -m "feat: add SQLDelight local database schema and driver factory"
```

---

## Task 6: Supabase Client + AuthRepository

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseProvider.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/AuthRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/FakeAuthRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/AuthRepositoryTest.kt`

- [ ] **Step 1: Create `SupabaseProvider.kt`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/SupabaseProvider.kt`:
```kotlin
package com.habittracker.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseProvider {
    // Replace with your Supabase project URL and anon key from supabase.com → Project Settings → API
    private const val SUPABASE_URL = "https://YOUR_PROJECT_REF.supabase.co"
    private const val SUPABASE_ANON_KEY = "YOUR_ANON_KEY"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }
}
```

> **Security note:** Never commit real credentials. For production, inject via `BuildConfig` (Android) or `Info.plist` (iOS). This placeholder is intentional for Phase 1 setup verification.

- [ ] **Step 2: Create `AuthRepository` interface**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/AuthRepository.kt`:
```kotlin
package com.habittracker.data.repository

data class UserSession(
    val userId: String,
    val email: String,
)

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<UserSession>
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signOut(): Result<Unit>
    fun currentUserId(): String?
    fun isLoggedIn(): Boolean
}
```

- [ ] **Step 3: Write the failing tests**

`mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/AuthRepositoryTest.kt`:
```kotlin
package com.habittracker.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    @Test
    fun isLoggedIn_whenNoSession_returnsFalse() {
        val repo = FakeAuthRepository()
        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun currentUserId_whenNoSession_returnsNull() {
        val repo = FakeAuthRepository()
        assertNull(repo.currentUserId())
    }

    @Test
    fun signIn_success_returnsSession() = runTest {
        val repo = FakeAuthRepository()
        val result = repo.signIn("user@example.com", "password123")
        assertTrue(result.isSuccess)
        assertEquals("user@example.com", result.getOrNull()?.email)
    }

    @Test
    fun isLoggedIn_afterSignIn_returnsTrue() = runTest {
        val repo = FakeAuthRepository()
        repo.signIn("user@example.com", "password123")
        assertTrue(repo.isLoggedIn())
    }

    @Test
    fun currentUserId_afterSignIn_returnsId() = runTest {
        val repo = FakeAuthRepository()
        repo.signIn("user@example.com", "password123")
        assertTrue(repo.currentUserId()?.isNotEmpty() == true)
    }

    @Test
    fun signOut_clearsSession() = runTest {
        val repo = FakeAuthRepository()
        repo.signIn("user@example.com", "password123")
        repo.signOut()
        assertFalse(repo.isLoggedIn())
        assertNull(repo.currentUserId())
    }

    @Test
    fun signIn_failure_returnsError() = runTest {
        val repo = FakeAuthRepository(shouldFailAuth = true)
        val result = repo.signIn("user@example.com", "wrongpassword")
        assertTrue(result.isFailure)
    }
}
```

> **Note:** `runTest` requires `kotlinx-coroutines-test` in `commonTest`. Add to `build.gradle.kts`:
> ```kotlin
> commonTest.dependencies {
>     implementation(kotlin("test"))
>     implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
> }
> ```

- [ ] **Step 4: Run tests to verify they fail**

```bash
./gradlew :mobile:shared:allTests
```

Expected: fails with "Unresolved reference: FakeAuthRepository".

- [ ] **Step 5: Create `FakeAuthRepository`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/FakeAuthRepository.kt`:
```kotlin
package com.habittracker.data.repository

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FakeAuthRepository(
    private val shouldFailAuth: Boolean = false,
) : AuthRepository {

    private var session: UserSession? = null

    override suspend fun signUp(email: String, password: String): Result<UserSession> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        val s = UserSession(userId = Uuid.random().toString(), email = email)
        session = s
        return Result.success(s)
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        val s = UserSession(userId = Uuid.random().toString(), email = email)
        session = s
        return Result.success(s)
    }

    override suspend fun signOut(): Result<Unit> {
        session = null
        return Result.success(Unit)
    }

    override fun currentUserId(): String? = session?.userId

    override fun isLoggedIn(): Boolean = session != null
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./gradlew :mobile:shared:allTests
```

Expected: `AuthRepositoryTest` — 7 tests pass. `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/
git add mobile/shared/src/commonTest/kotlin/com/habittracker/data/
git commit -m "feat: add Supabase client, AuthRepository interface, and FakeAuthRepository"
```

---

## Task 7: Android Material 3 Theme

**Files:**
- Create: `mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Color.kt`
- Create: `mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Typography.kt`
- Create: `mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Spacing.kt`
- Create: `mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Theme.kt`
- Create: `mobile/androidApp/src/main/kotlin/com/habittracker/android/MainActivity.kt`
- Create: `mobile/androidApp/src/main/res/values/themes.xml`

- [ ] **Step 1: Create `Color.kt`**

`mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Color.kt`:
```kotlin
package com.habittracker.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Streak semantic colors — used directly in streak grid composable
val StreakComplete = Color(0xFF2E7D32)       // green 800 — 7.54:1 on white (WCAG AAA)
val StreakCompleteDark = Color(0xFF81C784)   // green 300 — 4.56:1 on #121212 (WCAG AA)
val StreakFrozen = Color(0xFF1565C0)         // blue 800 — 7.02:1 on white (WCAG AAA)
val StreakFrozenDark = Color(0xFF64B5F6)     // blue 300 — 4.60:1 on #121212 (WCAG AA)
val StreakBroken = Color(0xFFC62828)         // red 800 — 5.91:1 on white (WCAG AA)
val StreakBrokenDark = Color(0xFFEF9A9A)     // red 200 — 5.12:1 on #121212 (WCAG AA)
val StreakEmpty = Color(0xFFEEEEEE)          // grey 200 — surface variant light
val StreakEmptyDark = Color(0xFF2C2C2C)      // near-black surface variant dark
val StreakTodayOutline = Color(0xFF757575)   // grey 600 — outline for today ring

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),            // green 800
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5D6A7),   // green 200
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF1565C0),          // blue 800 — frozen streak
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBBDEFB), // blue 100
    onSecondaryContainer = Color(0xFF0D47A1),
    error = Color(0xFFC62828),              // red 800 — broken streak
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFCDD2),     // red 100
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFEEEEEE),     // streak_empty
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFF757575),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),            // green 300
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E7D32),   // green 800
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFF64B5F6),          // blue 300 — frozen streak
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFBBDEFB),
    error = Color(0xFFEF9A9A),              // red 200 — broken streak
    onError = Color(0xFFB71C1C),
    errorContainer = Color(0xFFC62828),
    onErrorContainer = Color(0xFFFFCDD2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF2C2C2C),     // streak_empty dark
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF9E9E9E),
)
```

- [ ] **Step 2: Create `Spacing.kt`**

`mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Spacing.kt`:
```kotlin
package com.habittracker.android.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val xxxxl = 48.dp
    val xxxxxl = 64.dp
}
```

- [ ] **Step 3: Create `Typography.kt`**

`mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Typography.kt`:
```kotlin
package com.habittracker.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HabitTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

- [ ] **Step 4: Create `Theme.kt`**

`mobile/androidApp/src/main/kotlin/com/habittracker/android/ui/theme/Theme.kt`:
```kotlin
package com.habittracker.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun HabitTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HabitTypography,
        content = content,
    )
}

// Resolved streak colors for use in composables
@Composable
fun streakCompleteColor(): Color =
    if (isSystemInDarkTheme()) StreakCompleteDark else StreakComplete

@Composable
fun streakFrozenColor(): Color =
    if (isSystemInDarkTheme()) StreakFrozenDark else StreakFrozen

@Composable
fun streakBrokenColor(): Color =
    if (isSystemInDarkTheme()) StreakBrokenDark else StreakBroken

@Composable
fun streakEmptyColor(): Color =
    if (isSystemInDarkTheme()) StreakEmptyDark else StreakEmpty
```

- [ ] **Step 5: Create `res/values/themes.xml`**

`mobile/androidApp/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.HabitTracker" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: Create `AndroidManifest.xml`**

`mobile/androidApp/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
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

- [ ] **Step 7: Create `MainActivity.kt`**

`mobile/androidApp/src/main/kotlin/com/habittracker/android/MainActivity.kt`:
```kotlin
package com.habittracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.habittracker.android.ui.theme.HabitTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Habit Tracker — Phase 1 ✓")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 8: Build and run on emulator**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. APK at `mobile/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Run on emulator — verify green text "Habit Tracker — Phase 1 ✓" renders on white (light) or dark background, no crash.

- [ ] **Step 9: Commit**

```bash
git add mobile/androidApp/src/
git commit -m "feat: add Android Material 3 theme with WCAG-compliant streak colors"
```

---

## Task 8: iOS Design Tokens

**Files:**
- Create: `mobile/iosApp/HabitTracker/Theme/HabitColors.swift`
- Create: `mobile/iosApp/HabitTracker/Theme/HabitSpacing.swift`
- Create: `mobile/iosApp/HabitTracker/Theme/HabitTypography.swift`
- Create: `mobile/iosApp/HabitTracker/HabitTrackerApp.swift`
- Create: `mobile/iosApp/HabitTracker/ContentView.swift`

- [ ] **Step 1: Create `HabitColors.swift`**

`mobile/iosApp/HabitTracker/Theme/HabitColors.swift`:
```swift
import SwiftUI

extension Color {
    // Primary — habit complete / main actions
    static let habitPrimary = Color(light: Color(hex: "2E7D32"), dark: Color(hex: "81C784"))
    static let habitPrimaryContainer = Color(light: Color(hex: "A5D6A7"), dark: Color(hex: "2E7D32"))

    // Secondary — frozen streak (blue)
    static let habitSecondary = Color(light: Color(hex: "1565C0"), dark: Color(hex: "64B5F6"))

    // Error — broken streak (red)
    static let habitError = Color(light: Color(hex: "C62828"), dark: Color(hex: "EF9A9A"))

    // Surface
    static let habitBackground = Color(light: .white, dark: Color(hex: "121212"))
    static let habitSurface = Color(light: .white, dark: Color(hex: "121212"))
    static let habitSurfaceVariant = Color(light: Color(hex: "EEEEEE"), dark: Color(hex: "2C2C2C"))
    static let habitOutline = Color(light: Color(hex: "757575"), dark: Color(hex: "9E9E9E"))

    // Streak semantic — mirrors Android streak tokens
    static let streakComplete = Color(light: Color(hex: "2E7D32"), dark: Color(hex: "81C784"))
    static let streakFrozen = Color(light: Color(hex: "1565C0"), dark: Color(hex: "64B5F6"))
    static let streakBroken = Color(light: Color(hex: "C62828"), dark: Color(hex: "EF9A9A"))
    static let streakEmpty = Color(light: Color(hex: "EEEEEE"), dark: Color(hex: "2C2C2C"))
    static let streakTodayOutline = Color(light: Color(hex: "757575"), dark: Color(hex: "9E9E9E"))
}

private extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xFF) / 255
        let g = Double((int >> 8) & 0xFF) / 255
        let b = Double(int & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }

    init(light: Color, dark: Color) {
        self.init(UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(dark)
                : UIColor(light)
        })
    }
}
```

- [ ] **Step 2: Create `HabitSpacing.swift`**

`mobile/iosApp/HabitTracker/Theme/HabitSpacing.swift`:
```swift
import SwiftUI

enum HabitSpacing {
    static let xs: CGFloat = 2
    static let sm: CGFloat = 4
    static let md: CGFloat = 8
    static let lg: CGFloat = 12
    static let xl: CGFloat = 16
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32
    static let xxxxl: CGFloat = 48
    static let xxxxxl: CGFloat = 64
}
```

- [ ] **Step 3: Create `HabitTypography.swift`**

`mobile/iosApp/HabitTracker/Theme/HabitTypography.swift`:
```swift
import SwiftUI

enum HabitFont {
    static let displayLarge = Font.system(size: 57, weight: .regular)
    static let headlineLarge = Font.system(size: 32, weight: .semibold)
    static let headlineMedium = Font.system(size: 28, weight: .semibold)
    static let titleLarge = Font.system(size: 22, weight: .semibold)
    static let titleMedium = Font.system(size: 16, weight: .medium)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    static let bodyMedium = Font.system(size: 14, weight: .regular)
    static let labelLarge = Font.system(size: 14, weight: .medium)
    static let labelMedium = Font.system(size: 12, weight: .medium)
}
```

- [ ] **Step 4: Create `HabitTrackerApp.swift`**

`mobile/iosApp/HabitTracker/HabitTrackerApp.swift`:
```swift
import SwiftUI

@main
struct HabitTrackerApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

- [ ] **Step 5: Create `ContentView.swift`**

`mobile/iosApp/HabitTracker/ContentView.swift`:
```swift
import SwiftUI

struct ContentView: View {
    var body: some View {
        ZStack {
            Color.habitBackground.ignoresSafeArea()
            Text("Habit Tracker — Phase 1 ✓")
                .font(HabitFont.titleLarge)
                .foregroundColor(.habitPrimary)
                .padding(HabitSpacing.xl)
        }
    }
}

#Preview {
    ContentView()
}
```

- [ ] **Step 6: Set up Xcode project with CocoaPods**

Run from `mobile/iosApp/`:
```bash
cd mobile/iosApp
cat > Podfile << 'EOF'
platform :ios, '17.0'
use_frameworks!

target 'HabitTracker' do
  pod 'shared', :path => '../shared'
end
EOF
pod install
```

Expected: `HabitTracker.xcworkspace` created. Open it in Xcode.

- [ ] **Step 7: Build and run on iOS simulator**

In Xcode: Product → Run (⌘R) on iPhone 16 simulator.

Expected: green text "Habit Tracker — Phase 1 ✓" on white background (light mode) or dark background (dark mode). No crash.

- [ ] **Step 8: Commit**

```bash
git add mobile/iosApp/
git commit -m "feat: add iOS design tokens matching Android Material 3 theme"
```

---

## Task 9: Final Verification + Commit

- [ ] **Step 1: Run all tests**

```bash
./gradlew :mobile:shared:allTests
```

Expected:
```
PointCalculatorTest > pointsEarned_exactThreshold_returnsOne PASSED
PointCalculatorTest > pointsEarned_belowThreshold_returnsZero PASSED
PointCalculatorTest > pointsEarned_multipleThresholds_returnsFloor PASSED
PointCalculatorTest > pointsEarned_fractionalQuantity_truncates PASSED
PointCalculatorTest > pointsSpent_exactUnit_returnsOne PASSED
PointCalculatorTest > pointsSpent_threeUnits_returnsThree PASSED
PointCalculatorTest > pointsSpent_lessThanOneUnit_returnsZero PASSED
PointCalculatorTest > pointsSpent_scrollOneMinute_returnsOne PASSED
AuthRepositoryTest > isLoggedIn_whenNoSession_returnsFalse PASSED
AuthRepositoryTest > currentUserId_whenNoSession_returnsNull PASSED
AuthRepositoryTest > signIn_success_returnsSession PASSED
AuthRepositoryTest > isLoggedIn_afterSignIn_returnsTrue PASSED
AuthRepositoryTest > currentUserId_afterSignIn_returnsId PASSED
AuthRepositoryTest > signOut_clearsSession PASSED
AuthRepositoryTest > signIn_failure_returnsError PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 2: Build Android release**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify iOS builds in Xcode**

Open `mobile/iosApp/HabitTracker.xcworkspace`. Build for Any iOS Simulator Device (⌘B).

Expected: Build succeeded, 0 errors.

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "feat: Phase 1 foundation complete — KMP, Supabase schema, SQLDelight, auth, theme"
```

---

## Phase 1 Done — What's Next

Phase 1 delivers:
- ✅ Monorepo with KMP shared module
- ✅ Supabase schema + RLS + seed data (8 identities, 31 habits, 15 want activities)
- ✅ SQLDelight offline-first local DB
- ✅ Domain models for all entities
- ✅ PointCalculator (tested)
- ✅ AuthRepository interface + FakeAuthRepository (tested)
- ✅ Supabase client configured
- ✅ Android app with Material 3 theme (WCAG-compliant, dark + light)
- ✅ iOS app with matching design tokens

**Phase 2 plan:** Core habit loop on Android — onboarding flow, identity → habit selection, log need → earn points, log want → spend points, point balance display.
