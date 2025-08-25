package com.example.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream

class SimpleUploadServer(private val ctx: Context, port: Int = 18090) : NanoHTTPD(port) {
    private val channelId = "recv_service"

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.POST || session.uri != "/upload") {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            } else {
                val filenameHeader = session.headers["x-filename"] ?: "upload_${System.currentTimeMillis()}"
                val filename = java.net.URLDecoder.decode(filenameHeader, "UTF-8")

                val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                val saveDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                val outFile = uniqueFile(File(saveDir, filename))

                session.inputStream.use { input ->
                    FileOutputStream(outFile).use { fos ->
                        var remain = contentLength
                        val buf = ByteArray(64 * 1024)
                        while (remain > 0) {
                            val r = input.read(buf, 0, minOf(buf.size, remain))
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            remain -= r
                        }
                    }
                }
                notifyDone(outFile)
                newFixedLengthResponse(Response.Status.OK, "text/plain", "OK")
            }
        } catch (e: Exception) {
            Log.e("SimpleUploadServer", "serve error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "ERR")
        }
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val name = file.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate: File
        do {
            candidate = File(file.parentFile, "$base(${i++})$ext")
        } while (candidate.exists())
        return candidate
    }

    private fun notifyDone(file: File) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(channelId, "接收服务", NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
            val n = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("接收完成")
                .setContentText(file.name)
                .setAutoCancel(true)
                .build()
            nm.notify(file.name.hashCode(), n)
        } catch (_: Exception) {}
    }
}

