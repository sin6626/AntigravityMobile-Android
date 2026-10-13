package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtifactTextSelection(
    val content: String,
    val startLine: Int,
    val endLine: Int,
)

@Serializable
data class ArtifactCommentDraft(
    val selection: ArtifactTextSelection,
    val comment: String,
)

@Serializable
data class ArtifactCommentDraftState(
    val comments: List<ArtifactCommentDraft> = emptyList(),
    val activeSelection: ArtifactTextSelection? = null,
    val activeComment: String = "",
)
