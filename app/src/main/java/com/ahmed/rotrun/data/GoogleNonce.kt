package com.ahmed.rotrun.data

import java.security.MessageDigest

/** SHA-256 hex of the raw nonce. Google puts this in the ID token. Supabase hashes the raw nonce and compares. */
object GoogleNonce {
    fun hash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
