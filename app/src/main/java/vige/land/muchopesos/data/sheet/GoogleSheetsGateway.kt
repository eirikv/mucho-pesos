package vige.land.muchopesos.data.sheet

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vige.land.muchopesos.data.remote.AddSheetPropertiesDto
import vige.land.muchopesos.data.remote.AddSheetRequest
import vige.land.muchopesos.data.remote.BatchUpdateSpreadsheetRequest
import vige.land.muchopesos.data.remote.CellDataDto
import vige.land.muchopesos.data.remote.CellFormatDto
import vige.land.muchopesos.data.remote.DriveCreateFileRequest
import vige.land.muchopesos.data.remote.DrivePermissionRequest
import vige.land.muchopesos.data.remote.GOOGLE_SPREADSHEET_MIME_TYPE
import vige.land.muchopesos.data.remote.GoogleDriveApi
import vige.land.muchopesos.data.remote.GoogleSheetsApi
import vige.land.muchopesos.data.remote.GridPropertiesDto
import vige.land.muchopesos.data.remote.GridRangeDto
import vige.land.muchopesos.data.remote.RepeatCellRequest
import vige.land.muchopesos.data.remote.SheetBatchRequest
import vige.land.muchopesos.data.remote.SheetColorDto
import vige.land.muchopesos.data.remote.SheetPropertiesUpdateDto
import vige.land.muchopesos.data.remote.TextFormatDto
import vige.land.muchopesos.data.remote.UpdateSheetPropertiesRequest
import vige.land.muchopesos.data.remote.ValueRangeDto
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow

