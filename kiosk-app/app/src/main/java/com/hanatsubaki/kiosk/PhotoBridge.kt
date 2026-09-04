package com.hanatsubaki.kiosk

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exposed to the web page as `window.KioskPhoto`.
 * The 記念撮影 page hands over a `data:image/jpeg;base64,...` string and this
 * writes a real JPEG into the device gallery (Pictures/花つばき記念撮影).
 */
class PhotoBridge(private val context: Context) {

    @JavascriptInterface
    fun savePhoto(dataUrl: String?): Boolean {
        if (dataUrl == null) return false
        val comma = dataUrl.indexOf(',')
        val b64 = if (comma >= 0) dataUrl.substring(comma + 1) else dataUrl
        val bytes = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (bytes.isEmpty()) return false

        val name = "hanatsubaki_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/花つばき記念撮影")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return false
        return try {
            resolver.openOutputStream(uri).use { out ->
                if (out == null) return false
                out.write(bytes)
                out.flush()
            }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Lets the page feature-detect the native bridge. */
    @JavascriptInterface
    fun isAvailable(): Boolean = true
}
