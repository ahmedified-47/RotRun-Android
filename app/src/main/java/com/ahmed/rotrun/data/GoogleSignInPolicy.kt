package com.ahmed.rotrun.data

/**
 * GetSignInWithGoogleOption turns auto-select on. On a phone that has never granted this app,
 * that returns no credential and the account list never appears. The account-list request is the retry.
 * A tap the user cancelled is not a retry.
 */
object GoogleSignInPolicy {
    fun retryWithAccountList(exceptionClass: String, message: String): Boolean {
        val kind = exceptionClass.lowercase()
        if (kind.contains("cancellation")) return false
        if (kind.contains("nocredential")) return true
        if (kind.contains("providerconfiguration")) return true
        if (kind.contains("interrupted")) return true
        val text = message.lowercase()
        return text.contains("no credential") || text.contains("no credentials")
    }

    fun message(exceptionClass: String, text: String): String {
        val kind = exceptionClass.lowercase()
        val body = text.trim()
        if (kind.contains("cancellation")) return "Google sign-in was cancelled."
        if (kind.contains("nocredential") || body.contains("no credential", ignoreCase = true)) {
            return "Add a Google account on this phone, then try again."
        }
        if (
            body.contains("developer console", ignoreCase = true) ||
            body.contains("28444") ||
            body.contains("not set up correctly", ignoreCase = true)
        ) {
            return "Google sign-in isn't set up for this install. In Google Cloud, add an Android OAuth client for com.ahmed.rotrun with the SHA-1 of the key that signed this app, in the same project as the web client id."
        }
        if (body.contains("GOOGLE_WEB_CLIENT_ID", ignoreCase = true) || body.contains("local.properties", ignoreCase = true)) {
            return "Sign-in isn’t set up yet"
        }
        if (body.isBlank()) return "Google sign-in didn’t finish."
        return body
    }
}
