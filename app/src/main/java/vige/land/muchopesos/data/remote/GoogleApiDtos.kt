package vige.land.muchopesos.data.remote

import kotlinx.serialization.Serializable

const val GOOGLE_SPREADSHEET_MIME_TYPE = "application/vnd.google-apps.spreadsheet"

// ============================================================================
// Google Drive API v3 DTOs
// ============================================================================

@Serializable
data class DriveCreateFileRequest(
    val name: String,
    val mimeType: String = GOOGLE_SPREADSHEET_MIME_TYPE,
)

@Serializable
data class DriveUserDto(
    val displayName: String? = null,
    val emailAddress: String? = null,
)

@Serializable
data class DriveFileDto(
    val id: String = "",
    val name: String = "",
    val mimeType: String? = null,
    val modifiedTime: String? = null,
    val owners: List<DriveUserDto> = emptyList(),
)

@Serializable
data class DriveFilesListResponse(
    val files: List<DriveFileDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class DrivePermissionRequest(
    val type: String = "user",
    val role: String = "writer",
    val emailAddress: String,
)

@Serializable
data class DrivePermissionResponse(
    val id: String = "",
    val type: String? = null,
    val role: String? = null,
)

// ============================================================================
// Google Sheets API v4 DTOs
// ============================================================================

@Serializable
data class SpreadsheetResponse(
    val spreadsheetId: String = "",
    val properties: SpreadsheetPropertiesDto? = null,
    val sheets: List<SheetDto> = emptyList(),
)

@Serializable
data class SpreadsheetPropertiesDto(
    val title: String = "",
)

@Serializable
data class SheetDto(
    val properties: SheetPropertiesDto? = null,
)

@Serializable
data class SheetPropertiesDto(
    val sheetId: Int = 0,
    val title: String = "",
    val index: Int = 0,
    val gridProperties: GridPropertiesDto? = null,
)

@Serializable
data class GridPropertiesDto(
    val rowCount: Int? = null,
    val columnCount: Int? = null,
    val frozenRowCount: Int? = null,
)

@Serializable
data class BatchUpdateSpreadsheetRequest(
    val requests: List<SheetBatchRequest>,
)

@Serializable
data class BatchUpdateSpreadsheetResponse(
    val spreadsheetId: String = "",
)

@Serializable
data class SheetBatchRequest(
    val updateSheetProperties: UpdateSheetPropertiesRequest? = null,
    val addSheet: AddSheetRequest? = null,
    val repeatCell: RepeatCellRequest? = null,
)

@Serializable
data class UpdateSheetPropertiesRequest(
    val properties: SheetPropertiesUpdateDto,
    val fields: String,
)

@Serializable
data class SheetPropertiesUpdateDto(
    val sheetId: Int,
    val title: String? = null,
    val gridProperties: GridPropertiesDto? = null,
)

@Serializable
data class AddSheetRequest(
    val properties: AddSheetPropertiesDto,
)

@Serializable
data class AddSheetPropertiesDto(
    val title: String,
    val gridProperties: GridPropertiesDto? = null,
)

@Serializable
data class RepeatCellRequest(
    val range: GridRangeDto,
    val cell: CellDataDto,
    val fields: String,
)

@Serializable
data class GridRangeDto(
    val sheetId: Int,
    val startRowIndex: Int,
    val endRowIndex: Int,
    val startColumnIndex: Int,
    val endColumnIndex: Int,
)

@Serializable
data class CellDataDto(
    val userEnteredFormat: CellFormatDto,
)

@Serializable
data class CellFormatDto(
    val textFormat: TextFormatDto? = null,
    val backgroundColor: SheetColorDto? = null,
)

@Serializable
data class TextFormatDto(
    val bold: Boolean = true,
)

@Serializable
data class SheetColorDto(
    val red: Float,
    val green: Float,
    val blue: Float,
)

@Serializable
data class ValueRangeDto(
    val range: String? = null,
    val majorDimension: String = "ROWS",
    val values: List<List<String>> = emptyList(),
)

@Serializable
data class BatchGetValuesResponse(
    val spreadsheetId: String = "",
    val valueRanges: List<ValueRangeDto> = emptyList(),
)

@Serializable
data class UpdateValuesResponse(
    val spreadsheetId: String = "",
    val updatedRange: String? = null,
    val updatedRows: Int? = null,
    val updatedColumns: Int? = null,
    val updatedCells: Int? = null,
)

@Serializable
data class AppendValuesResponse(
    val spreadsheetId: String = "",
    val tableRange: String? = null,
    val updates: UpdateValuesResponse? = null,
)
