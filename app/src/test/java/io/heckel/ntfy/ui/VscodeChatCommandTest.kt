package io.heckel.ntfy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VscodeChatCommandTest {
    @Test
    fun legacyConversationDoesNotBecomeReplyText() {
        val sessionCode = "4146F34FD0F1"
        val message = "测试2收到回复‘测试完成’"

        assertEquals(
            "/reply $sessionCode $message",
            VscodeChatCommand.route(sessionCode, "legacy:$sessionCode", message),
        )
    }

    @Test
    fun uuidConversationIdRemainsInReplyRoute() {
        val sessionCode = "4146F34FD0F1"
        val conversationId = "123e4567-e89b-12d3-a456-426614174000"
        val message = "继续之前的任务"

        assertEquals(
            "/reply $sessionCode $conversationId $message",
            VscodeChatCommand.route(sessionCode, conversationId, message),
        )
    }
}