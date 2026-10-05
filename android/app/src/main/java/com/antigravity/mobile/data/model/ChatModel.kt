package com.antigravity.mobile.data.model

import kotlinx.serialization.json.*

data class ChatModel(val id: String, val label: String, val model: String, val supportsImages: Boolean)

/** Account-scoped GetUserStatus catalog, with no hardcoded model aliases. */
internal fun parseChatModels(body: JsonObject): List<ChatModel> {
    val user = body["userStatus"]?.jsonObject ?: error("未返回账号模型目录")
    val tier = user["planStatus"]?.jsonObject?.get("planInfo")?.jsonObject?.get("teamsTier")?.jsonPrimitive?.contentOrNull
    val configs = user["cascadeModelConfigData"]?.jsonObject?.get("clientModelConfigs")?.jsonArray
        ?: error("未返回账号模型目录")
    return configs.mapNotNull { entry ->
        val item = entry.jsonObject
        val allowed = item["allowedTiers"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        if (allowed.isNotEmpty() && tier == null) error("未返回账号套餐，无法确认可用模型")
        if (allowed.isNotEmpty() && tier !in allowed) return@mapNotNull null
        val id = item["modelId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val label = item["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val model = item["modelOrAlias"]?.jsonObject?.get("model")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        if (id.isBlank() || label.isBlank() || !model.matches(Regex("MODEL_[A-Z0-9_]+"))) return@mapNotNull null
        ChatModel(id, label, model, item["supportsImages"]?.jsonPrimitive?.booleanOrNull == true)
    }.distinctBy { it.id }.also { check(it.isNotEmpty()) { "账号暂时没有可用模型" } }
}
