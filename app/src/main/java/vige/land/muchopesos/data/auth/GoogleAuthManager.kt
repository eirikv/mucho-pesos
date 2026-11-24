package vige.land.muchopesos.data.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import vige.land.muchopesos.BuildConfig
import vige.land.muchopesos.domain.model.UserSession
import java.nio.charset.StandardCharsets

sealed interface GoogleSignInResult {
    data class Success(val session: UserSession) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Failed(val message: String) : GoogleSignInResult
}

sealed interface AuthScopeResult {
    data class Authorized(val accessToken: String) : AuthScopeResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthScopeResult
    data class Failed(val message: String) : AuthScopeResult
}

class GoogleAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class GoogleAuthManager(
    private val appContext: Context,
) {
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(appContext)
    }
    private val authorizationClient by lazy {
        Identity.getAuthorizationClient(appContext)
    }

    private val tokenMutex = Mutex()
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAtMs: Long = 0L

    fun isGoogleSignInConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) {
            return GoogleSignInResult.Failed(
                "Google Sign-In is not configured yet. Add GOOGLE_WEB_CLIENT_ID to local.properties " +
                    "(see GOOGLE_CLOUD_SETUP.md), or use Try Demo Mode to explore the app immediately.",
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request,
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val claims = parseJwtPayload(idTokenCredential.idToken)
                val email = claims?.stringClaim("email")
                    ?.takeIf { it.isNotBlank() }
                    ?: idTokenCredential.id
                val displayName = claims?.stringClaim("name")
                    ?.takeIf { it.isNotBlank() }
                    ?: idTokenCredential.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore('@')
                val pictureUrl = claims?.stringClaim("picture")
                    ?.takeIf { it.isNotBlank() }
                    ?: idTokenCredential.profilePictureUri?.toString()

                GoogleSignInResult.Success(
                    UserSession(
                        email = email,
                        displayName = displayName,
                        photoUrl = pictureUrl,
                        isDemoMode = false,
                    ),
                )
            } else {
                GoogleSignInResult.Failed(
                    "Unexpected credential type returned by Google Sign-In.",
                )
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.Failed(
                "No Google account available on this device. Add a Google account in system settings or use Demo Mode.",
            )
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed(
                "Failed to parse Google ID token: ${e.localizedMessage ?: "invalid format"}",
            )
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed(
                e.localizedMessage ?: "Google Sign-In failed. Check your OAuth configuration in GOOGLE_CLOUD_SETUP.md.",
            )
        } catch (e: Exception) {
            GoogleSignInResult.Failed(
                e.localizedMessage ?: "Unexpected error during Google Sign-In.",
            )
        }
    }

    suspend fun requestApiAuthorization(): AuthScopeResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(REQUIRED_SCOPES)
            .build()

        return try {
            val authResult = authorizationClient.authorize(request).await()
            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    AuthScopeResult.NeedsConsent(pendingIntent)
                } else {
                    AuthScopeResult.Failed("Google authorization requires consent, but no consent prompt was returned.")
                }
            } else {
                val accessToken = authResult.accessToken
                if (!accessToken.isNullOrBlank()) {
                    cacheToken(accessToken)
                    AuthScopeResult.Authorized(accessToken)
                } else {
                    AuthScopeResult.Failed("Google authorization succeeded without returning an access token.")
                }
            }
        } catch (e: Exception) {
            AuthScopeResult.Failed(
                e.localizedMessage ?: "Failed to authorize Google Sheets and Drive access.",
            )
        }
    }

    fun extractTokenFromActivityResult(intent: Intent?): String? {
        if (intent == null) return null
        return try {
            val authResult = authorizationClient.getAuthorizationResultFromIntent(intent)
            val token = authResult.accessToken?.takeIf { it.isNotBlank() }
            if (token != null) {
                cacheToken(token)
            }
            token
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getFreshAccessToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        val current = cachedAccessToken
        if (!current.isNullOrBlank() && now < tokenExpiresAtMs - TOKEN_REFRESH_BUFFER_MS) {
            return@withLock current
        }

        when (val auth = requestApiAuthorization()) {
            is AuthScopeResult.Authorized -> auth.accessToken
            is AuthScopeResult.NeedsConsent -> {
                throw GoogleAuthException(
                    "Google Sheets & Drive access requires your approval. Please sign in again to grant permission.",
                )
            }
            is AuthScopeResult.Failed -> {
                throw GoogleAuthException(auth.message)
            }
        }
    }

    suspend fun signOut() {
        tokenMutex.withLock {
            cachedAccessToken = null
            tokenExpiresAtMs = 0L
        }
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun cacheToken(token: String) {
        cachedAccessToken = token
        tokenExpiresAtMs = System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS
    }

    internal fun parseJwtPayload(idToken: String): JsonObject? {
        val parts = idToken.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val decodedBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val jsonText = String(decodedBytes, StandardCharsets.UTF_8)
            jwtJson.parseToJsonElement(jsonText) as? JsonObject
        }.getOrNull()
    }

    private fun JsonObject.stringClaim(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"

        private val REQUIRED_SCOPES: List<Scope> = listOf(
            Scope(SCOPE_SHEETS),
            Scope(SCOPE_DRIVE_FILE),
            Scope(SCOPE_DRIVE_READONLY),
        )

        private const val DEFAULT_TOKEN_TTL_MS = 50L * 60L * 1000L // 50 minutes conservative TTL
        private const val TOKEN_REFRESH_BUFFER_MS = 5L * 60L * 1000L // refresh 5 min before expiry

        private val jwtJson = Json { ignoreUnknownKeys = true }
    }
}
