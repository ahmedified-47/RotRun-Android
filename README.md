# RotRun for Android

Kotlin and Jetpack Compose port of the iOS RotRun app. Same screens, copy, colors, and scoring: onboarding, brain health, Brick Mode, Focus, interventions, stats, and settings.

Open `RotRun-Android` in Android Studio (compile SDK 37.2) and run the `app` configuration.

## Permissions

- **Usage access** loads today’s real screen time, pickups, and top apps. Home → Connect Screen Time.
- **Notifications** are requested during onboarding.
- **Brick Mode** and **Focus** start a foreground watcher. When a blocked app is in front, RotRun opens the shield and an Ask my brain intervention. Android cannot apply Apple Screen Time shields, so this is the on-device equivalent. Phone, Settings, and the launcher stay available.

## Premium

The release build, including Play internal testing, always shows the paywall. Live prices come from Google Play `formattedPrice`. The app queries only these ids: `com.ahmed.rotrun.subscription.monthly`, `.yearly`, `.yearly70`, and lifetime `com.ahmed.rotrun.lifetime`. An intro price shows that Play price, then `then {regular}/year`. SAVE and the one-time offer percent use the Play quote. Placeholders appear only before Play returns a price: monthly US$12.99, yearly US$59.99 then US$155.99, yearly70 US$46.99 then US$155.99, lifetime US$149.99.

Those Play products are already active. yearly70's base plan is US$155.99 with offer id `first-year-70` at US$46.99 for the first year (A$69.99). yearly's base plan is US$155.99 with offer id `first-year` at US$59.99. Monthly is US$12.99 with no offer. Lifetime purchase option `buy` is US$149.99 and is not consumable.

A signed internal AAB, from the repo root, once `keystore.properties` points at the release keystore (that file stays off git):

```
./gradlew :app:bundleRelease
```

`keystore.properties` keys: `storeFile` (path relative to the repo root), `storePassword`, `keyAlias`, `keyPassword`. The AAB is `app/build/outputs/bundle/release/app-release.aab`.

## Blocking

The app picker lists launchable apps (MAIN/LAUNCHER). It does not use `QUERY_ALL_PACKAGES`. Free accounts block one app. Premium blocks unlimited apps. The shield is an Accessibility overlay, with a display-over-other-apps fallback. Schedules and per-app daily limits are enforced on Android. Signed-in blocker lists are backed up to Supabase with `client_id` prefixed `android:` so iOS rows are left alone.

## Deep links

`rotrun://blocked?app=Instagram`, `rotrun://focus?minutes=30`, `rotrun://focus?end=1`, `rotrun://bailout?from=Sam`, `rotrun://audioslap?from=Sam`, `rotrun://duel?vs=Sam`.
