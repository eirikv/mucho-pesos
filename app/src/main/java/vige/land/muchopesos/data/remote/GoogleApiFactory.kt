package vige.land.muchopesos.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vige.land.muchopesos.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleApiFactory {

    private const val SHEETS_BASE_URL = "https://sheets.googleapis.com/"
    private const val DRIVE_BASE_URL = "https://www.googleapis.com/"

    @OptIn(ExperimentalSerializationApi::class)
    val apiJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    fun createOkHttpClient(
        accessTokenProvider: suspend () -> String,
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val token = try {
                runBlocking { accessTokenProvider() }
            } catch (e: Exception) {
                throw IOException(e.message ?: "Failed to obtain Google OAuth access token", e)
            }
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createSheetsApi(okHttpClient: OkHttpClient): GoogleSheetsApi {
        val contentType = "application/json; charset=UTF8".toMediaType()
        return Retrofit.Builder()
            .baseUrl(SHEETS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(apiJson.asConverterFactory(contentType))
            .build()
            .create(GoogleSheetsApi::class.java)
    }

    fun createDriveApi(okHttpClient: OkHttpClient): GoogleDriveApi {
        val contentType = "application/json; charset=UTF8".toMediaType()
        return Retrofit.Builder()
            .baseUrl(DRIVE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(apiJson.asConverterFactory(contentType))
            .build()
            .create(GoogleDriveApi::class.java)
    }
}
