package com.ahmed.rotrun.logic

/**
 * Home-widget glyphs from BrainHealthWidget.WidgetAppGlyph and
 * ScreenTimeSnapshotStore.glyph(for:). Play Store is the Android stand-in for App Store.
 */
enum class WidgetGlyph {
    Instagram,
    WhatsApp,
    Maps,
    AppStore,
    Screenshots,
    Other,
}

object WidgetGlyphs {
    fun forApp(label: String, packageName: String = ""): WidgetGlyph {
        val name = label.lowercase()
        val pkg = packageName.lowercase()
        if (name.contains("instagram") || pkg == "com.instagram.android" || pkg.startsWith("com.instagram.")) {
            return WidgetGlyph.Instagram
        }
        if (name.contains("whatsapp") || pkg == "com.whatsapp" || pkg.startsWith("com.whatsapp.")) {
            return WidgetGlyph.WhatsApp
        }
        if (name.contains("map") || pkg == "com.google.android.apps.maps") return WidgetGlyph.Maps
        if (
            name.contains("app store") ||
            name.contains("appstore") ||
            name.contains("play store") ||
            pkg == "com.android.vending"
        ) {
            return WidgetGlyph.AppStore
        }
        if (name.contains("screenshot")) return WidgetGlyph.Screenshots
        return WidgetGlyph.Other
    }
}
