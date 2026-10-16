package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ConversationDiffResponse
import com.antigravity.mobile.data.model.ReviewDiffLineKind
import com.antigravity.mobile.data.model.buildReviewDiff

internal data class ConversationDiffSummary(val files: Int, val additions: Int, val deletions: Int, val unavailable: Int)

internal fun summarizeConversationDiff(review: ConversationDiffResponse): ConversationDiffSummary? {
    if (review.files.isEmpty() && review.unavailableUris.isEmpty()) return null
    var additions = 0
    var deletions = 0
    review.files.forEach { file ->
        buildReviewDiff(file.originalContent, file.modifiedContent).forEach { line ->
            if (line.kind == ReviewDiffLineKind.ADDED) additions++
            if (line.kind == ReviewDiffLineKind.REMOVED) deletions++
        }
    }
    return ConversationDiffSummary(review.files.size, additions, deletions, review.unavailableUris.size)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ConversationChangesBar(summary: ConversationDiffSummary, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(color = Color(0xFFFAFBFC), shape = shape, border = BorderStroke(1.dp, Color(0xFFE7E9ED))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).quietClickable(shape, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "审阅当前会话变更：${summary.files} 个文件，新增 ${summary.additions} 行，删除 ${summary.deletions} 行" +
                    if (summary.unavailable > 0) "，${summary.unavailable} 个文件暂不可用" else ""
            }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("当前会话变更", color = SecondaryInk, fontSize = 11.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${summary.files} 个文件", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("+${summary.additions}", color = Color(0xFF25835D), fontSize = 12.sp)
                    Text("−${summary.deletions}", color = Color(0xFFBC515A), fontSize = 12.sp)
                }
                if (summary.unavailable > 0) Text("${summary.unavailable} 个文件暂不可用", color = SecondaryInk, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("审阅", color = SecondaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = SecondaryInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}
