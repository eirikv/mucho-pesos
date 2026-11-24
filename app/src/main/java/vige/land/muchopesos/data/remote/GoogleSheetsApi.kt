package vige.land.muchopesos.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleSheetsApi {

    @GET("v4/spreadsheets/{spreadsheetId}")
    suspend fun getSpreadsheet(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("fields") fields: String = "spreadsheetId,properties.title,sheets.properties",
    ): SpreadsheetResponse

    @POST("v4/spreadsheets/{spreadsheetId}:batchUpdate")
    suspend fun batchUpdate(
        @Path("spreadsheetId") spreadsheetId: String,
        @Body request: BatchUpdateSpreadsheetRequest,
    ): BatchUpdateSpreadsheetResponse

    @GET("v4/spreadsheets/{spreadsheetId}/values:batchGet")
    suspend fun batchGetValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("ranges") ranges: List<String>,
        @Query("valueRenderOption") valueRenderOption: String = "FORMATTED_VALUE",
    ): BatchGetValuesResponse

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "RAW",
        @Body body: ValueRangeDto,
    ): UpdateValuesResponse

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "RAW",
        @Query("insertDataOption") insertDataOption: String = "INSERT_ROWS",
        @Body body: ValueRangeDto,
    ): AppendValuesResponse
}
