package com.ahmed.rotrun.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * The Play install restores the encrypted session file without the keystore key.
 * Opening that file throws AEADBadTagException and used to kill the process.
 */
internal object SessionStore {
    const val NAME = "rotrun.session"

    fun open(context: Context): SharedPreferences {
        try {
            return create(context)
        } catch (error: Exception) {
            if (!SessionUnlock.recoverable(error)) throw error
            Log.w(TAG, "session unreadable (${error.javaClass.simpleName}); resetting the file")
        }
        wipe(context, deleteKey = false)
        try {
            return create(context)
        } catch (error: Exception) {
            if (!SessionUnlock.recoverable(error)) throw error
            Log.w(TAG, "session key unreadable (${error.javaClass.simpleName}); resetting the key")
        }
        wipe(context, deleteKey = true)
        return try {
            create(context)
        } catch (error: Exception) {
            if (!SessionUnlock.recoverable(error)) throw error
            Log.w(TAG, "keystore cannot store a session (${error.javaClass.simpleName}); keeping it in memory")
            MemoryPrefs()
        }
    }

    private fun create(context: Context): SharedPreferences {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            NAME,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun wipe(context: Context, deleteKey: Boolean) {
        runCatching { context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().commit() }
        runCatching { context.deleteSharedPreferences(NAME) }
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        File(dir, "$NAME.xml").delete()
        File(dir, "$NAME.bak").delete()
        if (!deleteKey) return
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)
            val alias = MasterKey.DEFAULT_MASTER_KEY_ALIAS
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }

    private const val TAG = "RotRun"
}
