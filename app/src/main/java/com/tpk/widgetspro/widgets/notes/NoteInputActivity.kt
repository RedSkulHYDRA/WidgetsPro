package com.tpk.widgetspro.widgets.notes


import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.tpk.widgetspro.R

class NoteInputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_input)

        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.3).toInt()
        )
        window?.setBackgroundDrawableResource(R.drawable.rounded_layout_bg_alt)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val noteEditText = findViewById<EditText>(R.id.note_edit_text)
        val saveButton = findViewById<Button>(R.id.save_button)

        val prefs = getSharedPreferences("notes", Context.MODE_PRIVATE)
        val existingNote = prefs.getString("note_$appWidgetId", "")
        noteEditText.setText(existingNote)

        saveButton.setOnClickListener {
            val noteText = noteEditText.text.toString()
            prefs.edit().putString("note_$appWidgetId", noteText).apply()
            val appWidgetManager = AppWidgetManager.getInstance(this)
            NoteWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)

            finish()
        }
    }
}