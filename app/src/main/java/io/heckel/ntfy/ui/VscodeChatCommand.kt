package io.heckel.ntfy.ui

internal object VscodeChatCommand {
    private val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

    fun isConversationId(value: String): Boolean = uuidRegex.matches(value)

    fun route(sessionCode: String?, conversationId: String, message: String): String {
        val id = conversationId.takeIf(::isConversationId)
        val prefix = sessionCode?.let { code ->
            if (id == null) "/reply $code" else "/reply $code $id"
        } ?: if (id == null) "/vscode" else "/vscode $id"
        return "$prefix $message"
    }
}