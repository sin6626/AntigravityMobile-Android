package com.antigravity.mobile.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.ui.demo.AccentBlue
import com.antigravity.mobile.ui.demo.Ink
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun PairingScreen(
    isPairing: Boolean,
    error: String?,
    onPair: (String) -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            uri = it
            onPair(it)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("连接电脑网关", color = Ink, fontSize = 29.sp)
        Spacer(Modifier.height(14.dp))
        Text("在电脑运行 mgy pair，扫描二维码或粘贴完整配对链接。", color = Color(0xFF666666),
            fontSize = 16.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("配对链接") },
            placeholder = { Text("agy://pair?...") },
            maxLines = 3,
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Color(0xFFB3261E), fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onPair(uri) },
            enabled = !isPairing && uri.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
        ) { Text(if (isPairing) "正在连接…" else "连接网关") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                scanner.launch(ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("扫描 mgy pair 二维码")
                    .setBeepEnabled(false))
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("扫描二维码") }
    }
}
