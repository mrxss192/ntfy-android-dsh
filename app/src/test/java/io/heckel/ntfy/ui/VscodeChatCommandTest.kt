package io.heckel.ntfy.ui

import io.heckel.ntfy.db.PhoneConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun notificationWithNewSessionCodeCreatesLegacyConversation() {
        val sessionCode = "4146F34FD0F1"

        val conversation = VscodeChatConversationSummary.update(
            existing = null,
            conversationId = null,
            sessionCode = sessionCode,
            title = "VS Code 会话 $sessionCode",
            body = "任务已启动",
            status = "active",
            updatedAt = 1_000,
        )

        assertEquals("legacy:$sessionCode", conversation?.id)
        assertEquals(sessionCode, conversation?.sessionCode)
        assertEquals("任务已启动", conversation?.lastMessage)
        assertEquals(1_000, conversation?.createdAt)
    }

    @Test
    fun notificationForExistingConversationKeepsIdAndIgnoresOlderSummary() {
        val existing = PhoneConversation(
            id = "legacy:4146F34FD0F1",
            title = "Existing title",
            lastMessage = "最新消息",
            sessionCode = "4146F34FD0F1",
            status = "active",
            createdAt = 1_000,
            updatedAt = 2_000,
            unreadCount = 3,
        )

        val updated = VscodeChatConversationSummary.update(
            existing = existing,
            conversationId = null,
            sessionCode = existing.sessionCode,
            title = "Ignored title",
            body = "较旧消息",
            status = "processing",
            updatedAt = 1_500,
        )

        assertSame(existing, updated)
        assertNull(
            VscodeChatConversationSummary.update(
                existing = null,
                conversationId = null,
                sessionCode = null,
                title = "No session",
                body = "不可关联的通知",
                status = "processing",
                updatedAt = 3_000,
            )
        )
    }
}
