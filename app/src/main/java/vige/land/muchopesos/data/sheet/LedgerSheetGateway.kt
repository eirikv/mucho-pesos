package vige.land.muchopesos.data.sheet

import kotlinx.serialization.Serializable
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow

@Serializable
data class DriveSpreadsheetItem(
    val id: String,
    val name: String,
    val modifiedTime: String = "",
    val ownerEmail: String? = null,
)

interface LedgerSheetGateway {

    suspend fun createLedger(
        ledgerName: String,
        homeCurrencyCode: String,
        partnerAEmail: String,
        partnerAName: String,
        partnerBEmail: String,
        partnerBName: String,
    ): LedgerConfig

    suspend fun listAvailableSpreadsheets(query: String? = null): List<DriveSpreadsheetItem>

    suspend fun loadLedger(spreadsheetId: String): Pair<LedgerConfig, List<LedgerRow>>

    suspend fun updateConfig(config: LedgerConfig)

    suspend fun appendRows(
        spreadsheetId: String,
        rows: List<LedgerRow>,
        scale: Int = 2,
    )
}
