package com.antigravity.mobile.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.mobile.data.model.ArtifactTextSelection
import com.antigravity.mobile.data.model.ConversationDiffFile
import com.antigravity.mobile.data.model.DiffCommentDraftState
import com.antigravity.mobile.data.model.DiffSide
import com.antigravity.mobile.data.model.ReviewDiffLine
import com.antigravity.mobile.data.model.ReviewDiffLineKind
import com.antigravity.mobile.data.model.buildReviewDiff
import com.antigravity.mobile.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DiffReviewScreen(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val review = state.diffReview
    var selectedUri by rememberSaveable(state.selectedConversationId) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedConversationId) { viewModel.loadDiffReview() }
    LaunchedEffect(review) {
        val files = review?.files.orEmpty()
        if (selectedUri !in files.map { it.fileUri }) selectedUri = files.firstOrNull()?.fileUri
    }

    Dialog(
        onDismissRequest = { if (!state.isSubmittingDiffComments) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("arrow_back", "关闭变更审阅", {
                    if (!state.isSubmittingDiffComments) onDismiss()
                })
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("审阅变更", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            state.isLoadingDiffReview && review == null -> "正在读取电脑上的会话变更"
                            review == null -> "当前会话变更"
                            review.files.isEmpty() -> "没有可审阅的代码变更"
                            else -> "${review.files.size} 个文件 · 点按红色或绿色行可评论"
                        },
                        color = SecondaryInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    enabled = !state.isLoadingDiffReview && !state.isSubmittingDiffComments,
                    onClick = viewModel::loadDiffReview,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "刷新当前变更" },
                ) {
                    Text("刷新", color = if (!state.isLoadingDiffReview && !state.isSubmittingDiffComments) AccentBlue
                        else SecondaryInk.copy(alpha = .45f))
                }
            }

            if (!review?.unavailableUris.isNullOrEmpty()) {
                Text(
                    "${review!!.unavailableUris.size} 个历史文件内容已不可用",
                    color = Color(0xFF8A5B00), fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            review?.files?.takeIf { it.isNotEmpty() }?.let { files ->
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(files, key = ConversationDiffFile::fileUri) { file ->
                        val selected = file.fileUri == selectedUri
                        val background by animateColorAsState(
                            if (selected) AccentBlue else Color(0xFFF2F5F9), tween(160), label = "diff-file-selection")
                        val foreground = if (selected) Color.White else Ink
                        Surface(
                            color = background,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                this.selected = selected
                                contentDescription = "${diffActionLabel(file.actionType)} ${file.fileName}"
                            }.quietClickable(RoundedCornerShape(14.dp)) { selectedUri = file.fileUri },
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(diffActionMark(file.actionType), color = foreground, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(7.dp))
                                Text(file.fileName, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val selected = review?.files?.firstOrNull { it.fileUri == selectedUri }
                when {
                    state.isLoadingDiffReview && review == null -> CircularProgressIndicator(Modifier.size(30.dp), color = AccentBlue)
                    review == null -> DiffReviewFailure(state.diffReviewError ?: "无法读取当前变更", viewModel::loadDiffReview)
                    review.files.isEmpty() -> DiffReviewEmpty(state.diffReviewError)
                    selected != null -> key(selected.fileUri) {
                        val listState = rememberSaveable(selected.fileUri, saver = LazyListState.Saver) { LazyListState() }
                        DiffFileContent(selected, listState, state.diffCommentDraft, viewModel::selectDiffComment)
                    }
                }
            }

            AnimatedVisibility(
                state.diffCommentDraft.comments.isNotEmpty() || state.diffCommentDraft.activeSelection != null ||
                    (state.diffReviewError != null && review != null),
            ) {
                DiffCommentEditor(
                    state.diffCommentDraft, review?.files.orEmpty(), state.isSubmittingDiffComments,
                    state.diffReviewError, viewModel::setDiffComment, viewModel::addDiffComment,
                    viewModel::removeDiffComment, viewModel::clearDiffCommentSelection,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !state.isSubmittingDiffComments, onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭", color = SecondaryInk) }
                val readyCount = state.diffCommentDraft.comments.size +
                    if (state.diffCommentDraft.activeSelection != null && state.diffCommentDraft.activeComment.isNotBlank()) 1 else 0
                TextButton(
                    enabled = readyCount > 0 && !state.isSubmittingDiffComments,
                    onClick = viewModel::submitDiffComments,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        stateDescription = when {
                            state.isSubmittingDiffComments -> "正在提交"
                            readyCount == 0 -> "需要先添加评论"
                            else -> "可以提交"
                        }
                    },
                ) {
                    if (state.isSubmittingDiffComments) CircularProgressIndicator(Modifier.size(18.dp), color = AccentBlue, strokeWidth = 2.dp)
                    else Text("提交反馈${if (readyCount > 0) "（$readyCount）" else ""}",
                        color = if (readyCount > 0) AccentBlue else SecondaryInk.copy(alpha = .45f))
                }
            }
        }
    }
}

@Composable
private fun DiffFileContent(
    file: ConversationDiffFile,
    listState: LazyListState,
    draft: DiffCommentDraftState,
    onSelect: (String, DiffSide, ArtifactTextSelection) -> Unit,
) {
    var lines by remember(file.fileUri, file.originalContent, file.modifiedContent) {
        mutableStateOf<List<ReviewDiffLine>?>(null)
    }
    LaunchedEffect(file.fileUri, file.originalContent, file.modifiedContent) {
        lines = withContext(Dispatchers.Default) { buildReviewDiff(file.originalContent, file.modifiedContent) }
    }
    if (lines == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp), color = AccentBlue, strokeWidth = 2.dp)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text(file.fileUri.substringBefore('?'), color = SecondaryInk, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "${file.fileName} 变更内容" },
        ) {
            itemsIndexed(lines.orEmpty(), key = { index, line ->
                "${line.kind}:${line.originalLine}:${line.modifiedLine}:$index"
            }) { _, line ->
                DiffLineRow(file.fileUri, line, draft, onSelect)
            }
        }
    }
}

