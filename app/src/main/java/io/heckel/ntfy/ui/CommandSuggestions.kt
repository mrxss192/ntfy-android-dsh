package io.heckel.ntfy.ui

import android.widget.PopupMenu
import com.google.android.material.textfield.TextInputEditText
import io.heckel.ntfy.util.AfterChangedTextWatcher

class CommandSuggestions(
    editText: TextInputEditText,
    commands: List<String> = listOf("/vscode", "/dsh"),
) {
    private var popupMenu: PopupMenu? = null

    init {
        editText.addTextChangedListener(AfterChangedTextWatcher {
            val cursor = editText.selectionStart
            val text = editText.text
            if (cursor < 1 || text == null || cursor > text.length) {
                popupMenu?.dismiss()
                return@AfterChangedTextWatcher
            }

            val prefix = text.subSequence(0, cursor)
            val tokenStart = prefix.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1
            val token = prefix.subSequence(tokenStart, prefix.length).toString()
            if (token != "/" || prefix.subSequence(0, tokenStart).isNotBlank()) {
                popupMenu?.dismiss()
                return@AfterChangedTextWatcher
            }

            if (popupMenu != null) return@AfterChangedTextWatcher

            val menu = PopupMenu(editText.context, editText)
            commands.forEach { command -> menu.menu.add(command) }
            menu.setOnMenuItemClickListener { item ->
                val currentText = editText.text ?: return@setOnMenuItemClickListener false
                val currentCursor = editText.selectionStart
                if (currentCursor < 1 || currentCursor > currentText.length) return@setOnMenuItemClickListener false
                val currentPrefix = currentText.subSequence(0, currentCursor)
                val currentTokenStart = currentPrefix.lastIndexOfAny(charArrayOf(' ', '\n', '\t')) + 1
                if (currentPrefix.substring(currentTokenStart) != "/" || currentPrefix.substring(0, currentTokenStart).isNotBlank()) {
                    return@setOnMenuItemClickListener false
                }

                val command = item.title.toString()
                currentText.replace(currentTokenStart, currentCursor, "$command ")
                editText.setSelection(currentTokenStart + command.length + 1)
                true
            }
            menu.setOnDismissListener {
                if (popupMenu === menu) popupMenu = null
            }
            popupMenu = menu
            menu.show()
        })
    }
}