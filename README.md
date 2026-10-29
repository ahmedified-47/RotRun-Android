# RotRun for Android

Kotlin and Jetpack Compose port of the iOS RotRun app. Same screens, copy, colors, and scoring: onboarding, brain health, Brick Mode, Focus, interventions, stats, and settings.

Open `RotRun-Android` in Android Studio (compile SDK 37.2) and run the `app` configuration.

## Permissions

- **Usage access** loads today’s real screen time, pickups, and top apps. Home → Connect Screen Time.
- **Notifications** are requested during onboarding.
- **Brick Mode** and **Focus** start a foreground watcher. When a blocked app is in front, RotRun opens the shield and an Ask my brain intervention. Android cannot apply Apple Screen Time shields, so this is the on-device equivalent. Phone, Settings, and the launcher stay available.

## Premium

Prices come from Google Play. Nothing is hard-coded. Product ids: `com.ahmed.rotrun.subscription.monthly`, `.yearly`, `.yearly70`, and lifetime `com.ahmed.rotrun.lifetime`. SAVE and the one-time offer percent are computed from the prices Play returns. A build with the paywall off: `rotrun.showPaywall=false` in `local.properties`.

## Blocking

The app picker lists launchable apps (MAIN/LAUNCHER). It does not use `QUERY_ALL_PACKAGES`. Free accounts block one app. Premium blocks unlimited apps. The shield is an Accessibility overlay, with a display-over-other-apps fallback. Schedules and per-app daily limits are enforced on Android. Signed-in blocker lists are backed up to Supabase with `client_id` prefixed `android:` so iOS rows are left alone.

## Deep links

`rotrun://blocked?app=Instagram`, `rotrun://focus?minutes=30`, `rotrun://focus?end=1`, `rotrun://bailout?from=Sam`, `rotrun://audioslap?from=Sam`, `rotrun://duel?vs=Sam`.
