package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.R

@Composable
internal fun ConversationContent(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier.width(270.dp).height(194.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(Color(0xFF23272C)),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.demo_attachment),
                            contentDescription = "示例插件设置截图",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "下载完成后怎么使用？",
                        modifier = Modifier.background(Color(0xFFE5F2FF), RoundedCornerShape(23.dp))
                            .padding(horizontal = 17.dp, vertical = 13.dp),
                        color = Color(0xFF163E63),
                        fontSize = 17.sp,
                    )
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)) {
                Text(
                    "安装完成后，通常需要重启 Android Studio 才会生效。",
                    color = Ink,
                    fontSize = 18.sp,
                    lineHeight = 29.sp,
                )
                Spacer(Modifier.height(22.dp))
                Text("你可以按这个顺序操作：", color = Ink, fontSize = 18.sp, lineHeight = 29.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "1. 安装完成后，检查右下角是否出现 Restart IDE。\n" +
                        "2. 点击重启。\n" +
                        "3. 如果界面仍是英文，再打开设置确认语言。",
                    color = Ink,
                    fontSize = 18.sp,
                    lineHeight = 31.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Symbol("content_copy", size = 20, color = Color(0xFFAAAAAA))
                    Spacer(Modifier.width(19.dp))
                    Symbol("thumb_up", size = 20, color = Color(0xFFAAAAAA))
                    Spacer(Modifier.width(19.dp))
                    Symbol("more_horiz", size = 20, color = Color(0xFFAAAAAA))
                }
            }
        }
    }
}
