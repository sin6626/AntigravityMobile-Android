package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationDiffFile(
    val fileUri: String = "",
    val fileName: String = "",
    val actionType: String = "MODIFY",
    val originalContent: String = "",
    val modifiedContent: String = "",
    val firstTouchedStepIndex: Int? = null,
    val lastTouchedStepIndex: Int? = null,
)

@Serializable
data class ConversationDiffResponse(
    val cascadeId: String = "",
    val files: List<ConversationDiffFile> = emptyList(),
    val unavailableUris: List<String> = emptyList(),
)

enum class DiffSide { ORIGINAL, MODIFIED }

data class DiffCommentDraft(
    val fileUri: String,
    val side: DiffSide,
    val selection: ArtifactTextSelection,
    val comment: String,
)

data class DiffCommentDraftState(
    val comments: List<DiffCommentDraft> = emptyList(),
    val activeFileUri: String? = null,
    val activeSide: DiffSide? = null,
    val activeSelection: ArtifactTextSelection? = null,
    val activeComment: String = "",
)

internal enum class ReviewDiffLineKind { CONTEXT, ADDED, REMOVED, COLLAPSED }

internal data class ReviewDiffLine(
    val kind: ReviewDiffLineKind,
    val text: String,
    val originalLine: Int? = null,
    val modifiedLine: Int? = null,
    val collapsedCount: Int = 0,
)

private fun contentLines(value: String): List<String> =
    if (value.isEmpty()) emptyList() else value.split('\n').map { it.removeSuffix("\r") }

internal fun buildReviewDiff(original: String, modified: String): List<ReviewDiffLine> {
    val before = contentLines(original)
    val after = contentLines(modified)
    var prefix = 0
    while (prefix < before.size && prefix < after.size && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < before.size - prefix && suffix < after.size - prefix &&
        before[before.lastIndex - suffix] == after[after.lastIndex - suffix]
    ) suffix++

    val oldMiddle = before.subList(prefix, before.size - suffix)
    val newMiddle = after.subList(prefix, after.size - suffix)
    val rows = mutableListOf<ReviewDiffLine>()
    repeat(prefix) { index ->
        rows += ReviewDiffLine(ReviewDiffLineKind.CONTEXT, before[index], index + 1, index + 1)
    }

    if (oldMiddle.size.toLong() * newMiddle.size <= 1_000_000L) {
        val width = newMiddle.size + 1
        val lengths = IntArray((oldMiddle.size + 1) * width)
        for (oldIndex in oldMiddle.lastIndex downTo 0) {
            for (newIndex in newMiddle.lastIndex downTo 0) {
                lengths[oldIndex * width + newIndex] = if (oldMiddle[oldIndex] == newMiddle[newIndex]) {
                    lengths[(oldIndex + 1) * width + newIndex + 1] + 1
                } else maxOf(lengths[(oldIndex + 1) * width + newIndex], lengths[oldIndex * width + newIndex + 1])
            }
        }
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < oldMiddle.size || newIndex < newMiddle.size) {
            when {
                oldIndex < oldMiddle.size && newIndex < newMiddle.size && oldMiddle[oldIndex] == newMiddle[newIndex] -> {
                    rows += ReviewDiffLine(ReviewDiffLineKind.CONTEXT, oldMiddle[oldIndex],
                        prefix + oldIndex + 1, prefix + newIndex + 1)
                    oldIndex++; newIndex++
                }
                newIndex < newMiddle.size && (oldIndex == oldMiddle.size ||
                    lengths[oldIndex * width + newIndex + 1] > lengths[(oldIndex + 1) * width + newIndex]) -> {
                    rows += ReviewDiffLine(ReviewDiffLineKind.ADDED, newMiddle[newIndex],
                        modifiedLine = prefix + newIndex + 1)
                    newIndex++
                }
                else -> {
                    rows += ReviewDiffLine(ReviewDiffLineKind.REMOVED, oldMiddle[oldIndex],
                        originalLine = prefix + oldIndex + 1)
                    oldIndex++
                }
            }
        }
    } else {
        oldMiddle.forEachIndexed { index, line ->
            rows += ReviewDiffLine(ReviewDiffLineKind.REMOVED, line, originalLine = prefix + index + 1)
        }
        newMiddle.forEachIndexed { index, line ->
            rows += ReviewDiffLine(ReviewDiffLineKind.ADDED, line, modifiedLine = prefix + index + 1)
        }
    }

    repeat(suffix) { index ->
        val oldIndex = before.size - suffix + index
        val newIndex = after.size - suffix + index
        rows += ReviewDiffLine(ReviewDiffLineKind.CONTEXT, before[oldIndex], oldIndex + 1, newIndex + 1)
    }

    val visible = mutableListOf<ReviewDiffLine>()
    var index = 0
    while (index < rows.size) {
        if (rows[index].kind != ReviewDiffLineKind.CONTEXT) {
            visible += rows[index++]
            continue
        }
        val start = index
        while (index < rows.size && rows[index].kind == ReviewDiffLineKind.CONTEXT) index++
        val run = rows.subList(start, index)
        if (run.size <= 6) visible += run
        else {
            visible += run.take(3)
            visible += ReviewDiffLine(ReviewDiffLineKind.COLLAPSED, "", collapsedCount = run.size - 6)
            visible += run.takeLast(3)
        }
    }
    return visible
}
