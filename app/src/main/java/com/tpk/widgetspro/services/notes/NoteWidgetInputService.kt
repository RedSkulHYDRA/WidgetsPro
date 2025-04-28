package com.tpk.widgetspro.services.notes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.CompoundButtonCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.widgets.notes.NoteWidgetProvider
import com.tpk.widgetspro.utils.CommonUtils

class NoteWidgetInputService : AppCompatActivity() {
    private var isFormatting = false
    private lateinit var noteEditText: EditText
    private lateinit var bulletToggle: CheckBox


    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NotesWidget)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_input)

        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.3).toInt()
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

        val accentColor = CommonUtils.getAccentColor(this)

        CompoundButtonCompat.setButtonTintList(bulletToggle, ColorStateList.valueOf(accentColor))
        saveButton.setBackgroundColor(accentColor)

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
                        if (source.isNotEmpty() && Character.isLetter(source[0]) && Character.isLowerCase(source[0])) {
                            source.toString().replaceFirstChar { it.uppercaseChar() }
                        } else {
                            source
                        }
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
            if (start > 0 && text[start-1] == ' ' && start > 1 && text[start-2] == '•') {
                isFormatting = true
                val newText = text.removeRange(start - 2, start)
                noteEditText.setText(newText)
                noteEditText.setSelection((start - 2).coerceAtLeast(0))
                isFormatting = false
                return
            } else if (deletedPart.contains("•")) {
                isFormatting = true
                val newText = text.replaceRange(start, end, "")
                noteEditText.setText(newText)
                noteEditText.setSelection(start)
                isFormatting = false
            }
        }
    }


    private fun handleNewLineInsertion(text: String, start: Int) {
        val currentLineStart = text.lastIndexOf('\n', start - 1) + 1
        val currentLineContent = text.substring(currentLineStart, start).trim()

        if (currentLineContent == "•" || currentLineContent.isEmpty()) {
            isFormatting = true
            val newText = StringBuilder(text)
            if (start >= 2 && text.substring(start - 2, start) == "• ") {
                newText.delete(start - 2, start)
                noteEditText.setText(newText)
                noteEditText.setSelection(start - 2)
            } else {
                noteEditText.setText(text)
                noteEditText.setSelection(start)
            }
            isFormatting = false
        } else {
            val newText = StringBuilder(text).insert(start + 1, "• ")
            isFormatting = true
            noteEditText.setText(newText)
            noteEditText.setSelection(start + 3)
            isFormatting = false
        }
    }

    private fun enforceCapitalization(s: Editable?) {
        if (!bulletToggle.isChecked || isFormatting) return
        s?.let {
            val len = it.length
            if (len >= 3 && it.startsWith("• ") && Character.isLetter(it[2]) && Character.isLowerCase(it[2])) {
                it.replace(2, 3, it[2].uppercaseChar().toString())
            }

            var i = 0
            while (i < len) {
                val nlIndex = it.indexOf('\n', i)
                if (nlIndex == -1) break
                if (nlIndex + 3 < len && it.substring(nlIndex + 1, nlIndex + 3) == "• ") {
                    if (Character.isLetter(it[nlIndex + 3]) && Character.isLowerCase(it[nlIndex + 3])) {
                        it.replace(nlIndex + 3, nlIndex + 4, it[nlIndex + 3].uppercaseChar().toString())
                    }
                }
                i = nlIndex + 1
            }
        }
    }


    private fun enforceBulletStructure(s: Editable?) {
        if (bulletToggle.isChecked && !isFormatting) {
            s?.let {
                if (it.isNotEmpty() && !it.startsWith("• ")) {
                    isFormatting = true
                    it.insert(0, "• ")
                    isFormatting = false
                }
                val lines = it.split('\n').toMutableList()
                var changed = false
                for (i in 1 until lines.size) {
                    val trimmedLine = lines[i].trimStart()
                    if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("• ")) {
                        lines[i] = "• " + lines[i].trimStart()
                        changed = true
                    }
                }
                if (changed) {
                    isFormatting = true
                    val newText = lines.joinToString("\n")
                    val currentSelection = noteEditText.selectionStart
                    it.replace(0, it.length, newText)
                    noteEditText.setSelection(currentSelection.coerceAtMost(newText.length))
                    isFormatting = false
                }
            }
        }
    }

    private fun formatWithBullets(text: String): String {
        if (text.trim().isEmpty()) return "• "
        val lines = text.split('\n')
        val formattedLines = lines.mapIndexed { index, line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.isEmpty() && index == lines.lastIndex -> ""
                trimmedLine.isEmpty() -> ""
                trimmedLine.startsWith("• ") -> line
                trimmedLine.startsWith("•") -> "• " + trimmedLine.substring(1).trimStart()
                else -> "• " + line.trimStart()
            }
        }
        val result = formattedLines.joinToString("\n")
        return if (result.isNotEmpty() && !result.startsWith("• ") && !result.startsWith("\n")) {
            "• " + result
        } else {
            result
        }
    }

    private fun removeBullets(text: String): String {
        return text.split('\n').joinToString("\n") { line ->
            if (line.trimStart().startsWith("• ")) {
                line.trimStart().substring(2).trimStart()
            } else if (line.trimStart().startsWith("•")) {
                line.trimStart().substring(1).trimStart()
            }
            else {
                line
            }
        }
    }

    private fun saveNote(appWidgetId: Int) {
        val prefs = getSharedPreferences("notes", Context.MODE_PRIVATE)
        val noteTextToSave = noteEditText.text.toString()

        prefs.edit()
            .putString("note_$appWidgetId",
                if (bulletToggle.isChecked) removeBullets(noteTextToSave) else noteTextToSave
            )
            .putBoolean("bullets_enabled_$appWidgetId", bulletToggle.isChecked)
            .apply()

        AppWidgetManager.getInstance(this).updateAppWidget(
            appWidgetId,
            NoteWidgetProvider.buildRemoteViews(this, appWidgetId)
        )

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(noteEditText.windowToken, 0)
    }
}