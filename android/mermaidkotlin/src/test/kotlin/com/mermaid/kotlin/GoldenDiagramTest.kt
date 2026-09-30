package com.mermaid.kotlin

import com.mermaid.kotlin.model.FlowchartDiagram
import com.mermaid.kotlin.model.SequenceDiagram
import com.mermaid.kotlin.model.StateDiagram
import com.mermaid.kotlin.parser.MermaidParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GoldenDiagramTest {
    @Test fun parsesGoldenConversationDiagramShapes() {
        val parser = MermaidParser()
        val flow = parser.parse("""
            flowchart TD
            Client[客户端] -->|隧道| Net{虚拟局域网}
            Net --> Win[服务端]
            Win --> Auth{公钥校验}
            Auth -->|通过| Shell[终端]
            Auth -->|失败| Block[拒绝访问]
        """.trimIndent()) as FlowchartDiagram
        assertEquals(5, flow.edges.size)
        assertEquals("公钥校验", flow.nodes.first { it.id == "Auth" }.label)

        val sequence = parser.parse("""
            sequenceDiagram
            autonumber
            actor Dev as 开发者
            participant Mac as Mac 终端
            Dev->>Mac: 发起连接
            Mac-->>Dev: 认证成功
        """.trimIndent()) as SequenceDiagram
        assertEquals("开发者", sequence.participants.first().label)
        assertEquals(2, sequence.messages.size)

        val state = parser.parse("""
            stateDiagram-v2
            [*] --> 离线状态
            离线状态 --> 服务已就绪: 启动服务
            服务已就绪 --> [*]: 停止服务
        """.trimIndent()) as StateDiagram
        assertEquals(3, state.transitions.size)
    }
}
