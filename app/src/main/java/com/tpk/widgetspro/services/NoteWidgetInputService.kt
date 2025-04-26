package com.tpk.widgetspro.services

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider

class NoteWidgetInputService : AppCompatActivity() {
    private var isFormatting = false
    private lateinit var noteEditText: EditText
    private lateinit var bulletToggle: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_input)

        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.4).toInt()
        )
        window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ).takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID } ?: run {
            finish()
            return
        }

        noteEditText = findViewById(R.id.note_edit_text)
        val saveButton = findViewById<Button>(R.id.save_button)
        bulletToggle = findViewById(R.id.bullet_toggle)

        val prefs = getSharedPreferences("notes", Context.MODE_PRIVATE)
        val existingNote = prefs.getString("note_$appWidgetId", "") ?: ""
        val bulletsEnabled = prefs.getBoolean("bullets_enabled_$appWidgetId", true)

        setupEditText()
        bulletToggle.isChecked = bulletsEnabled
        updateTextFormatting(existingNote, bulletsEnabled)

        bulletToggle.setOnCheckedChangeListener { _, isChecked ->
            val currentText = noteEditText.text.toString()
            updateTextFormatting(currentText, isChecked)
        }

        noteEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (isFormatting || !bulletToggle.isChecked) return

                when {

                    count == 0 && before > 0 -> handleBackspace(s.toString(), start, before)

                    count == 1 && s.substring(start, start + 1) == "\n" ->
                        handleNewLineInsertion(s.toString(), start)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || !bulletToggle.isChecked) return
                enforceCapitalization(s)
                enforceBulletStructure(s)
            }
        })

        saveButton.setOnClickListener {
            saveNote(appWidgetId)
            finish()
        }
    }

    private fun setupEditText() {
        noteEditText.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_CLASS_TEXT


        noteEditText.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                if (bulletToggle.isChecked && dstart >= 2) {
                    val prefix = dest.subSequence(dstart - 2, dstart).toString()
                    if (prefix == "• " && dstart == dend) {
                        source.toString().capitalize()
                    } else {
                        source
                    }
                } else {
                    source
                }
            }
        )
    }

    private fun updateTextFormatting(text: String, bulletsEnabled: Boolean) {
        isFormatting = true
        noteEditText.setText(if (bulletsEnabled) formatWithBullets(text) else removeBullets(text))
        noteEditText.setSelection(noteEditText.text.length)
        isFormatting = false
    }

    private fun handleBackspace(text: String, start: Int, before: Int) {
        val end = start + before
        if (end >= 2) {
            val deletedPart = text.substring(start.coerceAtLeast(0), end.coerceAtMost(text.length))
            if (deletedPart.contains("•")) {
                isFormatting = true
                val newText = text.replaceRange(start, end, "")
                noteEditText.setText(newText)
                noteEditText.setSelection(start)
                isFormatting = false
            }
        }
    }

    private fun handleNewLineInsertion(text: String, start: Int) {
        val newText = StringBuilder(text).insert(start + 1, "• ")
        isFormatting = true
        noteEditText.setText(newText)
        noteEditText.setSelection(start + 3)
        isFormatting = false
    }

    private fun enforceCapitalization(s: Editable?) {
        s?.let {
            val cursorPos = noteEditText.selectionStart
            if (cursorPos >= 2 && cursorPos <= it.length) {
                val prevChars = it.subSequence(cursorPos - 2, cursorPos).toString()
                if (prevChars == "• " && cursorPos < it.length) {
                    val currentChar = it[cursorPos]
                    if (currentChar.isLetter() && currentChar.isLowerCase()) {
                        it.replace(cursorPos, cursorPos + 1, currentChar.uppercaseChar().toString())
                    }
                }
            }
        }
    }

    private fun enforceBulletStructure(s: Editable?) {
        if (bulletToggle.isChecked) {
            s?.let {
                if (it.isNotEmpty() && !it.startsWith("• ")) {
                    isFormatting = true
                    it.insert(0, "• ")
                    isFormatting = false
                }
            }
        }
    }

    private fun formatWithBullets(text: String): String {
        if (text.isEmpty()) return "• "
        return text.split("\n")
            .joinToString("\n") {
                when {
                    it.trim().isEmpty() -> ""
                    it.startsWith("• ") -> it
                    else -> "• $it"
                }
            }
    }

    private fun removeBullets(text: String): String {
        return text.replace("• ", "")
    }

    private fun saveNote(appWidgetId: Int) {
        val prefs = getSharedPreferences("notes", Context.MODE_PRIVATE)
        val noteText = noteEditText.text.toString()

        prefs.edit()
            .putString("note_$appWidgetId",
                if (bulletToggle.isChecked) removeBullets(noteText) else noteText
            )
            .putBoolean("bullets_enabled_$appWidgetId", bulletToggle.isChecked)
            .apply()

        AppWidgetManager.getInstance(this).updateAppWidget(
            appWidgetId,
            NoteWidgetProvider.buildRemoteViews(this, appWidgetId)
        )

        noteEditText.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(noteEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}