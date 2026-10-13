package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WorkspacePathScope(
    val absoluteUri: String,
    val workspaceUrisToRelativePaths: Map<String, String> = emptyMap(),
    val numFiles: Long? = null,
    val numBytes: Long? = null,
)

@Serializable
data class WorkspaceScopeItem(
    val file: WorkspacePathScope? = null,
    val directory: WorkspacePathScope? = null,
)

@Serializable
data class WorkspaceContextItem(
    val type: String,
    val name: String,
    val relativePath: String,
    val rootName: String,
    val rootUri: String,
    val uri: String,
    val size: Long? = null,
    val scopeItem: WorkspaceScopeItem,
)

@Serializable
data class WorkspaceContextResponse(
    val workspaceUri: String,
    val items: List<WorkspaceContextItem> = emptyList(),
    val nextCursor: String? = null,
    val truncated: Boolean = false,
)

@Serializable
data class SlashCommandArgument(
    val name: String,
    val description: String = "",
    val required: Boolean = false,
    val defaultValue: String = "",
)

@Serializable
data class SlashCommandInfo(
    val name: String,
    val modelFacingText: String,
    val type: String = "",
    val icon: String = "",
    val definitionPath: String = "",
    val displayName: String = "",
    val logo: JsonElement? = null,
    val plugin: JsonElement? = null,
)

@Serializable
data class SlashCommandDefinition(
    val info: SlashCommandInfo,
    val title: String = "",
    val description: String = "",
    val arguments: List<SlashCommandArgument> = emptyList(),
)

@Serializable
data class SlashCommandsResponse(val commands: List<SlashCommandDefinition> = emptyList())

@Serializable
data class DraftComposerContext(
    val workspaceItems: List<WorkspaceContextItem> = emptyList(),
    val slashCommand: SlashCommandDefinition? = null,
)
