package com.antigravity.mobile.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ChatModelTest {
    @Test fun accountCatalogUsesExactEnumsAndRejectsMissingPermissions() {
        val catalog = """{"userStatus":{"planStatus":{"planInfo":{"teamsTier":"PRO"}},"cascadeModelConfigData":{"clientModelConfigs":[
            {"modelId":"available","label":"Same label","modelOrAlias":{"model":"MODEL_PLACEHOLDER_M16"},"supportsImages":true,"allowedTiers":["PRO"]},
            {"modelId":"restricted","label":"Same label","modelOrAlias":{"model":"MODEL_PLACEHOLDER_M36"},"allowedTiers":["ENTERPRISE"]},
            {"modelId":"text","label":"Text","modelOrAlias":{"model":"MODEL_TEXT"}},
            {"modelId":"alias-only","label":"Alias","modelOrAlias":{"alias":"UNSPECIFIED"}},
            {"modelId":"available","label":"Duplicate","modelOrAlias":{"model":"MODEL_DUPLICATE"}}
        ]}}}"""
        val models = parseChatModels(Json.parseToJsonElement(catalog).jsonObject)
        assertEquals(listOf("available", "text"), models.map { it.id })
        assertEquals("MODEL_PLACEHOLDER_M16", models.first().model)
        assertTrue(models.first().supportsImages)
        assertFalse(models.last().supportsImages)
        assertTrue(runCatching { parseChatModels(Json.parseToJsonElement(catalog.replace("\"teamsTier\":\"PRO\"", "")).jsonObject) }.isFailure)
        assertTrue(runCatching { parseChatModels(Json.parseToJsonElement("{}").jsonObject) }.isFailure)
    }
}
