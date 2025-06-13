package com.tpk.widgetspro.utils

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.tpk.widgetspro.R
import com.tpk.widgetspro.api.ImageApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ImageLoader(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val appWidgetId: Int,
    private val views: RemoteViews
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun loadImageAsync(device: android.bluetooth.BluetoothDevice) {
        scope.launch {
            val deviceName = device.name ?: "Unknown_Device_${device.address}"
            val imageUrl = ImageApiClient.getCachedUrl(context, deviceName)
                ?: ImageApiClient.getImageUrl(context, deviceName).also {
                    if (it.isNotEmpty()) ImageApiClient.cacheUrl(context, deviceName, it)
                }

            if (imageUrl.isNotEmpty()) {
                downloadBitmap(imageUrl)?.let { bitmap ->
                    BitmapCacheManager.cacheBitmap(context, deviceName, bitmap)
                    withContext(Dispatchers.Main) {
                        views.setImageViewBitmap(R.id.device_image, bitmap)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
