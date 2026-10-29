import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun buildConfigString(name: String): String {
    val raw = localProperties.getProperty(name) ?: (project.findProperty(name) as String?) ?: ""
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

val showPaywall = (project.findProperty("rotrun.showPaywall") as String?)?.toBooleanStrictOrNull()
    ?: localProperties.getProperty("rotrun.showPaywall")?.toBooleanStrictOrNull()
    ?: true

android {
    namespace = "com.ahmed.rotrun"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.ahmed.rotrun"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "SHOW_PAYWALL", showPaywall.toString())
        buildConfigField("String", "SUPABASE_URL", "\"https://bhndzocphiexxcvbjisn.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString("SUPABASE_PUBLISHABLE_KEY"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", buildConfigString("GOOGLE_WEB_CLIENT_ID"))
        buildConfigField("boolean", "PLAY_GAMES_FRIENDS", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
