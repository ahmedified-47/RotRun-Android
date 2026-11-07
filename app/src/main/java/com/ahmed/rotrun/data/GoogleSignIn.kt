package com.ahmed.rotrun.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GoogleSignIn {
    fun start(activity: Activity, serverClientId: String, hashedNonce: String, onDone: (Result<String>) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = runCatching { fetchIdToken(activity, serverClientId, hashedNonce) }
            onDone(result)
        }
    }

    private suspend fun fetchIdToken(activity: Activity, serverClientId: String, hashedNonce: String): String {
        try {
            return tokenFrom(activity, buttonRequest(serverClientId, hashedNonce))
        } catch (error: Exception) {
            if (!GoogleSignInPolicy.retryWithAccountList(error.javaClass.name, error.message.orEmpty())) throw error
        }
        return tokenFrom(activity, accountListRequest(serverClientId, hashedNonce))
    }

    private fun buttonRequest(serverClientId: String, hashedNonce: String): GetCredentialRequest {
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(hashedNonce)
            .build()
        return GetCredentialRequest.Builder().addCredentialOption(option).build()
    }

    /** Every Google account on the phone. Auto-select is off, so a first grant still shows the list. */
    private fun accountListRequest(serverClientId: String, hashedNonce: String): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(serverClientId)
            .setNonce(hashedNonce)
            .build()
        return GetCredentialRequest.Builder().addCredentialOption(option).build()
    }

    private suspend fun tokenFrom(activity: Activity, request: GetCredentialRequest): String {
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Google didn’t return an ID token.")
    }
}
