package com.sabahhub.meetai.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sabahhub.meetai.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In via Credential Manager, bridged into Firebase Auth. The
 * resulting Firebase UID scopes the user's Firestore documents so history syncs
 * across that user's devices.
 */
class AuthManager(
    // Null when Firebase isn't configured (no google-services.json). The app
    // still runs; sign-in is simply unavailable.
    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull(),
) {
    val available: Boolean get() = auth != null

    private val _user = MutableStateFlow(auth?.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    val uid: String? get() = auth?.currentUser?.uid

    suspend fun signInWithGoogle(context: Context) {
        val auth = auth ?: error("Firebase isn't configured — add google-services.json to enable sign-in.")
        require(BuildConfig.WEB_CLIENT_ID.isNotBlank()) {
            "WEB_CLIENT_ID is missing — set it in local.properties (Firebase web client ID)"
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken

        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth.signInWithCredential(firebaseCredential).await()
        _user.value = auth.currentUser
    }

    fun signOut() {
        auth?.signOut()
        _user.value = null
    }
}
