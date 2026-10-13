package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelQuotaResponse(val response: ModelQuotaSummary? = null)

@Serializable
data class ModelQuotaSummary(
    val groups: List<ModelQuotaGroup> = emptyList(),
    val description: String = "",
)

@Serializable
data class ModelQuotaGroup(
    val displayName: String = "",
    val description: String = "",
    val buckets: List<ModelQuotaBucket> = emptyList(),
)

@Serializable
data class ModelQuotaBucket(
    val bucketId: String = "",
    val displayName: String = "",
    val description: String = "",
    val window: String = "",
    val remainingFraction: Double? = null,
    val resetTime: String? = null,
    val disabled: Boolean = false,
)
