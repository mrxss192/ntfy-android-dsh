package io.heckel.ntfy.ui

import io.heckel.ntfy.db.PhoneConversation

internal object VscodeChatConversationSummary {
    fun update(
        existing: PhoneConversation?,
        conversationId: String?,
        sessionCode: String?,
        title: String,
        body: String,
        status: String,
        updatedAt: Long,
    ): PhoneConversation? {
        if (body.isBlank()) return null
        val conversation = existing ?: run {
            val id = conversationId ?: sessionCode?.let { "legacy:$it" } ?: return null
            PhoneConversation(
                id = id,
                title = title,
                lastMessage = body,
                sessionCode = sessionCode,
                status = status,
                createdAt = updatedAt,
                updatedAt = updatedAt,
                unreadCount = 0,
            )
        }
        if (existing != null && updatedAt < existing.updatedAt) return existing
        return conversation.copy(
            lastMessage = body,
            sessionCode = sessionCode ?: conversation.sessionCode,
            status = status,
            updatedAt = updatedAt,
        )
    }
}