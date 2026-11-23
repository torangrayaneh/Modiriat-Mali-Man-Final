package com.example.modiriatmaliman

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun EditText.enableThousandsGrouping() {
    addTextChangedListener(object : TextWatcher {
        private var changing = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(editable: Editable?) {
            if (changing) return
            val original = editable?.toString().orEmpty()
            val digits = original
                .replace(Regex("[۰-۹]")) { (it.value[0].code - '۰'.code).toString() }
                .replace(Regex("[٠-٩]")) { (it.value[0].code - '٠'.code).toString() }
                .filter { it in '0'..'9' }
            if (digits.isEmpty()) return
            val formatted = digits.reversed().chunked(3).joinToString(".").reversed()
            if (formatted != original) {
                val cursor = editTextCursor(original, selectionStart)
                val digitsBefore = original.take(cursor).count { it.isDigit() }
                changing = true
                setText(formatted)
                var pos = 0
                var count = 0
                while (pos < formatted.length && count < digitsBefore) {
                    if (formatted[pos].isDigit()) count++
                    pos++
                }
                setSelection(pos.coerceIn(0, formatted.length))
                changing = false
            }
        }
    })
}

private fun editTextCursor(text: String, cursor: Int): Int = cursor.coerceIn(0, text.length)
