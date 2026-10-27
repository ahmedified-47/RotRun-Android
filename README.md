# RotRun for Android

Kotlin and Jetpack Compose port of the iOS RotRun app. Same screens, copy, colors, and scoring: onboarding, brain health, Brick Mode, Focus, interventions, stats, and settings.

Open `RotRun-Android` in Android Studio (compile SDK 37.2) and run the `app` configuration.

## Permissions

- **Usage access** loads today’s real screen time, pickups, and top apps. Home → Connect Screen Time.
- **Notifications** are requested during onboarding.
- **Brick Mode** and **Focus** start a foreground watcher. When a blocked app is in front, RotRun opens the shield and an Ask my brain intervention. Android cannot apply Apple Screen Time shields, so this is the on-device equivalent. Phone, Settings, and the launcher stay available.

## Premium

Plan prices match the iOS StoreKit config (Monthly $6.99, Yearly $79.99, 70% first-year offer $23.99). Purchases are saved on the device until Google Play products are connected. The product ids are `com.ahmed.rotrun.subscription.monthly`, `.yearly`, and `.yearly70`.

## Deep links

`rotrun://blocked?app=Instagram`, `rotrun://focus?minutes=30`, `rotrun://focus?end=1`, `rotrun://bailout?from=Sam`, `rotrun://audioslap?from=Sam`, `rotrun://duel?vs=Sam`.
