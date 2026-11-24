package vige.land.muchopesos.data.local

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow

@Serializable
data class CachedLedgerSnapshot(
    val config: LedgerConfig,
    val rows: List<LedgerRow>,
    val savedAtEpochMs: Long,
)

class LocalLedgerCache(
    private val cacheFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(expectedSpreadsheetId: String? = null): CachedLedgerSnapshot? = mutex.withLock {
        withContext(ioDispatcher) {
            if (!cacheFile.exists()) return@withContext null
            val snapshot = runCatching {
                json.decodeFromString<CachedLedgerSnapshot>(cacheFile.readText())
            }.getOrNull() ?: return@withContext null

            if (expectedSpreadsheetId != null &&
                snapshot.config.spreadsheetId != expectedSpreadsheetId
            ) {
                return@withContext null
            }
            snapshot
        }
    }

    suspend fun save(
        config: LedgerConfig,
        rows: List<LedgerRow>,
        savedAtEpochMs: Long = System.currentTimeMillis(),
    ) = mutex.withLock {
        withContext(ioDispatcher) {
            cacheFile.parentFile?.mkdirs()
            val payload = json.encodeToString(
                CachedLedgerSnapshot(
                    config = config,
                    rows = rows,
                    savedAtEpochMs = savedAtEpochMs,
                ),
            )
            val tmpFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmpFile.writeText(payload)
            if (!tmpFile.renameTo(cacheFile)) {
                cacheFile.writeText(payload)
                tmpFile.delete()
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(ioDispatcher) {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }
}
