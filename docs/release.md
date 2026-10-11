# Release build

This file tells you how to make a signed release of the Android app for the Play Store.
Do the steps in this order the first time. Later releases need only steps 5 and 6.

Keep these 4 items out of git. `.gitignore` already ignores them:

- `local.properties` and its copies, for example `local.properties.prod`
- `mobile/androidApp/google-services.json`
- the upload keystore (`*.jks`, `*.keystore`)
- the passwords of the keystore

## 1. Apply the migrations to prod

The Supabase CLI must know the prod project before `--linked` commands work.

1. Log in to the CLI. A browser window opens.

   ```sh
   supabase login
   ```

2. Link the repo to the prod project. The project ref is the first part of the host in
   `supabase.url` in `local.properties`: `https://<project-ref>.supabase.co`. The command
   asks for the database password. Find it in the Supabase dashboard, under
   **Project Settings → Database**.

   ```sh
   supabase link --project-ref <project-ref>
   ```

   The CLI writes the link to `supabase/.temp/`. Git ignores that folder.

3. Compare the migrations on your disk with the migrations on prod.

   ```sh
   supabase migration list --linked
   ```

   A migration with an empty **Remote** column is not on prod.

4. Show what the push will do. Then do the push.

   ```sh
   supabase db push --dry-run
   supabase db push
   ```

## 2. Make the upload keystore

Play signs the app for users with its own key (Play App Signing). You sign each upload with
an upload key. If you lose the upload key, Play can reset it, but the reset takes some days.

1. Make a folder outside the repo.

   ```sh
   mkdir -p ~/keys/habitto
   ```

2. Make the key. `keytool` asks for a store password, your name and your organization. Use
   a long password, and use the same password for the key when it asks.

   ```sh
   keytool -genkeypair -v \
     -keystore ~/keys/habitto/upload.jks \
     -alias upload \
     -keyalg RSA -keysize 2048 \
     -validity 10000
   ```

   If your shell does not find `keytool`, use the copy in Android Studio:
   `"/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"`.

3. Keep a copy of `upload.jks` and the password in your password manager.

4. Add these 4 lines to `local.properties` in each checkout that makes a release.

   ```properties
   release.store_file=/Users/<you>/keys/habitto/upload.jks
   release.store_password=<store password>
   release.key_alias=upload
   release.key_password=<key password>
   ```

   Without `release.store_file`, the release build is not signed, and Play refuses it.

## 3. Register the keys for Google sign-in

Google sign-in works only in an app that is signed with a key that the Google Cloud project
knows. Each key needs an **Android** OAuth client with the package name
`com.jktdeveloper.habitto` and the SHA-1 of that key. The app needs 3 keys:

| Key | Signs | How to find the SHA-1 |
|---|---|---|
| Debug key | builds from Android Studio | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` |
| Upload key | the release that you install with `adb` | `keytool -list -v -keystore ~/keys/habitto/upload.jks -alias upload` |
| Play app signing key | the app that testers get from Play | Play Console → your app → **Test and release → App integrity** |

You can see the Play key only after the first upload in step 6. Add its client before you
invite testers, or sign-in fails for each tester.

In the Google Cloud console, open **APIs & Services → Credentials**. Make 1 Android client
for each key. Do not change the Web client. `google.web_client_id` in `local.properties`
stays the same.

## 4. Connect Firebase

Firebase gives crash reports (Crashlytics) and the events of the closed test (Analytics).

1. Open the Firebase console. Add a project, or add Firebase to the Google Cloud project
   that has the OAuth clients. Turn on Google Analytics when the console asks.
2. Add an Android app with the package name `com.jktdeveloper.habitto`. Add the SHA-1 of
   the upload key.
3. Download `google-services.json`, and put it in `mobile/androidApp/`.
4. In the console, open **Crashlytics**, and turn it on.

Without `google-services.json`, the app builds with no crash reports and no events. A debug
build sends nothing, also with the file. So QA on a debug build does not fill the reports.

The app sends these events:

| Event | When | Parameters |
|---|---|---|
| `habit_logged` | a habit log is saved, from Today, a widget or a detail screen | `status` (`earned`, `below_threshold`, `daily_target_met`), `points` |
| `want_spent` | a want log is saved, also at the end of a timer | `points`, `device_mode` (`this_device`, `other`) |

A return on day 7 needs no event. Use **Analytics → Retention** in the Firebase console. It
uses the `first_open` and `session_start` events that Firebase sends.

To see the events at once while you test a release build, run this command, then open
**Analytics → DebugView**:

```sh
adb shell setprop debug.firebase.analytics.app com.jktdeveloper.habitto
```

## 5. Build

1. Make sure that `supabase.url` in `local.properties` is the prod URL.
2. Increase `versionCode` in `mobile/androidApp/build.gradle.kts` by 1 for each upload.
   Play refuses a `versionCode` that it has already received.
3. Build the bundle for Play:

   ```sh
   ./gradlew :mobile:androidApp:bundleRelease
   ```

   The file is `mobile/androidApp/build/outputs/bundle/release/androidApp-release.aab`.

4. To test the release on a phone first, build the APK and install it:

   ```sh
   ./gradlew :mobile:androidApp:assembleRelease
   adb install -r mobile/androidApp/build/outputs/apk/release/androidApp-release.apk
   ```

   A debug build and a release build have different keys. Android refuses to replace one
   with the other. Remove the debug build first. This deletes the local data of the app, so
   sync it first.

R8 is off in the release build. supabase-kt, Ktor and kotlinx.serialization need keep rules
before R8 can be on. Without R8, the stack traces in Crashlytics need no mapping file.

## 6. Upload to a closed test

1. In Play Console, make the app, and accept Play App Signing.
2. Fill in **Data safety**. Firebase collects crash logs, diagnostics and app interactions.
   Supabase stores the email address and the logs of the user.
3. Open **Test and release → Testing → Closed testing**. Make a track, and upload the
   `.aab` file.
4. Add the testers by email or by a Google Group.
5. Do step 3 for the Play app signing key before you send the invite link.
