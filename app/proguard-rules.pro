# Release minify is off. These rules are here for the libraries a minified
# release would break. The app uses org.json and OkHttp, not Gson, Ktor,
# kotlinx.serialization, or the Supabase Kotlin client.

-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

-keepclassmembers class com.ahmed.rotrun.billing.PlayPurchaseUpload { <fields>; }
-keepclassmembers class com.ahmed.rotrun.billing.ServerEntitlement { <fields>; }
