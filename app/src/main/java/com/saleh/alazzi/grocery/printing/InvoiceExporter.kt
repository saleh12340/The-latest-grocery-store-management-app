package com.saleh.alazzi.grocery.printing

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class InvoiceExporter(private val context: Context) {
    fun savePng(bitmap: Bitmap, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, safeName(fileName, "png"))
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlAzzi")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء ملف الصورة")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر حفظ الصورة" }
                } ?: error("تعذر فتح ملف الصورة")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AlAzzi").apply { mkdirs() }
            val file = File(directory, safeName(fileName, "png"))
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            FileProvider.getUriForFile(context, "\${context.packageName}.files", file)
        }
    }

    fun savePdf(bitmap: Bitmap, fileName: String): Uri {
        val widthPoints = (bitmap.width * 72f / 203f).toInt().coerceAtLeast(1)
        val heightPoints = (bitmap.height * 72f / 203f).toInt().coerceAtLeast(1)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(widthPoints, heightPoints, 1).create())
        page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, widthPoints.toFloat(), heightPoints.toFloat()), null)
        document.finishPage(page)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(fileName, "pdf"))
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AlAzzi")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("تعذر إنشاء ملف PDF")
                try {
                    context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                        ?: error("تعذر فتح ملف PDF")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    uri
                } catch (error: Throwable) {
                    context.contentResolver.delete(uri, null, null)
                    throw error
                }
            } else {
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AlAzzi").apply { mkdirs() }
                val file = File(directory, safeName(fileName, "pdf"))
                FileOutputStream(file).use { document.writeTo(it) }
                FileProvider.getUriForFile(context, "\${context.packageName}.files", file)
            }
        } finally {
            document.close()
        }
    }

    fun share(uri: Uri, mimeType: String, chooserTitle: String = "مشاركة الفاتورة") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "فاتورة بقالة العزي", uri)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWhatsAppChat(phone: String) {
        val international = normalizeYemeniPhone(phone)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun shareToWhatsApp(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "فاتورة بقالة العزي للمواد الغذائية")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newUri(context.contentResolver, "فاتورة", uri)
        }
        context.startActivity(intent)
    }

    private fun normalizeYemeniPhone(input: String): String {
        val digits = input.filter(Char::isDigit)
        val international = when {
            digits.length == 9 -> "967$digits"
            digits.startsWith("00967") -> digits.removePrefix("00")
            digits.startsWith("967") -> digits
            digits.startsWith("0") && digits.length == 10 -> "967" + digits.drop(1)
            else -> error("رقم الهاتف اليمني غير صحيح")
        }
        require(international.length == 12 && international.startsWith("967")) {
            "رقم الهاتف اليمني غير صحيح"
        }
        return international
    }

    private fun safeName(value: String, extension: String): String {
        val base = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "alazzi_invoice" }
        return if (base.endsWith(".$extension")) base else "$base.$extension"
    }
}
