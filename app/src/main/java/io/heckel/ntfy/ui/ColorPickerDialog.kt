package io.heckel.ntfy.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.heckel.ntfy.R
import java.util.Locale
import kotlin.math.roundToInt

object ColorPickerDialog {
    fun show(
        context: Context,
        title: String,
        initialColor: Int,
        resetLabel: String? = null,
        onReset: (() -> Unit)? = null,
        onColorSelected: (Int) -> Unit,
    ): AlertDialog {
        val channelValues = intArrayOf(Color.red(initialColor), Color.green(initialColor), Color.blue(initialColor))
        val seekBars = arrayOfNulls<SeekBar>(3)
        val channelInputs = arrayOfNulls<EditText>(3)
        var updating = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0)
        }
        val valueRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val hexLabel = TextView(context).apply {
            text = context.getString(R.string.color_picker_hex)
        }
        val hexInput = EditText(context).apply {
            setSingleLine()
            hint = "#RRGGBB"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7))
            setText(formatColor(initialColor))
        }
        val preview = TextView(context).apply {
            gravity = Gravity.CENTER
            minHeight = dp(context, 52)
            setPadding(dp(context, 8), 0, dp(context, 8), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 4).toFloat()
            }
        }
        valueRow.addView(hexLabel, LinearLayout.LayoutParams(dp(context, 48), ViewGroup.LayoutParams.WRAP_CONTENT))
        valueRow.addView(hexInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        valueRow.addView(preview, LinearLayout.LayoutParams(dp(context, 92), dp(context, 52)).apply {
            marginStart = dp(context, 12)
        })
        content.addView(valueRow)

        val initialHsv = FloatArray(3)
        Color.colorToHSV(initialColor, initialHsv)
        var hue = initialHsv[0]
        var saturation = initialHsv[1]
        val brightnessRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val brightnessLabel = TextView(context).apply {
            text = context.getString(R.string.color_picker_brightness)
        }
        val brightnessSeekBar = SeekBar(context).apply {
            max = 100
            progress = (initialHsv[2] * 100).roundToInt()
        }
        val brightnessInput = EditText(context).apply {
            setSingleLine()
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(3))
            setText(brightnessSeekBar.progress.toString())
        }
        brightnessRow.addView(brightnessLabel, LinearLayout.LayoutParams(dp(context, 88), ViewGroup.LayoutParams.WRAP_CONTENT))
        brightnessRow.addView(brightnessSeekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        brightnessRow.addView(brightnessInput, LinearLayout.LayoutParams(dp(context, 56), ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(brightnessRow)

        val channelLabels = intArrayOf(R.string.color_picker_red, R.string.color_picker_green, R.string.color_picker_blue)
        channelLabels.forEachIndexed { index, labelId ->
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(context).apply {
                text = context.getString(labelId)
            }
            val seekBar = SeekBar(context).apply {
                max = 255
                progress = channelValues[index]
            }
            val channelInput = EditText(context).apply {
                setSingleLine()
                gravity = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(3))
                setText(channelValues[index].toString())
            }
            seekBars[index] = seekBar
            channelInputs[index] = channelInput
            row.addView(label, LinearLayout.LayoutParams(dp(context, 48), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(seekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(channelInput, LinearLayout.LayoutParams(dp(context, 56), ViewGroup.LayoutParams.WRAP_CONTENT))
            content.addView(row)
        }

        fun updatePreview() {
            val color = Color.rgb(channelValues[0], channelValues[1], channelValues[2])
            preview.setBackgroundColor(color)
            preview.text = formatColor(color)
            preview.setTextColor(if (Color.luminance(color) > 0.5f) Color.BLACK else Color.WHITE)
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            val brightness = (hsv[2] * 100).roundToInt()
            if (brightnessSeekBar.progress != brightness ||
                (!brightnessInput.hasFocus() && brightnessInput.text.toString() != brightness.toString())
            ) {
                updating = true
                brightnessSeekBar.progress = brightness
                if (!brightnessInput.hasFocus()) brightnessInput.setText(brightness.toString())
                updating = false
            }
            if (!hexInput.hasFocus() && hexInput.text.toString() != formatColor(color)) {
                updating = true
                hexInput.setText(formatColor(color))
                updating = false
            }
        }

        fun updateChannelInput(index: Int, value: Int) {
            val input = channelInputs[index] ?: return
            val text = value.toString()
            if (input.text.toString() != text) {
                val wasUpdating = updating
                updating = true
                input.setText(text)
                updating = wasUpdating
            }
        }

        fun updateHueAndSaturation(color: Int) {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hue = hsv[0]
            saturation = hsv[1]
        }

        fun applyBrightness(progress: Int) {
            val hsv = floatArrayOf(hue, saturation, progress / 100f)
            val color = Color.HSVToColor(hsv)
            channelValues[0] = Color.red(color)
            channelValues[1] = Color.green(color)
            channelValues[2] = Color.blue(color)
            updating = true
            repeat(3) { index ->
                seekBars[index]?.progress = channelValues[index]
                updateChannelInput(index, channelValues[index])
            }
            brightnessSeekBar.progress = progress
            brightnessInput.setText(progress.toString())
            hexInput.setText(formatColor(color))
            updating = false
            updatePreview()
        }

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!updating && fromUser) applyBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                hexInput.clearFocus()
                brightnessInput.clearFocus()
                channelInputs.forEach { it?.clearFocus() }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                updatePreview()
            }
        })
        brightnessInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(text: Editable?) {
                if (updating) return
                val value = text?.toString()?.toIntOrNull()?.takeIf { it in 0..100 } ?: return
                applyBrightness(value)
            }
        })

        repeat(3) { index ->
            seekBars[index]?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (updating || !fromUser) return
                    channelValues[index] = progress
                    updateChannelInput(index, progress)
                    updateHueAndSaturation(Color.rgb(channelValues[0], channelValues[1], channelValues[2]))
                    updatePreview()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    hexInput.clearFocus()
                    brightnessInput.clearFocus()
                    channelInputs.forEach { it?.clearFocus() }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    updatePreview()
                }
            })
            channelInputs[index]?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(text: Editable?) {
                    if (updating) return
                    val value = text?.toString()?.toIntOrNull()?.takeIf { it in 0..255 } ?: return
                    channelValues[index] = value
                    updating = true
                    seekBars[index]?.progress = value
                    updating = false
                    updateHueAndSaturation(Color.rgb(channelValues[0], channelValues[1], channelValues[2]))
                    updatePreview()
                }
            })
        }
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(text: Editable?) {
                if (updating) return
                val color = text?.toString()?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
                    ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                    ?: return
                channelValues[0] = Color.red(color)
                channelValues[1] = Color.green(color)
                channelValues[2] = Color.blue(color)
                updateHueAndSaturation(color)
                updating = true
                repeat(3) { index ->
                    seekBars[index]?.progress = channelValues[index]
                    updateChannelInput(index, channelValues[index])
                }
                updating = false
                updatePreview()
            }
        })
        updatePreview()

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.common_button_save, null)
        if (resetLabel != null && onReset != null) {
            builder.setNeutralButton(resetLabel, null)
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            if (resetLabel != null && onReset != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    onReset()
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val color = hexInput.text.toString().trim()
                    .takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
                    ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                if (color == null) {
                    Toast.makeText(context, R.string.color_picker_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    onColorSelected(color)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
        return dialog
    }

    private fun formatColor(color: Int): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}