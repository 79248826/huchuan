package com.example.transfer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
    override fun onStart() {
        super.onStart()
        startService(Intent(this, ReceiveService::class.java))
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var url by remember { mutableStateOf(TextFieldValue("http://192.168.0.100:8090/upload")) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var log by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }

    val pickFileLauncher = remember {
        androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pickedUri = uri
        }
    }

    fun appendLog(msg: String) {
        log = "[${'$'}{java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}] ${'$'}msg\n" + log
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("互传（MVP）") })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("PC 上传 URL (/upload)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                // 自动发现 PC，发送 UDP 探测，等待回应
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    try {
                        val port = 49371
                        val socket = DatagramSocket()
                        socket.soTimeout = 1500
                        val payload = "DISCOVERY:WHO_IS_PC".toByteArray()
                        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), port)
                        socket.broadcast = true
                        socket.send(packet)

                        val buf = ByteArray(1024)
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val text = String(resp.data, 0, resp.length)
                        socket.close()
                        appendLog("发现回应：${'$'}text")
                        try {
                            val obj = JSONObject(text)
                            val started = obj.optBoolean("started", false)
                            val foundUrl = obj.optString("url", "")
                            val ip = obj.optString("ip", "")
                            val p = obj.optInt("port", 8090)
                            if (started && foundUrl.isNotEmpty()) {
                                url = TextFieldValue(foundUrl)
                            } else if (ip.isNotEmpty()) {
                                url = TextFieldValue("http://${'$'}ip:${'$'}p/upload")
                            }
                        } catch (_: Exception) { }
                    } catch (e: Exception) {
                        appendLog("未发现PC：${'$'}{e.message}")
                    }
                }
            }) { Text("自动发现PC") }

            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = { pickFileLauncher.launch("*") }) { Text("选择文件") }
                Spacer(Modifier.width(8.dp))
                Text(text = pickedUri?.toString() ?: "未选择")
            }
            Spacer(Modifier.height(8.dp))
            Button(enabled = pickedUri != null && !uploading, onClick = {
                val dest = url.text.trim()
                val theUri = pickedUri
                if (dest.isEmpty() || theUri == null) return@Button
                uploading = true
                appendLog("开始上传…")
                androidx.lifecycle.lifecycleScope.launchWhenResumed {
                    try {
                        val name = getDisplayName(theUri) ?: "upload.bin"
                        uploadBinary(dest, theUri, name)
                        appendLog("上传完成：${'$'}name")
                    } catch (e: Exception) {
                        appendLog("上传失败：${'$'}{e.message}")
                    } finally {
                        uploading = false
                    }
                }
            }) { Text(if (uploading) "上传中…" else "上传") }
            Spacer(Modifier.height(16.dp))
            Text(text = log)
        }
    }
}

suspend fun ComponentActivity.uploadBinary(url: String, uri: Uri, filename: String) = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("X-Filename", URLEncoder.encode(filename, "UTF-8"))

    contentResolver.openInputStream(uri).use { input: InputStream? ->
        requireNotNull(input) { "无法打开文件" }
        conn.outputStream.use { out ->
            input.copyTo(out, bufferSize = 64 * 1024)
        }
    }

    val code = conn.responseCode
    if (code !in 200..299) throw RuntimeException("HTTP ${'$'}code")
}

suspend fun ComponentActivity.getDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
    var name: String? = null
    val cursor = contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && it.moveToFirst()) {
            name = it.getString(idx)
        }
    }
    name
}

