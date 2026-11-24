package vige.land.muchopesos.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vige.land.muchopesos.domain.model.UserSession

private val Context.muchoPesosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mucho_pesos_prefs",
)

interface PreferencesStorage {
    val userSessionFlow: Flow<UserSession?>
    val activeSpreadsheetIdFlow: Flow<String?>
    val lastSyncEpochMsFlow: Flow<Long?>

    suspend fun getUserSession(): UserSession?
    suspend fun saveUserSession(session: UserSession)
    suspend fun clearUserSession()

    suspend fun getActiveSpreadsheetId(): String?
    suspend fun saveActiveSpreadsheetId(spreadsheetId: String?)

    suspend fun getLastSyncEpochMs(): Long?
    suspend fun saveLastSyncEpochMs(epochMs: Long?)

    suspend fun clearLedgerLink()
    suspend fun clearAll()
}

class PreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStorage {

    constructor(context: Context) : this(context.applicationContext.muchoPesosDataStore)

    override val userSessionFlow: Flow<UserSession?> = dataStore.data.map { prefs ->
        val email = prefs[KEY_USER_EMAIL] ?: return@map null
        UserSession(
            email = email,
            displayName = prefs[KEY_USER_DISPLAY_NAME].orEmpty().ifBlank { email.substringBefore('@') },
            photoUrl = prefs[KEY_USER_PHOTO_URL],
            isDemoMode = prefs[KEY_IS_DEMO_MODE] ?: false,
        )
    }

    override val activeSpreadsheetIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_SPREADSHEET_ID]?.takeIf { it.isNotBlank() }
    }

    override val lastSyncEpochMsFlow: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_EPOCH_MS]
    }

    override suspend fun getUserSession(): UserSession? = userSessionFlow.first()

    override suspend fun saveUserSession(session: UserSession) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = session.email
            prefs[KEY_USER_DISPLAY_NAME] = session.displayName
            if (session.photoUrl != null) {
                prefs[KEY_USER_PHOTO_URL] = session.photoUrl
            } else {
                prefs.remove(KEY_USER_PHOTO_URL)
            }
            prefs[KEY_IS_DEMO_MODE] = session.isDemoMode
        }
    }

    override suspend fun clearUserSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_DISPLAY_NAME)
            prefs.remove(KEY_USER_PHOTO_URL)
            prefs.remove(KEY_IS_DEMO_MODE)
        }
    }

    override suspend fun getActiveSpreadsheetId(): String? = activeSpreadsheetIdFlow.first()

    override suspend fun saveActiveSpreadsheetId(spreadsheetId: String?) {
        dataStore.edit { prefs ->
            if (spreadsheetId.isNullOrBlank()) {
                prefs.remove(KEY_ACTIVE_SPREADSHEET_ID)
            } else {
                prefs[KEY_ACTIVE_SPREADSHEET_ID] = spreadsheetId
            }
        }
    }

    override suspend fun getLastSyncEpochMs(): Long? = lastSyncEpochMsFlow.first()

    override suspend fun saveLastSyncEpochMs(epochMs: Long?) {
        dataStore.edit { prefs ->
            if (epochMs == null) {
                prefs.remove(KEY_LAST_SYNC_EPOCH_MS)
            } else {
                prefs[KEY_LAST_SYNC_EPOCH_MS] = epochMs
            }
        }
    }

    override suspend fun clearLedgerLink() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_SPREADSHEET_ID)
            prefs.remove(KEY_LAST_SYNC_EPOCH_MS)
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        private val KEY_USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        private val KEY_IS_DEMO_MODE = booleanPreferencesKey("user_is_demo_mode")
        private val KEY_ACTIVE_SPREADSHEET_ID = stringPreferencesKey("active_spreadsheet_id")
        private val KEY_LAST_SYNC_EPOCH_MS = longPreferencesKey("last_sync_epoch_ms")
    }
}

/**
 * Lightweight pure-JVM in-memory implementation of [PreferencesStorage] for unit testing.
 */
class InMemoryPreferencesStore : PreferencesStorage {
    private val _session = MutableStateFlow<UserSession?>(null)
    private val _spreadsheetId = MutableStateFlow<String?>(null)
    private val _lastSync = MutableStateFlow<Long?>(null)

    override val userSessionFlow: Flow<UserSession?> = _session
    override val activeSpreadsheetIdFlow: Flow<String?> = _spreadsheetId
    override val lastSyncEpochMsFlow: Flow<Long?> = _lastSync

    override suspend fun getUserSession(): UserSession? = _session.value
    override suspend fun saveUserSession(session: UserSession) {
        _session.value = session
    }

    override suspend fun clearUserSession() {
        _session.value = null
    }

    override suspend fun getActiveSpreadsheetId(): String? = _spreadsheetId.value
    override suspend fun saveActiveSpreadsheetId(spreadsheetId: String?) {
        _spreadsheetId.value = spreadsheetId?.takeIf { it.isNotBlank() }
    }

    override suspend fun getLastSyncEpochMs(): Long? = _lastSync.value
    override suspend fun saveLastSyncEpochMs(epochMs: Long?) {
        _lastSync.value = epochMs
    }

    override suspend fun clearLedgerLink() {
        _spreadsheetId.value = null
        _lastSync.value = null
    }

    override suspend fun clearAll() {
        _session.value = null
        _spreadsheetId.value = null
        _lastSync.value = null
    }
}
