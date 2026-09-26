package com.antigravity.mobile.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.antigravity.mobile.ui.demo.AccentBlue
import com.antigravity.mobile.ui.demo.Ink
import com.antigravity.mobile.ui.demo.RoundIconButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun PairingScreen(
    isPairing: Boolean,
    error: String?,
    currentGatewayUrl: String? = null,
    onBack: (() -> Unit)? = null,
    onPair: (String, String) -> Unit,
) {
    var uri by rememberSaveable { mutableStateOf("") }
    var customUrl by rememberSaveable { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { uri = it }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = if (onBack == null) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        if (onBack != null) {
            Spacer(Modifier.height(18.dp))
            RoundIconButton("arrow_back", "返回", onBack, size = 48.dp)
            Spacer(Modifier.height(28.dp))
        }
        Text(if (onBack == null) "连接电脑网关" else "配对设置", color = Ink, fontSize = 29.sp)
        Spacer(Modifier.height(14.dp))
        Text("在电脑运行 mgy pair，扫描二维码或粘贴完整配对链接。", color = Color(0xFF666666),
            fontSize = 16.sp, lineHeight = 24.sp)
        if (!currentGatewayUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("当前地址：$currentGatewayUrl", color = Color(0xFF666666), fontSize = 14.sp)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("配对链接") },
            placeholder = { Text("agy://pair?...") },
            shape = RoundedCornerShape(18.dp),
            maxLines = 3,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自定义网关 URL（可选）") },
            placeholder = { Text("http://100.64.x.x:58900 或 https://域名") },
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(9.dp))
        Text("填写后将使用这个地址配对。Tailscale、内网穿透仍需 mgy pair 的配对码。",
            color = Color(0xFF666666), fontSize = 13.sp, lineHeight = 20.sp)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Color(0xFFB3261E), fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onPair(uri, customUrl) },
            enabled = !isPairing && uri.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
        ) { Text(if (isPairing) "正在连接…" else "重新配对") }
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