class GoogleSheetsGateway(
    private val sheetsApi: GoogleSheetsApi,
    private val driveApi: GoogleDriveApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowIsoProvider: () -> String = { Instant.now().toString() },
) : LedgerSheetGateway {

    override suspend fun createLedger(
        ledgerName: String,
        homeCurrencyCode: String,
        partnerAEmail: String,
        partnerAName: String,
        partnerBEmail: String,
        partnerBName: String,
    ): LedgerConfig = withContext(ioDispatcher) {
        val cleanName = ledgerName.trim()
        val cleanCurrency = homeCurrencyCode.trim().uppercase()
        val cleanAEmail = partnerAEmail.trim()
        val cleanAName = partnerAName.trim()
        val cleanBEmail = partnerBEmail.trim()
        val cleanBName = partnerBName.trim()

        // 1. Create the Google Sheet via Drive API v3 so the app owns it under drive.file scope.
        val createdFile = driveApi.createFile(
            request = DriveCreateFileRequest(
                name = cleanName,
                mimeType = GOOGLE_SPREADSHEET_MIME_TYPE,
            ),
        )
        val spreadsheetId = createdFile.id
        require(spreadsheetId.isNotBlank()) { "Google Drive did not return a spreadsheet ID." }

        // 2. Inspect default sheetId, rename it to "Ledger", freeze header row, and add "Config" tab.
        val metadata = sheetsApi.getSpreadsheet(spreadsheetId)
        val defaultSheetId = metadata.sheets.firstOrNull()?.properties?.sheetId ?: 0

        sheetsApi.batchUpdate(
            spreadsheetId = spreadsheetId,
            request = BatchUpdateSpreadsheetRequest(
                requests = listOf(
                    SheetBatchRequest(
                        updateSheetProperties = UpdateSheetPropertiesRequest(
                            properties = SheetPropertiesUpdateDto(
                                sheetId = defaultSheetId,
                                title = SheetRowCodec.LEDGER_TAB_NAME,
                                gridProperties = GridPropertiesDto(frozenRowCount = 1),
                            ),
                            fields = "title,gridProperties.frozenRowCount",
                        ),
                    ),
                    SheetBatchRequest(
                        addSheet = AddSheetRequest(
                            properties = AddSheetPropertiesDto(
                                title = SheetRowCodec.CONFIG_TAB_NAME,
                                gridProperties = GridPropertiesDto(
                                    rowCount = 30,
                                    columnCount = 4,
                                    frozenRowCount = 1,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        // 3. Apply bold header styling to both tabs (best effort so formatting never blocks creation).
        runCatching {
            val updatedMeta = sheetsApi.getSpreadsheet(spreadsheetId)
            val ledgerSheetId = updatedMeta.sheets
                .firstOrNull { it.properties?.title == SheetRowCodec.LEDGER_TAB_NAME }
                ?.properties?.sheetId ?: defaultSheetId
            val configSheetId = updatedMeta.sheets
                .firstOrNull { it.properties?.title == SheetRowCodec.CONFIG_TAB_NAME }
                ?.properties?.sheetId

            val headerFormat = CellDataDto(
                userEnteredFormat = CellFormatDto(
                    textFormat = TextFormatDto(bold = true),
                    backgroundColor = SheetColorDto(red = 0.91f, green = 0.95f, blue = 0.92f),
                ),
            )
            val styleRequests = buildList {
                add(
                    SheetBatchRequest(
                        repeatCell = RepeatCellRequest(
                            range = GridRangeDto(
                                sheetId = ledgerSheetId,
                                startRowIndex = 0,
                                endRowIndex = 1,
                                startColumnIndex = 0,
                                endColumnIndex = SheetRowCodec.LEDGER_HEADERS.size,
                            ),
                            cell = headerFormat,
                            fields = "userEnteredFormat(textFormat.bold,backgroundColor)",
                        ),
                    ),
                )
                if (configSheetId != null) {
                    add(
                        SheetBatchRequest(
                            repeatCell = RepeatCellRequest(
                                range = GridRangeDto(
                                    sheetId = configSheetId,
                                    startRowIndex = 0,
                                    endRowIndex = 1,
                                    startColumnIndex = 0,
                                    endColumnIndex = SheetRowCodec.CONFIG_HEADERS.size,
                                ),
                                cell = headerFormat,
                                fields = "userEnteredFormat(textFormat.bold,backgroundColor)",
                            ),
                        ),
                    )
                }
            }
            sheetsApi.batchUpdate(
                spreadsheetId = spreadsheetId,
                request = BatchUpdateSpreadsheetRequest(requests = styleRequests),
            )
        }

        // 4. Write Config metadata and Ledger header row.
        val config = LedgerConfig(
            spreadsheetId = spreadsheetId,
            ledgerName = cleanName,
            homeCurrencyCode = cleanCurrency,
            partnerAEmail = cleanAEmail,
            partnerAName = cleanAName,
            partnerBEmail = cleanBEmail,
            partnerBName = cleanBName,
            createdAt = nowIsoProvider(),
        )

        sheetsApi.updateValues(
            spreadsheetId = spreadsheetId,
            range = SheetRowCodec.CONFIG_WRITE_RANGE,
            body = ValueRangeDto(values = SheetRowCodec.encodeConfig(config)),
        )

        sheetsApi.updateValues(
            spreadsheetId = spreadsheetId,
            range = SheetRowCodec.LEDGER_HEADER_RANGE,
            body = ValueRangeDto(values = listOf(SheetRowCodec.LEDGER_HEADERS)),
        )

        // 5. Grant Partner B writer access via Drive API v3 permissions.create.
        if (cleanBEmail.isNotBlank() && !cleanBEmail.equals(cleanAEmail, ignoreCase = true)) {
            driveApi.createPermission(
                fileId = spreadsheetId,
                request = DrivePermissionRequest(
                    type = "user",
                    role = "writer",
                    emailAddress = cleanBEmail,
                ),
                sendNotificationEmail = true,
            )
        }

        config
    }

    override suspend fun listAvailableSpreadsheets(query: String?): List<DriveSpreadsheetItem> =
        withContext(ioDispatcher) {
            val baseFilter = "mimeType='$GOOGLE_SPREADSHEET_MIME_TYPE' and trashed=false"
            val trimmedQuery = query?.trim().orEmpty()
            val fullQuery = if (trimmedQuery.isEmpty()) {
                baseFilter
            } else {
                val escaped = trimmedQuery.replace("\\", "\\\\").replace("'", "\\'")
                "$baseFilter and name contains '$escaped'"
            }

            val response = driveApi.listFiles(query = fullQuery)
            response.files
                .filter { it.id.isNotBlank() }
                .map { dto ->
                    DriveSpreadsheetItem(
                        id = dto.id,
                        name = dto.name.ifBlank { "Untitled Spreadsheet" },
                        modifiedTime = dto.modifiedTime.orEmpty(),
                        ownerEmail = dto.owners.firstOrNull()?.emailAddress,
                    )
                }
        }

    override suspend fun loadLedger(spreadsheetId: String): Pair<LedgerConfig, List<LedgerRow>> =
        withContext(ioDispatcher) {
            val response = sheetsApi.batchGetValues(
                spreadsheetId = spreadsheetId,
                ranges = listOf(
                    SheetRowCodec.CONFIG_READ_RANGE,
                    SheetRowCodec.LEDGER_DATA_RANGE,
                ),
            )
            val configValues = response.valueRanges.getOrNull(0)?.values.orEmpty()
            val ledgerValues = response.valueRanges.getOrNull(1)?.values.orEmpty()

            val config = SheetRowCodec.decodeConfig(
                spreadsheetId = spreadsheetId,
                rows = configValues,
            )
            val rows = SheetRowCodec.decodeLedgerRows(
                rows = ledgerValues,
                scale = config.currencySpec.fractionDigits,
            )
            config to rows
        }

    override suspend fun updateConfig(config: LedgerConfig) {
        withContext(ioDispatcher) {
            sheetsApi.updateValues(
                spreadsheetId = config.spreadsheetId,
                range = SheetRowCodec.CONFIG_WRITE_RANGE,
                body = ValueRangeDto(values = SheetRowCodec.encodeConfig(config)),
            )
        }
    }

    override suspend fun appendRows(
        spreadsheetId: String,
        rows: List<LedgerRow>,
        scale: Int,
    ) {
        if (rows.isEmpty()) return
        withContext(ioDispatcher) {
            val encodedRows = SheetRowCodec.encodeLedgerRows(rows, scale)
            sheetsApi.appendValues(
                spreadsheetId = spreadsheetId,
                range = SheetRowCodec.LEDGER_APPEND_RANGE,
                body = ValueRangeDto(values = encodedRows),
            )
        }
    }
}
