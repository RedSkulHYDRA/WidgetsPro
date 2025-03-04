package com.tpk.widgetspro.api

import android.content.Context
import android.content.pm.PackageManager
import com.tpk.widgetspro.BuildConfig
import com.tpk.widgetspro.models.GoogleSearchResponse
import com.tpk.widgetspro.utils.CryptoUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.resume

object ImageApiClient {
    private const val GOOGLE_API_BASE_URL = "https://www.googleapis.com/"
    private const val PREF_NAME = "ImageCache"
    private const val URL_CACHE_PREFIX = "url_"
    private const val TIMESTAMP_PREFIX = "ts_"
    private const val WEEK_IN_MILLIS = 604_800_000L

    val fernetKey = BuildConfig.API_KEY_1
    val encryptedApiKey1 = BuildConfig.API_KEY_2
    val encryptedApiKey2 = BuildConfig.API_KEY_3
    private val API_KEY = CryptoUtils.decryptApiKey(encryptedApiKey1, fernetKey)
    private val SEARCH_ENGINE_ID = CryptoUtils.decryptApiKey(encryptedApiKey2, fernetKey)

    private fun createService(context: Context): GoogleSearchApiService {

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val packageName = context.packageName
                val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val sha1Hex = packageInfo.signingInfo?.apkContentsSigners?.get(0)?.toByteArray()
                    .let { java.security.MessageDigest.getInstance("SHA1").digest(it) }
                    .joinToString("") { "%02x".format(it) }

                val newRequest = originalRequest.newBuilder()
                    .header("X-Android-Package", packageName)
                    .header("X-Android-Cert", sha1Hex)
                    .build()
                chain.proceed(newRequest)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(GOOGLE_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleSearchApiService::class.java)
    }

    suspend fun getImageUrl(context: Context, modelName: String): String = suspendCancellableCoroutine { continuation ->
        val query = getCustomQuery(context, modelName) ?: buildSearchQuery(modelName)
        createService(context).searchImages(API_KEY, SEARCH_ENGINE_ID, query, "image", "png")
            .enqueue(object : retrofit2.Callback<GoogleSearchResponse> {
                override fun onResponse(call: retrofit2.Call<GoogleSearchResponse>, response: retrofit2.Response<GoogleSearchResponse>) {
                    continuation.resume(response.body()?.items?.firstOrNull()?.link ?: "")
                }

                override fun onFailure(call: retrofit2.Call<GoogleSearchResponse>, t: Throwable) {
                    continuation.resume("")
                }
            })
    }

    private fun buildSearchQuery(modelName: String): String = when {
        modelName == "Unknown" -> "bluetooth device icon transparent"
        modelName.contains("watch", ignoreCase = true) -> "${sanitizeDeviceName(modelName)} transparent"
        else -> "$modelName transparent"
    }

    fun getCachedUrl(context: Context, modelName: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("$TIMESTAMP_PREFIX$modelName", 0)
        return if (System.currentTimeMillis() - timestamp < WEEK_IN_MILLIS) {
            prefs.getString("$URL_CACHE_PREFIX$modelName", null)
        } else {
            prefs.edit()
                .remove("$URL_CACHE_PREFIX$modelName")
                .remove("$TIMESTAMP_PREFIX$modelName")
                .apply()
            null
        }
    }

    fun cacheUrl(context: Context, modelName: String, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString("$URL_CACHE_PREFIX$modelName", url)
            .putLong("$TIMESTAMP_PREFIX$modelName", System.currentTimeMillis())
            .apply()
    }

    private fun sanitizeDeviceName(originalName: String?): String {
        if (originalName.isNullOrEmpty()) return "Unknown Device"
        var sanitized = originalName.trim()
        listOf(
            "\\s*-?\\s*LE$",
            "\\s*\\(.*\\)$",
            "\\s*[_-]\\s*[A-Za-z0-9]{4}$",
            "\\s*[_-]\\s*[vV]\\d+",
            "\\s*Pro$",
            "\\s*Lite$",
            "\\s*[_-]?\\s*[Bb][Ll][Ee]$"
        ).forEach { pattern -> sanitized = sanitized.replace(Regex(pattern, RegexOption.IGNORE_CASE), "") }
        return sanitized.replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .trim()
            .replace(Regex("\\s{2,}"), " ")
            .lowercase()
            .replaceFirstChar { it.uppercaseChar() }
    }

    fun clearUrlCache(context: Context, modelName: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove("$URL_CACHE_PREFIX$modelName")
            .remove("$TIMESTAMP_PREFIX$modelName")
            .apply()
    }

    fun setCustomQuery(context: Context, modelName: String, query: String) {
        context.getSharedPreferences("CustomQueries", Context.MODE_PRIVATE).edit()
            .putString("custom_query_$modelName", query)
            .apply()
    }

    fun clearCustomQuery(context: Context, modelName: String) {
        context.getSharedPreferences("CustomQueries", Context.MODE_PRIVATE).edit()
            .remove("custom_query_$modelName")
            .apply()
    }

    private fun getCustomQuery(context: Context, modelName: String): String? {
        return "${sanitizeDeviceName(modelName)} " + context.getSharedPreferences("CustomQueries", Context.MODE_PRIVATE)
            .getString("custom_query_$modelName", null)
    }
}