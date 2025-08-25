package com.example.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

class ReceiveService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        job = scope.launch { runServers() }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    private fun startForegroundWithNotification() {
        val channelId = "recv_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "接收服务", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("互传接收服务运行中")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        startForeground(1, n)
    }

    private suspend fun runServers() {
        // 1) UDP 发现回应
        scope.launch { udpResponder() }
        // 2) TCP(HTTP) 接收 /upload
        scope.launch { httpUploadServer() }
    }

    private fun getLocalIPv4(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (ni in interfaces) {
                val addrs = ni.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun udpResponder() {
        try {
            val socket = DatagramSocket(49371)
            val buf = ByteArray(1024)
            while (true) {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val text = String(packet.data, 0, packet.length)
                if (text == "DISCOVERY:WHO_IS_ANDROID") {
                    val ip = getLocalIPv4()
                    val resp = "{" +
                            "\"type\":\"DISCOVERY:ANDROID_INFO\"," +
                            "\"ip\":\"" + ip + "\"," +
                            "\"port\":18090," +
                            "\"url\":\"http://" + ip + ":18090/upload\"" +
                            "}"
                    val out = DatagramPacket(resp.toByteArray(), resp.length, packet.address, packet.port)
                    socket.send(out)
                }
            }
        } catch (e: Exception) {
            Log.e("ReceiveService", "udpResponder error", e)
        }
    }

    private fun httpUploadServer() {
        try {
            val server = SimpleUploadServer(this@ReceiveService, 18090)
            server.start()
        } catch (e: Exception) {
            Log.e("ReceiveService", "httpUploadServer error", e)
        }
    }
}

