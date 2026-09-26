package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable

/** 与 Go 网关的 PendingInteraction / InteractionSubmitRequest 字段保持一致。 */
@Serializable
data class InteractionOption(
    val id: String = "",
    val text: String = "",
    val scope: Int = 0,
    val isDeny: Boolean = false,
)

@Serializable
data class PendingInteraction(
    val type: String = "",
    val trajectoryId: String = "",
    val stepIndex: Int = 0,
    val title: String = "",
    val target: String = "",
    val action: String = "",
    val description: String = "",
    val options: List<InteractionOption> = emptyList(),
    val isMultiSelect: Boolean = false,
    val defaultOptionId: String = "",
    val hasWriteIn: Boolean = false,
    val writeInLabel: String = "",
    val writeInPlaceholder: String = "",
)

@Serializable
data class InteractionRespondRequest(
    val cascadeId: String,
    val trajectoryId: String,
    val stepIndex: Int,
    val type: String,
    val optionId: String,
    val scope: Int,
    val allow: Boolean,
    val writeInResponse: String,
    val skipped: Boolean = false,
    val target: String = "",
)
