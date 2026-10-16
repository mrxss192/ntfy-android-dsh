package io.heckel.ntfy.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.heckel.ntfy.R
import io.heckel.ntfy.db.PhoneConversation

class VscodeHomeAdapter(
    private val onClick: (String) -> Unit,
    private val onDelete: (String, String) -> Unit,
) : RecyclerView.Adapter<VscodeHomeAdapter.Holder>() {
    private var items: List<PhoneConversation> = emptyList()

    fun submit(value: List<PhoneConversation>) {
        items = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 6).toFloat()
                setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY))
            }
            isClickable = true
            isFocusable = true
        }
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
            textSize = 15f
        }
        val delete = ImageButton(context).apply {
            setImageResource(R.drawable.ic_delete_white_20dp)
            imageTintList = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY),
            )
            background = android.util.TypedValue().let { value ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
                androidx.core.content.ContextCompat.getDrawable(context, value.resourceId)
            }
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(dp(context, 44), dp(context, 44))
        }
        val preview = TextView(context).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY))
            textSize = 13f
        }
        val metadata = TextView(context).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY))
            textSize = 12f
        }
        heading.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(delete)
        row.addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(metadata, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val params = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(dp(context, 4), dp(context, 3), dp(context, 4), dp(context, 3))
        row.layoutParams = params
        return Holder(row, title, preview, metadata, delete)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.preview.text = item.lastMessage
        val state = holder.itemView.context.getString(
            when (item.status) {
                "waiting" -> R.string.vscode_chat_waiting
                "processing" -> R.string.vscode_chat_processing
                "failed" -> R.string.vscode_chat_failed
                else -> R.string.vscode_chat_active
            },
        )
        val code = item.sessionCode?.let { " · $it" }.orEmpty()
        val unread = if (item.unreadCount > 0) " · ${item.unreadCount}" else ""
        holder.metadata.text = "$state$code$unread"
        holder.delete.contentDescription = holder.itemView.context.getString(
            R.string.vscode_chat_delete_session_for,
            item.sessionCode ?: item.title,
        )
        holder.itemView.setOnClickListener { onClick(item.id) }
        holder.delete.setOnClickListener { onDelete(item.id, item.title) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(
        itemView: LinearLayout,
        val title: TextView,
        val preview: TextView,
        val metadata: TextView,
        val delete: ImageButton,
    ) : RecyclerView.ViewHolder(itemView)

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
