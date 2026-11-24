package vige.land.muchopesos.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleDriveApi {

    @POST("drive/v3/files")
    suspend fun createFile(
        @Body request: DriveCreateFileRequest,
        @Query("fields") fields: String = "id,name,mimeType,modifiedTime,owners(displayName,emailAddress)",
    ): DriveFileDto

    @POST("drive/v3/files/{fileId}/permissions")
    suspend fun createPermission(
        @Path("fileId") fileId: String,
        @Body request: DrivePermissionRequest,
        @Query("sendNotificationEmail") sendNotificationEmail: Boolean = true,
    ): DrivePermissionResponse

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String = "mimeType='$GOOGLE_SPREADSHEET_MIME_TYPE' and trashed=false",
        @Query("orderBy") orderBy: String = "modifiedTime desc",
        @Query("pageSize") pageSize: Int = 50,
        @Query("fields") fields: String = "files(id,name,mimeType,modifiedTime,owners(displayName,emailAddress)),nextPageToken",
    ): DriveFilesListResponse

    @GET("drive/v3/files/{fileId}")
    suspend fun getFileMetadata(
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "id,name,mimeType,modifiedTime,owners(displayName,emailAddress)",
    ): DriveFileDto
}