@Composable
private fun DiffLineRow(
    fileUri: String,
    line: ReviewDiffLine,
    draft: DiffCommentDraftState,
    onSelect: (String, DiffSide, ArtifactTextSelection) -> Unit,
) {
    if (line.kind == ReviewDiffLineKind.COLLAPSED) {
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).background(Color(0xFFF5F7FA)), contentAlignment = Alignment.Center) {
            Text("… ${line.collapsedCount} 行未更改", color = SecondaryInk, fontSize = 12.sp)
        }
        return
    }
    val side = when (line.kind) {
        ReviewDiffLineKind.REMOVED -> DiffSide.ORIGINAL
        ReviewDiffLineKind.ADDED -> DiffSide.MODIFIED
        else -> null
    }
    val lineNumber = if (side == DiffSide.ORIGINAL) line.originalLine else line.modifiedLine
    val selected = side != null && draft.activeFileUri == fileUri && draft.activeSide == side &&
        draft.activeSelection?.startLine == lineNumber?.minus(1)
    val baseColor = when (line.kind) {
        ReviewDiffLineKind.ADDED -> Color(0xFFE7F6EC)
        ReviewDiffLineKind.REMOVED -> Color(0xFFFCEBEC)
        else -> Color.Transparent
    }
    val background by animateColorAsState(
        if (selected) AccentBlue.copy(alpha = .18f) else baseColor, tween(160), label = "diff-line-selection")
    val marker = when (line.kind) {
        ReviewDiffLineKind.ADDED -> "+"
        ReviewDiffLineKind.REMOVED -> "−"
        else -> " "
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (side == null) 36.dp else 48.dp)
            .background(background, shape)
            .then(if (side != null && lineNumber != null) Modifier.semantics {
                this.selected = selected
                contentDescription = "评论${if (side == DiffSide.ORIGINAL) "删除" else "新增"}第 $lineNumber 行"
            }.quietClickable(shape) {
                onSelect(fileUri, side, ArtifactTextSelection(line.text, lineNumber - 1, lineNumber))
            } else Modifier)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(marker, color = when (line.kind) {
            ReviewDiffLineKind.ADDED -> Color(0xFF176B37)
            ReviewDiffLineKind.REMOVED -> Color(0xFFA1252D)
            else -> SecondaryInk
        }, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
        Text(
            (lineNumber ?: line.modifiedLine ?: line.originalLine ?: 0).toString(),
            color = SecondaryInk, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.width(34.dp).padding(top = 2.dp),
        )
        Text(line.text.ifEmpty { " " }, color = Ink, fontFamily = FontFamily.Monospace,
            fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DiffCommentEditor(
    draft: DiffCommentDraftState,
    files: List<ConversationDiffFile>,
    submitting: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(horizontal = 14.dp)
            .background(Color(0xFFF5F8FC), RoundedCornerShape(16.dp)).animateContentSize(tween(180)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (draft.comments.isNotEmpty()) {
            Text("待提交评论 ${draft.comments.size} 条", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            LazyColumn(Modifier.heightIn(max = 92.dp)) {
                itemsIndexed(draft.comments) { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${files.firstOrNull { it.fileUri == item.fileUri }?.fileName ?: previewFileName(item.fileUri)} · " +
                                    "${if (item.side == DiffSide.ORIGINAL) "删除" else "新增"}第 ${item.selection.startLine + 1} 行",
                                color = AccentBlue, fontSize = 11.sp,
                            )
                            Text(item.comment, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(enabled = !submitting, onClick = { onRemove(index) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("移除", color = Color(0xFFB3261E), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        draft.activeSelection?.let { selection ->
            Text(
                "${if (draft.activeSide == DiffSide.ORIGINAL) "删除" else "新增"}第 ${selection.startLine + 1} 行",
                color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
            BasicTextField(
                value = draft.activeComment,
                onValueChange = onChange,
                enabled = !submitting,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 14.sp, lineHeight = 20.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).background(Color.White, RoundedCornerShape(12.dp))
                    .padding(11.dp).semantics { contentDescription = "变更评论内容" },
                decorationBox = { inner ->
                    Box {
                        if (draft.activeComment.isBlank()) Text("告诉代理这一行需要怎样修改", color = SecondaryInk, fontSize = 14.sp)
                        inner()
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !submitting, onClick = onCancel) { Text("取消", color = SecondaryInk) }
                TextButton(enabled = draft.activeComment.isNotBlank() && !submitting, onClick = onAdd) {
                    Text("加入评论", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp, maxLines = 2) }
    }
}

@Composable
private fun DiffReviewFailure(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = Color(0xFFB3261E), fontSize = 14.sp)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试", color = AccentBlue) }
    }
}

@Composable
private fun DiffReviewEmpty(error: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("当前会话没有代码变更", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("文件尚未修改，或历史变更内容已被电脑清理", color = SecondaryInk, fontSize = 13.sp)
        error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp) }
    }
}

private fun diffActionLabel(action: String) = when (action) {
    "CREATE" -> "新增"
    "DELETE" -> "删除"
    else -> "修改"
}

private fun diffActionMark(action: String) = when (action) {
    "CREATE" -> "+"
    "DELETE" -> "−"
    else -> "±"
}
