package com.ahmed.rotrun.data

import java.io.IOException
import java.security.GeneralSecurityException

/** Decides whether a failed encrypted session can be deleted and opened again. */
object SessionUnlock {
    fun recoverable(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (isRecoverableType(current)) return true
            current = current.cause
        }
        return false
    }

    private fun isRecoverableType(error: Throwable): Boolean {
        if (error is GeneralSecurityException || error is IOException) return true
        val name = error.javaClass.name
        return name.endsWith("AEADBadTagException") || name.endsWith("KeyStoreException")
    }
}
