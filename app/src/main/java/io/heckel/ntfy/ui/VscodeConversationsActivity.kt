package io.heckel.ntfy.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import io.heckel.ntfy.R
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.db.Notification
import io.heckel.ntfy.db.PhoneConversation
import io.heckel.ntfy.db.PhoneMessage
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.Subscription
import io.heckel.ntfy.firebase.FirebaseMessenger
import io.heckel.ntfy.msg.ApiService
import io.heckel.ntfy.msg.Poller
import io.heckel.ntfy.service.SubscriberServiceManager
import io.heckel.ntfy.util.copyToClipboard
import io.heckel.ntfy.util.decodeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class VscodeConversationsActivity : AppCompatActivity() {
    private val repository by lazy { (application as Application).repository }
    private val api by lazy { ApiService(this) }
    private val messenger = FirebaseMessenger()
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }

    private lateinit var chatRoot: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var conversationTitleView: TextView
    private lateinit var conversationStatusView: TextView
    private lateinit var list: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var composerContainer: LinearLayout
    private lateinit var composer: LinearLayout
    private lateinit var attachmentRow: LinearLayout
    private lateinit var attachmentName: TextView
    private lateinit var attachmentButton: ImageButton
    private lateinit var input: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var messageAdapter: MessageAdapter
    private var conversationId: String? = null
    private var conversations: List<PhoneConversation> = emptyList()
    private var messages: List<PhoneMessage> = emptyList()
    private var commandTopicSubscriptionId: Long? = null
    private var notificationsObserver: Observer<List<Notification>>? = null
    private var notificationsLiveData: LiveData<List<Notification>>? = null
    private var commandNotificationsObserver: Observer<List<Notification>>? = null
    private var commandNotificationsLiveData: LiveData<List<Notification>>? = null
    private var activeMessageObserver: Observer<List<PhoneMessage>>? = null
    private var activeMessageLiveData: LiveData<List<PhoneMessage>>? = null
    private var pendingNewConversation = false
    private var selectedAttachment: ChatAttachment? = null
    private var bubbleColors: Pair<Int, Int> = Color.BLUE to Color.WHITE
    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::selectAttachment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationId = savedInstanceState?.getString(EXTRA_CONVERSATION_ID)
            ?: intent.getStringExtra(EXTRA_CONVERSATION_ID)
            ?: intent.data?.takeIf { it.scheme == CHAT_SCHEME }?.pathSegments?.firstOrNull()
        pendingNewConversation = intent.getBooleanExtra(EXTRA_NEW_CONVERSATION, false)
        if (conversationId == null && !pendingNewConversation) {
            finish()
            return
        }
        buildContent()
        messageAdapter = MessageAdapter()
        list.adapter = messageAdapter
        applyChatAppearance()
        repository.getPhoneConversationsLiveData().observe(this) { value ->
            conversations = value
            if (conversationId != null) renderMode()
        }
        renderMode()
        if (conversationId != null) observeMessages(conversationId!!)

        if (readConfig() == null) {
            showSetupDialog()
        } else {
            ensureReplySubscription()
            showPendingNewConversation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        conversationId?.let { outState.putString(EXTRA_CONVERSATION_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (conversationId != null) {
            menu.add(Menu.NONE, MENU_COPY_INFO, Menu.NONE, R.string.vscode_chat_copy_session_info)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(Menu.NONE, MENU_PIN, Menu.NONE, if (isConversationPinned()) R.string.vscode_chat_unpin else R.string.vscode_chat_pin)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(Menu.NONE, MENU_APPEARANCE, Menu.NONE, R.string.vscode_chat_appearance)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.add(Menu.NONE, MENU_NEW, Menu.NONE, R.string.vscode_chat_new)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            if (conversations.firstOrNull { it.id == conversationId }?.status == STATUS_FAILED) {
                menu.add(Menu.NONE, MENU_RETRY, Menu.NONE, R.string.vscode_chat_retry)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
        menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.vscode_chat_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            MENU_NEW -> {
                showNewConversationDialog()
                true
            }
            MENU_SETTINGS -> {
                showSetupDialog()
                true
            }
            MENU_RETRY -> {
                retryLastMessage()
                true
            }
            MENU_COPY_INFO -> {
                copySessionInfo()
                true
            }
            MENU_PIN -> {
                toggleConversationPin()
                true
            }
            MENU_APPEARANCE -> {
                showAppearanceDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun buildContent() {
        chatRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val root = chatRoot
        toolbar = MaterialToolbar(this).apply {
            title = ""
            setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
            setNavigationOnClickListener { finish() }
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        conversationTitleView = TextView(this).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            maxWidth = dp(220)
            ellipsize = android.text.TextUtils.TruncateAt.END
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        conversationStatusView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
        }
        titleBlock.addView(conversationTitleView)
        titleBlock.addView(conversationStatusView)
        toolbar.addView(
            titleBlock,
            androidx.appcompat.widget.Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val content = FrameLayout(this)
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@VscodeConversationsActivity).apply { stackFromEnd = true }
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        emptyText = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(MaterialColors.getColor(this@VscodeConversationsActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY))
            text = getString(R.string.vscode_chat_empty)
        }
        content.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(emptyText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        composerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        attachmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), 0)
            isVisible = false
        }
        attachmentName = TextView(this).apply {
            setTextColor(MaterialColors.getColor(this@VscodeConversationsActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY))
        }
        attachmentRow.addView(attachmentName)
        composerContainer.addView(attachmentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        input = TextInputEditText(this).apply {
            hint = getString(R.string.vscode_chat_input_hint)
            minLines = 1
            maxLines = 5
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        sendButton = MaterialButton(this).apply {
            text = getString(R.string.vscode_chat_send)
            setOnClickListener { sendCurrentMessage() }
        }
        attachmentButton = createIconButton(R.drawable.ic_add_black_24dp, R.string.vscode_chat_add_attachment) {
            attachmentPicker.launch(arrayOf("*/*"))
        }
        composer.addView(attachmentButton)
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        composer.addView(sendButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        composerContainer.addView(composer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(composerContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            toolbar.updatePadding(top = statusBarTop)
            val toolbarLayoutParams = toolbar.layoutParams
            val toolbarHeight = dp(56) + statusBarTop
            if (toolbarLayoutParams.height != toolbarHeight) {
                toolbarLayoutParams.height = toolbarHeight
                toolbar.layoutParams = toolbarLayoutParams
            }
            view.updatePadding(top = 0, bottom = maxOf(imeBottom, systemBars.bottom))
            if (imeBottom > systemBars.bottom && ::messageAdapter.isInitialized && messageAdapter.itemCount > 0) {
                list.post { list.scrollToPosition(messageAdapter.itemCount - 1) }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun renderMode() {
        val conversation = conversations.firstOrNull { it.id == conversationId }
        composer.isVisible = conversationId != null
        conversationTitleView.text = when {
            pendingNewConversation && conversationId == null -> getString(R.string.vscode_chat_new)
            conversation?.sessionCode != null -> conversation.sessionCode
            conversation != null -> conversation.title
            else -> initialConversationTitle()
        }
        conversationStatusView.text = when (conversation?.status) {
            STATUS_ACTIVE -> getString(R.string.vscode_chat_online)
            STATUS_PROCESSING -> getString(R.string.vscode_chat_processing)
            STATUS_FAILED -> getString(R.string.vscode_chat_failed)
            else -> getString(R.string.vscode_chat_waiting)
        }
        invalidateOptionsMenu()
        updateEmptyState()
    }

    private fun isConversationPinned(): Boolean = conversationId?.let { id ->
        id in preferences.getStringSet(KEY_PINNED_CONVERSATIONS, emptySet()).orEmpty()
    } ?: false

    private fun toggleConversationPin() {
        val id = conversationId ?: return
        val pinned = preferences.getStringSet(KEY_PINNED_CONVERSATIONS, emptySet()).orEmpty().toMutableSet()
        val nowPinned = if (pinned.add(id)) true else {
            pinned.remove(id)
            false
        }
        preferences.edit().putStringSet(KEY_PINNED_CONVERSATIONS, pinned).apply()
        invalidateOptionsMenu()
        Toast.makeText(this, if (nowPinned) R.string.vscode_chat_pinned else R.string.vscode_chat_unpinned, Toast.LENGTH_SHORT).show()
    }

    private fun showAppearanceDialog() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val keys = arrayOf(KEY_BACKGROUND_COLOR, KEY_OUTGOING_COLOR, KEY_INCOMING_COLOR)
        val labelIds = intArrayOf(
            R.string.vscode_chat_background_color,
            R.string.vscode_chat_outgoing_color,
            R.string.vscode_chat_incoming_color,
        )
        val colors = intArrayOf(
            chatColor(KEY_BACKGROUND_COLOR, android.R.attr.colorBackground, Color.LTGRAY),
            chatColor(KEY_OUTGOING_COLOR, R.attr.colorPrimary, Color.BLUE),
            chatColor(KEY_INCOMING_COLOR, com.google.android.material.R.attr.colorSurface, Color.WHITE),
        )
        val colorValues = ArrayList<TextView>(colors.size)
        labelIds.forEachIndexed { index, labelId ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, dp(4), 0, dp(4))
            }
            val label = TextView(this).apply {
                text = getString(labelId)
            }
            val value = TextView(this).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                minWidth = dp(100)
            }
            fun updateValue() {
                value.text = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and colors[index])
                value.setTextColor(contrastingColor(colors[index]))
                value.background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(colors[index])
                }
            }
            updateValue()
            colorValues.add(value)
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(value)
            row.setOnClickListener {
                ColorPickerDialog.show(this, getString(labelId), colors[index]) { color ->
                    colors[index] = color
                    updateValue()
                }
            }
            fields.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vscode_chat_appearance_title)
            .setView(fields)
            .setNeutralButton(R.string.vscode_chat_appearance_reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.common_button_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                preferences.edit()
                    .remove(KEY_THEME_COLOR)
                    .remove(KEY_BACKGROUND_COLOR)
                    .remove(KEY_OUTGOING_COLOR)
                    .remove(KEY_INCOMING_COLOR)
                    .apply()
                applyChatAppearance()
                dialog.dismiss()
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val editor = preferences.edit()
                keys.forEachIndexed { index, key -> editor.putInt(key, colors[index]) }
                editor.apply()
                applyChatAppearance()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun applyChatAppearance() {
        val themeColor = MaterialColors.getColor(this, R.attr.colorPrimary, Color.BLUE)
        val backgroundColor = chatColor(KEY_BACKGROUND_COLOR, android.R.attr.colorBackground, Color.LTGRAY)
        val outgoingColor = chatColor(KEY_OUTGOING_COLOR, R.attr.colorPrimary, themeColor)
        val incomingColor = chatColor(KEY_INCOMING_COLOR, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val outlineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.LTGRAY)
        chatRoot.setBackgroundColor(backgroundColor)
        list.setBackgroundColor(backgroundColor)
        toolbar.setBackgroundColor(themeColor)
        window.statusBarColor = themeColor
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            Color.luminance(themeColor) > 0.5f
        val foregroundColor = contrastingColor(themeColor)
        conversationTitleView.setTextColor(foregroundColor)
        conversationStatusView.setTextColor(foregroundColor)
        conversationStatusView.alpha = 0.78f
        toolbar.navigationIcon?.setTint(foregroundColor)
        toolbar.overflowIcon?.setTint(foregroundColor)
        composerContainer.setBackgroundColor(surfaceColor)
        composer.setBackgroundColor(surfaceColor)
        sendButton.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        sendButton.setTextColor(foregroundColor)
        input.background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(surfaceColor)
            setStroke(dp(1), outlineColor)
        }
        attachmentButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surfaceColor)
            setStroke(dp(1), outlineColor)
        }
        attachmentButton.imageTintList = android.content.res.ColorStateList.valueOf(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK),
        )
        attachmentName.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY))
        bubbleColors = outgoingColor to incomingColor
        if (::messageAdapter.isInitialized) messageAdapter.submit(messages)
    }

    private fun chatColor(key: String, colorAttribute: Int, fallback: Int): Int =
        preferences.getInt(key, MaterialColors.getColor(this, colorAttribute, fallback))

    private fun contrastingColor(color: Int): Int = if (Color.luminance(color) > 0.5f) Color.BLACK else Color.WHITE

    private fun updateEmptyState() {
        emptyText.isVisible = messageAdapter.itemCount == 0
        list.isVisible = messageAdapter.itemCount > 0
        emptyText.text = ""
    }

    private fun initialConversationTitle(): String {
        val id = conversationId ?: return getString(R.string.vscode_chat_title)
        return id.removePrefix("legacy:").takeIf { CODE_REGEX.matches(it) }
            ?: getString(R.string.vscode_chat_title)
    }

    private fun copySessionInfo() {
        val conversation = conversations.firstOrNull { it.id == conversationId }
        val code = conversation?.sessionCode ?: conversationId
            ?.removePrefix("legacy:")
            ?.takeIf { CODE_REGEX.matches(it) }
        val info = buildString {
            append(conversation?.title ?: supportActionBar?.title ?: getString(R.string.vscode_chat_title))
            if (code != null) {
                append('\n')
                append(getString(R.string.vscode_chat_session_code_label, code))
                append('\n')
                append(getString(R.string.vscode_chat_reply_command_label, code))
            }
        }
        copyToClipboard(this, getString(R.string.vscode_chat_copy_session_info), info)
    }

    private fun openConversation(id: String) {
        activeMessageObserver?.let { observer -> activeMessageLiveData?.removeObserver(observer) }
        conversationId = id
        messages = emptyList()
        renderMode()
        observeMessages(id)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.markPhoneMessagesAsRead(id)
            repository.getPhoneConversation(id)?.let { repository.updatePhoneConversation(it.copy(unreadCount = 0)) }
        }
    }

    private fun observeMessages(id: String) {
        val source = repository.getPhoneMessagesLiveData(id)
        val observer = Observer<List<PhoneMessage>> { value ->
            messages = value
            messageAdapter.submit(value)
            updateEmptyState()
            if (value.isNotEmpty()) list.scrollToPosition(value.lastIndex)
        }
        activeMessageLiveData = source
        activeMessageObserver = observer
        source.observe(this, observer)
    }

    private fun readConfig(): ChatConfig? {
        val server = preferences.getString(KEY_SERVER, null)?.trimEnd('/') ?: return null
        val commandTopic = preferences.getString(KEY_COMMAND_TOPIC, null) ?: return null
        val replyTopic = preferences.getString(KEY_REPLY_TOPIC, null) ?: return null
        return ChatConfig(server, commandTopic, replyTopic)
    }

    private fun showSetupDialog() {
        val serverInput = EditText(this).apply {
            hint = getString(R.string.vscode_chat_server)
            setSingleLine(true)
            setText(preferences.getString(KEY_SERVER, getString(R.string.app_base_url)))
        }
        val commandInput = AutoCompleteTextView(this).apply {
            hint = getString(R.string.vscode_chat_command_topic)
            setSingleLine(true)
            setText(preferences.getString(KEY_COMMAND_TOPIC, ""))
        }
        val replyInput = AutoCompleteTextView(this).apply {
            hint = getString(R.string.vscode_chat_reply_topic)
            setSingleLine(true)
            setText(preferences.getString(KEY_REPLY_TOPIC, ""))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val topics = repository.getSubscriptions().map { it.topic }.distinct().sorted()
            withContext(Dispatchers.Main) {
                val topicAdapter = ArrayAdapter(
                    this@VscodeConversationsActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    topics,
                )
                commandInput.setAdapter(topicAdapter)
                replyInput.setAdapter(topicAdapter)
            }
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(serverInput)
            addView(commandInput)
            addView(replyInput)
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vscode_chat_setup_title)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.common_button_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val server = serverInput.text.toString().trim().trimEnd('/')
                val command = commandInput.text.toString().trim()
                val reply = replyInput.text.toString().trim()
                val parsed = Uri.parse(server)
                if ((parsed.scheme != "https" && parsed.scheme != "http") || parsed.host.isNullOrBlank()
                    || !validTopic(command) || !validTopic(reply)) {
                    Toast.makeText(this, R.string.vscode_chat_setup_error, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                preferences.edit()
                    .putString(KEY_SERVER, server)
                    .putString(KEY_COMMAND_TOPIC, command)
                    .putString(KEY_REPLY_TOPIC, reply)
                    .apply()
                dialog.dismiss()
                ensureReplySubscription()
                showPendingNewConversation()
            }
        }
        dialog.show()
    }

    private fun ensureReplySubscription() {
        val config = readConfig() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val commandSubscription = ensureTopicSubscription(
                    config,
                    config.commandTopic,
                    "VS Code commands",
                    instant = false,
                )
                val replySubscription = ensureTopicSubscription(
                    config,
                    config.replyTopic,
                    "VS Code replies",
                    instant = true,
                )
                commandTopicSubscriptionId = commandSubscription.id
                if (config.server == getString(R.string.app_base_url).trimEnd('/')) {
                    messenger.subscribe(config.replyTopic)
                }
                SubscriberServiceManager.refresh(this@VscodeConversationsActivity)

                val initialHistoryLoad = !preferences.getBoolean(historyImportKey(config), false)
                var historyLoadSucceeded = true
                val subscriptions = listOf(commandSubscription, replySubscription)
                subscriptions.forEach { subscription ->
                    try {
                        val sinceAll = if (initialHistoryLoad) {
                            subscription.copy(lastNotificationId = ApiService.SINCE_ALL)
                        } else {
                            subscription
                        }
                        Poller(api, repository).poll(sinceAll)
                    } catch (_: Exception) {
                        if (initialHistoryLoad) historyLoadSucceeded = false
                    }
                }
                withContext(Dispatchers.Main) {
                    observeReplyNotifications(replySubscription.id)
                    observeCommandNotifications(commandSubscription.id)
                }
                val subscriptionIds = subscriptions.map { it.id }.toSet()
                importNotificationsInternal(
                    repository.getNotifications().filter { it.subscriptionId in subscriptionIds },
                )
                if (initialHistoryLoad && historyLoadSucceeded) {
                    preferences.edit().putBoolean(historyImportKey(config), true).apply()
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VscodeConversationsActivity, error.message ?: getString(R.string.refresh_message_error_one, ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun ensureTopicSubscription(
        config: ChatConfig,
        topic: String,
        displayName: String,
        instant: Boolean,
    ): Subscription {
        var subscription = repository.getSubscription(config.server, topic)
        if (subscription == null) {
            subscription = Subscription(
                id = io.heckel.ntfy.util.randomSubscriptionId(),
                baseUrl = config.server,
                topic = topic,
                instant = instant,
                dedicatedChannels = false,
                mutedUntil = 0,
                minPriority = Repository.MIN_PRIORITY_USE_GLOBAL,
                autoDelete = Repository.AUTO_DELETE_USE_GLOBAL,
                insistent = Repository.INSISTENT_MAX_PRIORITY_USE_GLOBAL,
                lastNotificationId = null,
                icon = null,
                upAppId = null,
                upConnectorToken = null,
                displayName = displayName,
                totalCount = 0,
                newCount = 0,
                lastActive = System.currentTimeMillis() / 1000,
            )
            repository.addSubscription(subscription)
        } else if (instant && !subscription.instant) {
            subscription = subscription.copy(instant = true)
            repository.updateSubscription(subscription)
        }
        return subscription
    }

    private fun observeReplyNotifications(subscriptionId: Long) {
        notificationsObserver?.let { notificationsLiveData?.removeObserver(it) }
        val source = repository.getNotificationsLiveData(subscriptionId)
        val observer = Observer<List<Notification>> { notifications -> importReplies(notifications) }
        notificationsLiveData = source
        notificationsObserver = observer
        source.observe(this, observer)
    }

    private fun observeCommandNotifications(subscriptionId: Long) {
        commandNotificationsObserver?.let { commandNotificationsLiveData?.removeObserver(it) }
        val source = repository.getNotificationsLiveData(subscriptionId)
        val observer = Observer<List<Notification>> { notifications -> importReplies(notifications) }
        commandNotificationsLiveData = source
        commandNotificationsObserver = observer
        source.observe(this, observer)
    }

    private fun importReplies(notifications: List<Notification>) {
        lifecycleScope.launch(Dispatchers.IO) {
            importNotificationsInternal(notifications)
            conversationId?.let { id ->
                repository.markPhoneMessagesAsRead(id)
                repository.getPhoneConversation(id)?.let { repository.updatePhoneConversation(it.copy(unreadCount = 0)) }
            }
        }
    }

    private suspend fun importNotificationsInternal(notifications: List<Notification>) {
        notifications
            .asSequence()
            .filterNot { it.deleted }
            .sortedWith(compareBy<Notification> { it.timestamp }.thenBy { it.id })
            .forEach { notification ->
                val message = decodeMessage(notification)
                val command = if (notification.subscriptionId == commandTopicSubscriptionId) {
                    parseCommand(message)
                } else {
                    null
                }
                val metadata = parseChatMetadata(notification.click)
                val sessionCode = metadata?.sessionCode
                    ?: command?.sessionCode
                    ?: extractSessionCode("${notification.title}\n$message")
                val conversationId = metadata?.conversationId ?: command?.conversationId
                val existing = conversationId?.let { repository.getPhoneConversation(it) }
                    ?: sessionCode?.let { repository.getPhoneConversationBySessionCode(it) }
                val unboundLegacyTask = command?.takeIf {
                    it.target == "vscode" && it.conversationId == null && it.sessionCode == null
                }
                val resolvedId = conversationId
                    ?: existing?.id
                    ?: sessionCode?.let { "legacy:$it" }
                    ?: unboundLegacyTask?.let { "legacy-task:${notification.id}" }
                    ?: return@forEach
                val kind = metadata?.kind ?: inferLegacyKind(notification, command)
                val body = command?.body ?: cleanLegacyReply(message)
                if (body.isBlank()) return@forEach
                val title = existing?.title ?: command?.body?.lineSequence()?.firstOrNull()?.take(MAX_TITLE_LENGTH)
                    ?: sessionCode?.let { getString(R.string.vscode_chat_legacy_title, it) }
                    ?: getString(R.string.vscode_chat_title)
                val now = notification.timestamp * 1000
                val focused = conversationId == resolvedId
                val direction = if (command != null) "user" else if (kind == "assistant") "assistant" else "status"
                val status = when {
                    kind == "bind" || kind == "assistant" -> STATUS_ACTIVE
                    kind == "failed" -> STATUS_FAILED
                    message.contains("失败") || message.contains("未送达") -> STATUS_FAILED
                    else -> STATUS_PROCESSING
                }

                var conversation = existing
                if (conversation == null) {
                    conversation = PhoneConversation(
                        id = resolvedId,
                        title = title,
                        lastMessage = body,
                        sessionCode = sessionCode,
                        status = status,
                        createdAt = now,
                        updatedAt = now,
                        unreadCount = 0,
                    )
                    repository.addPhoneConversation(conversation)
                }

                if (command != null && repository.hasRecentPhoneMessage(
                        resolvedId,
                        "user",
                        body,
                        now - COMMAND_ECHO_WINDOW_MS,
                    )) {
                    return@forEach
                }
                val inserted = repository.addPhoneMessage(
                    PhoneMessage(
                        conversationId = resolvedId,
                        remoteId = "${notification.subscriptionId}:${notification.id}",
                        direction = direction,
                        body = body,
                        timestamp = now,
                        isRead = focused,
                    )
                )
                if (inserted < 0) return@forEach
                repository.updatePhoneConversation(
                    conversation.copy(
                        lastMessage = body,
                        sessionCode = sessionCode ?: conversation.sessionCode,
                        status = status,
                        updatedAt = now,
                        unreadCount = if (focused) 0 else conversation.unreadCount + 1,
                    )
                )
            }
    }

    private fun parseChatMetadata(click: String): ChatMetadata? {
        val uri = runCatching { Uri.parse(click) }.getOrNull() ?: return null
        if (uri.scheme != CHAT_SCHEME || uri.host != "message") return null
        val conversationId = uri.pathSegments.firstOrNull()?.takeIf(VscodeChatCommand::isConversationId) ?: return null
        val code = uri.getQueryParameter("code")?.takeIf { CODE_REGEX.matches(it) }
        return ChatMetadata(conversationId, code, uri.getQueryParameter("kind") ?: "assistant")
    }

    private fun parseCommand(message: String): LegacyCommand? {
        val newSession = NEW_SESSION_COMMAND.matchEntire(message)
        if (newSession != null) {
            return LegacyCommand(newSession.groupValues[1], null, newSession.groupValues[2].trim(), "vscode")
        }
        val reply = SESSION_REPLY_COMMAND.matchEntire(message)
        if (reply != null) {
            return LegacyCommand(
                reply.groupValues[2].ifBlank { null },
                reply.groupValues[1].uppercase(),
                reply.groupValues[3].trim(),
                "reply",
            )
        }
        val legacyNewSession = LEGACY_NEW_SESSION_COMMAND.matchEntire(message) ?: return null
        return LegacyCommand(null, extractSessionCode(message), legacyNewSession.groupValues[1].trim(), "vscode")
    }

    private fun extractSessionCode(value: String): String? = SESSION_CODE_PATTERN.find(value)?.groupValues?.get(1)?.uppercase()

    private fun inferLegacyKind(notification: Notification, command: LegacyCommand?): String {
        if (command != null) return "user"
        val searchable = "${notification.title}\n${notification.message}"
        return when {
            searchable.contains("已绑定") || searchable.contains("session bound", ignoreCase = true) -> "bind"
            searchable.contains("失败") || searchable.contains("未送达") || searchable.contains("failed", ignoreCase = true) -> "failed"
            else -> "assistant"
        }
    }

    private fun cleanLegacyReply(message: String): String = message
        .replace(LEGACY_SESSION_CODE_LINE, "")
        .replace(LEGACY_BINDING_INSTRUCTIONS, "")
        .trim()

    private fun showNewConversationDialog() {
        if (readConfig() == null) {
            showSetupDialog()
            return
        }
        val taskInput = TextInputEditText(this).apply {
            hint = getString(R.string.vscode_chat_input_hint)
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        CommandSuggestions(taskInput, listOf("/vscode"))
        val taskContainer = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(taskInput)
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vscode_chat_new)
            .setView(taskContainer)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vscode_chat_send) { _, _ ->
                val raw = taskInput.text?.toString()?.trim().orEmpty()
                val task = raw.replaceFirst(Regex("^/vscode(?:\\s+|$)", RegexOption.IGNORE_CASE), "").trim()
                if (task.isBlank()) {
                    Toast.makeText(this, R.string.vscode_chat_new_task_required, Toast.LENGTH_LONG).show()
                } else if (task.startsWith("/dsh", ignoreCase = true)) {
                    Toast.makeText(this, R.string.vscode_chat_dsh_unavailable, Toast.LENGTH_LONG).show()
                } else {
                    createConversation(task)
                }
            }
            .show()
    }

    private fun showPendingNewConversation() {
        if (!pendingNewConversation) return
        pendingNewConversation = false
        showNewConversationDialog()
    }

    private fun createConversation(task: String) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val conversation = PhoneConversation(
            id = id,
            title = task.lineSequence().first().take(MAX_TITLE_LENGTH),
            lastMessage = task,
            sessionCode = null,
            status = STATUS_WAITING,
            createdAt = now,
            updatedAt = now,
            unreadCount = 0,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addPhoneConversation(conversation)
            repository.addPhoneMessage(
                PhoneMessage(
                    conversationId = id,
                    remoteId = null,
                    direction = "user",
                    body = task,
                    timestamp = now,
                    isRead = true,
                )
            )
            withContext(Dispatchers.Main) {
                openConversation(id)
                sendMessage(id, task, addUserMessage = false)
            }
        }
    }

    private fun sendCurrentMessage() {
        val id = conversationId ?: return
        val attachment = selectedAttachment
        val typedMessage = input.text?.toString()?.trim().orEmpty()
        if (typedMessage.isBlank() && attachment == null) return
        input.text?.clear()
        selectedAttachment = null
        updateAttachmentRow()
        val message = typedMessage.ifBlank { getString(R.string.vscode_chat_attachment_only_message) }
        sendMessage(id, message, attachment = attachment)
    }

    private fun sendMessage(
        id: String,
        message: String,
        addUserMessage: Boolean = true,
        attachment: ChatAttachment? = null,
    ) {
        val config = readConfig() ?: run {
            showSetupDialog()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val conversation = repository.getPhoneConversation(id) ?: return@launch
            val now = System.currentTimeMillis()
            if (addUserMessage) {
                repository.addPhoneMessage(
                    PhoneMessage(
                        conversationId = id,
                        remoteId = null,
                        direction = "user",
                        body = message,
                        timestamp = now,
                        isRead = true,
                    )
                )
            }
            repository.updatePhoneConversation(conversation.copy(status = STATUS_PROCESSING, updatedAt = now, lastMessage = message))
            val route = VscodeChatCommand.route(conversation.sessionCode, id, message)
            try {
                api.publish(
                    config.server,
                    config.commandTopic,
                    message = route,
                    filename = attachment?.name.orEmpty(),
                    body = attachment?.let(::createAttachmentRequestBody),
                )
            } catch (error: Exception) {
                val latest = repository.getPhoneConversation(id) ?: conversation
                repository.updatePhoneConversation(latest.copy(status = STATUS_FAILED, updatedAt = System.currentTimeMillis(), lastMessage = message))
                repository.addPhoneMessage(
                    PhoneMessage(
                        conversationId = id,
                        remoteId = null,
                        direction = "status",
                        body = getString(R.string.vscode_chat_publish_error, error.message ?: ""),
                        timestamp = System.currentTimeMillis(),
                        isRead = true,
                    )
                )
                if (attachment != null) {
                    withContext(Dispatchers.Main) {
                        if (conversationId == id && selectedAttachment == null) {
                            selectedAttachment = attachment
                            updateAttachmentRow()
                        }
                    }
                }
            }
        }
    }

    private fun selectAttachment(uri: Uri) {
        var name = uri.lastPathSegment ?: getString(R.string.vscode_chat_add_attachment)
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        selectedAttachment = ChatAttachment(uri, name, mimeType, size)
        updateAttachmentRow()
    }

    private fun updateAttachmentRow() {
        val attachment = selectedAttachment
        attachmentRow.isVisible = attachment != null
        attachmentName.text = attachment?.let { getString(R.string.vscode_chat_attachment_label, it.name) }.orEmpty()
    }

    private fun createAttachmentRequestBody(attachment: ChatAttachment): RequestBody = object : RequestBody() {
        override fun contentType() = attachment.mimeType.toMediaType()
        override fun contentLength() = attachment.size
        override fun writeTo(sink: BufferedSink) {
            contentResolver.openInputStream(attachment.uri)?.use { sink.writeAll(it.source()) }
                ?: throw IOException("Unable to read selected attachment")
        }
    }

    private fun createIconButton(icon: Int, description: Int, onClick: () -> Unit): ImageButton = ImageButton(this).apply {
        val selectableBackground = TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId
        setBackgroundResource(selectableBackground)
        setImageResource(icon)
        imageTintList = android.content.res.ColorStateList.valueOf(
            MaterialColors.getColor(this@VscodeConversationsActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY),
        )
        contentDescription = getString(description)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { onClick() }
    }

    private fun retryLastMessage() {
        val id = conversationId ?: return
        val latestUserMessage = messages.lastOrNull { it.direction == "user" } ?: return
        val attachment = selectedAttachment
        selectedAttachment = null
        updateAttachmentRow()
        sendMessage(id, latestUserMessage.body, addUserMessage = false, attachment = attachment)
    }

    private fun statusLabel(status: String): String = when (status) {
        STATUS_WAITING -> getString(R.string.vscode_chat_waiting)
        STATUS_PROCESSING -> getString(R.string.vscode_chat_processing)
        STATUS_FAILED -> getString(R.string.vscode_chat_failed)
        else -> getString(R.string.vscode_chat_active)
    }

    private fun validTopic(topic: String): Boolean = topic.matches(Regex("[A-Za-z0-9_-]{1,64}"))

    private fun historyImportKey(config: ChatConfig): String =
        "history_import_${config.server}_${config.commandTopic}_${config.replyTopic}"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class ChatConfig(val server: String, val commandTopic: String, val replyTopic: String)
    private data class ChatAttachment(val uri: Uri, val name: String, val mimeType: String, val size: Long)
    private data class LegacyCommand(
        val conversationId: String?,
        val sessionCode: String?,
        val body: String,
        val target: String,
    )
    private data class ChatMetadata(val conversationId: String, val sessionCode: String?, val kind: String)

    private inner class ConversationAdapter(private val onClick: (PhoneConversation) -> Unit) : RecyclerView.Adapter<RowHolder>() {
        private var items: List<PhoneConversation> = emptyList()
        fun submit(value: List<PhoneConversation>) {
            items = value
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createConversationRow(parent)
        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val item = items[position]
            val unread = if (item.unreadCount > 0) "  ·  ${item.unreadCount}" else ""
            val code = item.sessionCode?.let { "  ·  $it" }.orEmpty()
            holder.text.text = "${item.title}\n${statusLabel(item.status)}: ${item.lastMessage}\n${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.updatedAt))}$code$unread"
            holder.text.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }

    private inner class MessageAdapter : RecyclerView.Adapter<RowHolder>() {
        private var items: List<PhoneMessage> = emptyList()
        fun submit(value: List<PhoneMessage>) {
            items = value
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createMessageRow(parent)
        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val item = items[position]
            holder.text.text = item.body
            holder.text.setTextIsSelectable(true)
            val background = if (item.direction == "user") bubbleColors.first else bubbleColors.second
            holder.text.setTextColor(contrastingColor(background))
            holder.text.gravity = Gravity.START
            holder.text.background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(background)
            }
            (holder.text.layoutParams as FrameLayout.LayoutParams).gravity = when (item.direction) {
                "user" -> Gravity.END
                "status" -> Gravity.CENTER_HORIZONTAL
                else -> Gravity.START
            }
        }
        override fun getItemCount() = items.size
    }

    private inner class RowHolder(val text: TextView, itemView: View) : RecyclerView.ViewHolder(itemView)

    private fun createConversationRow(parent: ViewGroup): RowHolder {
        val text = TextView(parent.context).apply {
            textSize = 16f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return RowHolder(text, text)
    }

    private fun createMessageRow(parent: ViewGroup): RowHolder {
        val frame = FrameLayout(parent.context)
        val text = createMessageBubble(parent)
        frame.addView(text)
        frame.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return RowHolder(text, frame)
    }

    private fun createMessageBubble(parent: ViewGroup): TextView = TextView(parent.context).apply {
        val maxBubbleWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        maxWidth = maxBubbleWidth
        textSize = 16f
        setTextColor(MaterialColors.getColor(this@VscodeConversationsActivity, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
        setPadding(dp(14), dp(10), dp(14), dp(10))
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(dp(8), dp(5), dp(8), dp(5))
        layoutParams = params
    }

    companion object {
        private const val PREFERENCES = "vscode_chat"
        private const val KEY_SERVER = "server"
        private const val KEY_COMMAND_TOPIC = "command_topic"
        private const val KEY_REPLY_TOPIC = "reply_topic"
        private const val KEY_PINNED_CONVERSATIONS = "pinned_conversations"
        private const val KEY_THEME_COLOR = "chat_theme_color"
        private const val KEY_BACKGROUND_COLOR = "chat_background_color"
        private const val KEY_OUTGOING_COLOR = "chat_outgoing_color"
        private const val KEY_INCOMING_COLOR = "chat_incoming_color"
        private const val EXTRA_CONVERSATION_ID = "conversationId"
        private const val EXTRA_NEW_CONVERSATION = "newConversation"
        private const val CHAT_SCHEME = "dsh-chat"
        private const val MENU_NEW = 1
        private const val MENU_SETTINGS = 2
        private const val MENU_RETRY = 3
        private const val MENU_COPY_INFO = 4
        private const val MENU_PIN = 5
        private const val MENU_APPEARANCE = 6
        private const val STATUS_WAITING = "waiting"
        private const val STATUS_PROCESSING = "processing"
        private const val STATUS_ACTIVE = "active"
        private const val STATUS_FAILED = "failed"
        private const val MAX_TITLE_LENGTH = 54
        private const val COMMAND_ECHO_WINDOW_MS = 5 * 60 * 1000L
        private const val UUID_TEXT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val CODE_REGEX = Regex("[A-F0-9]{12}", RegexOption.IGNORE_CASE)
        private val NEW_SESSION_COMMAND = Regex("^/vscode\\s+($UUID_TEXT)(?:\\s+|$)([\\s\\S]*)$", RegexOption.IGNORE_CASE)
        private val SESSION_REPLY_COMMAND = Regex("^/reply\\s+([A-F0-9]{12})(?:\\s+($UUID_TEXT))?(?:\\s+([\\s\\S]*))?$", RegexOption.IGNORE_CASE)
        private val LEGACY_NEW_SESSION_COMMAND = Regex("^/vscode(?:\\s+|$)([\\s\\S]*)$", RegexOption.IGNORE_CASE)
        private val SESSION_CODE_PATTERN = Regex("(?i)(?:/reply\\s+|会话短码\\s*[:：]\\s*|session\\s+code\\s*[:：]\\s*|VS Code chat code\\s*[:：]\\s*)([A-F0-9]{12})")
        private val LEGACY_SESSION_CODE_LINE = Regex("(?im)^\\s*(?:会话短码|VS Code chat code)\\s*[:：].*\\R?")
        private val LEGACY_BINDING_INSTRUCTIONS = Regex("(?im)^.*(?:手机回复请发送|phone replies?).*\\R?")
        private val LEGACY_REPLY_COMMAND_LINE = Regex("(?im)^\\s*/reply\\s+[A-F0-9]{12}.*\\R?")
    }
}
