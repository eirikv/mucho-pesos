package vige.land.muchopesos.domain.logic

/**
 * Extracts a Google Spreadsheet ID from full Google Sheets/Drive URLs (desktop, mobile,
 * multi-account `/u/0/`, `#gid=...`, query-parameter links) or raw spreadsheet IDs.
 */
object SpreadsheetIdParser {

    private val SHEETS_PATH_REGEX = Regex(
        pattern = """^(?:https?://)?(?:[a-z0-9-]+\.)?docs\.google\.com/spreadsheets/(?:u/\d+/)?d/([A-Za-z0-9_-]{10,})(?:[/?#].*)?$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val DRIVE_OR_QUERY_REGEX = Regex(
        pattern = """^(?:https?://)?(?:[a-z0-9-]+\.)?(?:docs|drive)\.google\.com/(?:file/(?:u/\d+/)?d/|open\?[^#]*\bid=|spreadsheets/(?:u/\d+/)?[^#?]*\?[^#]*\b(?:key|id)=)([A-Za-z0-9_-]{10,})(?:[/?#&].*)?$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val RAW_ID_REGEX = Regex("""^[A-Za-z0-9_-]{10,128}$""")

    fun extractSpreadsheetId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        SHEETS_PATH_REGEX.matchEntire(trimmed)?.let { match ->
            return match.groupValues[1]
        }

        DRIVE_OR_QUERY_REGEX.matchEntire(trimmed)?.let { match ->
            return match.groupValues[1]
        }

        if (RAW_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        return null
    }

    fun buildSheetUrl(spreadsheetId: String): String =
        "https://docs.google.com/spreadsheets/d/${spreadsheetId.trim()}/edit"
}
