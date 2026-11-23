package com.example.modiriatmaliman

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.Editable
import android.text.TextWatcher
import java.text.NumberFormat
import java.util.Locale

private fun latinDigits(value: String): String = value
    .replace(Regex("[۰-۹]")) { (it.value[0].code - '۰'.code).toString() }
    .replace(Regex("[٠-٩]")) { (it.value[0].code - '٠'.code).toString() }

private fun persianDigits(value: String): String = value.map {
    if (it in '0'..'9') ('۰'.code + (it.code - '0'.code)).toChar() else it
}.joinToString("")

/** Gregorian date to Jalali date. Returned month/day are one-based. */
private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
    val gDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gDays[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    return intArrayOf(jy, jm, jd)
}

private fun jalaliLeapYear(year: Int): Boolean {
    return Math.floorMod(year, 33) in setOf(1, 5, 9, 13, 17, 22, 26, 30)
}

private fun validJalaliDate(year: Int, month: Int, day: Int): Boolean {
    if (year < 1200 || year > 1600 || month !in 1..12 || day < 1) return false
    val maxDay = when {
        month <= 6 -> 31
        month <= 11 -> 30
        jalaliLeapYear(year) -> 30
        else -> 29
    }
    return day <= maxDay
}

private fun todayJalali(): String {
    val c = java.util.Calendar.getInstance()
    val j = gregorianToJalali(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    return "%04d-%02d-%02d".format(Locale.US, j[0], j[1], j[2])
}

class DB(context: android.content.Context) : SQLiteOpenHelper(context, "finance.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, title TEXT NOT NULL, amount INTEGER NOT NULL, day TEXT NOT NULL, note TEXT)")
        db.execSQL("CREATE INDEX idx_entries_kind_day ON entries(kind, day)")
        db.execSQL("CREATE INDEX idx_entries_title ON entries(kind, title, day)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val rows = mutableListOf<Pair<Long, String>>()
            val cursor = db.rawQuery("SELECT id, day FROM entries", null)
            while (cursor.moveToNext()) rows.add(cursor.getLong(0) to cursor.getString(1))
            cursor.close()
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_kind_day ON entries(kind, day)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_title ON entries(kind, title, day)")
            rows.forEach { (id, rawDate) ->
                val p = rawDate.split("-")
                if (p.size == 3) {
                    val gy = p[0].toIntOrNull()
                    val gm = p[1].toIntOrNull()
                    val gd = p[2].toIntOrNull()
                    if (gy != null && gm != null && gd != null && gm in 1..12 && gd in 1..31) {
                        try {
                            val check = java.util.GregorianCalendar()
                            check.isLenient = false
                            check.clear()
                            check.set(gy, gm - 1, gd)
                            check.timeInMillis
                            val j = gregorianToJalali(gy, gm, gd)
                            val jalali = "%04d-%02d-%02d".format(Locale.US, j[0], j[1], j[2])
                            db.execSQL("UPDATE entries SET day=? WHERE id=?", arrayOf(jalali, id))
                        } catch (_: IllegalArgumentException) {
                            // Keep invalid legacy dates unchanged.
                        }
                    }
                }
            }
        }
    }

    fun add(kind: String, title: String, amount: Long, day: String, note: String) {
        writableDatabase.execSQL(
            "INSERT INTO entries(kind,title,amount,day,note) VALUES(?,?,?,?,?)",
            arrayOf(kind, title, amount, day, note)
        )
    }

    fun total(kind: String, month: String? = null): Long {
        val q = if (month == null) "SELECT COALESCE(SUM(amount),0) FROM entries WHERE kind=?"
        else "SELECT COALESCE(SUM(amount),0) FROM entries WHERE kind=? AND substr(day,1,7)=?"
        val c = if (month == null) readableDatabase.rawQuery(q, arrayOf(kind))
        else readableDatabase.rawQuery(q, arrayOf(kind, month))
        c.moveToFirst()
        val value = c.getLong(0)
        c.close()
        return value
    }

    fun suggestions(prefix: String): List<String> {
        val c = readableDatabase.rawQuery(
            "SELECT title, MAX(day) d FROM entries WHERE kind='expense' AND title LIKE ? GROUP BY title ORDER BY d DESC LIMIT 8",
            arrayOf("%$prefix%")
        )
        val result = mutableListOf<String>()
        while (c.moveToNext()) result.add(c.getString(0))
        c.close()
        return result
    }

    fun lastAmount(title: String): Long? {
        val c = readableDatabase.rawQuery(
            "SELECT amount FROM entries WHERE kind='expense' AND title=? ORDER BY day DESC,id DESC LIMIT 1",
            arrayOf(title)
        )
        val value = if (c.moveToFirst()) c.getLong(0) else null
        c.close()
        return value
    }

    fun recent(): List<String> {
        val c = readableDatabase.rawQuery("SELECT kind,title,amount,day FROM entries ORDER BY day DESC,id DESC LIMIT 20", null)
        val result = mutableListOf<String>()
        while (c.moveToNext()) {
            result.add("${persianDigits(c.getString(3))}  •  ${c.getString(1)}  •  ${fmt(c.getLong(2))} تومان  •  ${if (c.getString(0) == "income") "واریزی" else "هزینه"}")
        }
        c.close()
        return result
    }

    companion object {
        fun fmt(n: Long) = NumberFormat.getInstance(Locale("fa", "IR")).format(n)
    }
}

class MainActivity : Activity() {
    private lateinit var db: DB
    private lateinit var root: LinearLayout
    private val green = 0xFF145A43.toInt()
    private val ink = 0xFF202A25.toInt()
    private val muted = 0xFF778078.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DB(this)
        window.statusBarColor = 0xFFF4F2EC.toInt()
        window.navigationBarColor = 0xFFF5F7F6.toInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        showHome()
    }

    private fun page(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(0xFFF5F7F6.toInt())
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        return root
    }

    private fun text(s: String, size: Float = 16f, bold: Boolean = false, color: Int = ink): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            gravity = Gravity.RIGHT
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun add(v: View, h: Int = -2) {
        root.addView(v, LinearLayout.LayoutParams(-1, h).apply { bottomMargin = dp(8) })
    }

    private fun button(s: String, action: () -> Unit): Button = Button(this).apply {
        text = s
        isAllCaps = false
        textSize = 15f
        minHeight = dp(50)
        setTextColor(0xFFFFFFFF.toInt())
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(green)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0xFFB9954A.toInt())
        }
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { action() }
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        gravity = Gravity.RIGHT
        setSingleLine(true)
        setPadding(dp(12), dp(7), dp(12), dp(7))
        setBackgroundColor(0xFFFFFFFF.toInt())
    }

    private fun currentMonth() = todayJalali().substring(0, 7)

    private fun showHome() {
        page()
        add(text("مدیریت مالی من", 26f, true, green))
        add(text("خلاصه وضعیت مالی • ${persianDigits(todayJalali())}", 13f, false, muted))
        val month = currentMonth()
        val income = db.total("income", month)
        val expense = db.total("expense", month)
        val balance = db.total("income") - db.total("expense")
        add(card("واریزی‌های این ماه", income, 0xFFE5F4EC.toInt()))
        add(card("هزینه‌های این ماه", expense, 0xFFFBE9E7.toInt()))
        add(card("مانده کل تا امروز", balance, 0xFFE8EEF9.toInt()))
        add(button("＋ ثبت واریزی") { showEntry("income") })
        add(button("− ثبت هزینه") { showEntry("expense") })
        add(text("گزارش‌ها و دفتر حساب", 18f, true))
        add(button("گزارش مالی ماهانه") { showReport() })
        add(button("گزارش جامع و گردش مالی") { showComprehensiveReport() })
        add(button("دفتر حساب اشخاص") { startActivity(android.content.Intent(this, PersonAccountsActivity::class.java)) })
        add(button("درباره ما") { showAbout() })
        add(text("آخرین تراکنش‌ها", 19f, true))
        val items = db.recent()
        if (items.isEmpty()) add(text("هنوز تراکنشی ثبت نشده است.", 14f, false, muted))
        else items.forEach { add(text(it, 14f, false)) }
        add(text("تمام تاریخ‌ها شمسی هستند و اطلاعات روی همین گوشی ذخیره می‌شوند.", 12f, false, muted))
        add(text("توسعه و پشتیبانی: ترنج رایانه اسپادانا", 12f, true, muted))
    }

    private fun showAbout() {
        page()
        add(text("درباره ما", 27f, true, green))
        add(text("مدیریت مالی من", 21f, true, ink))
        add(text("راهکاری ساده و حرفه‌ای برای مدیریت دریافت‌ها، هزینه‌ها، حساب اشخاص و گزارش‌های مالی روزانه.", 15f, false, muted))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFFE5DCC8.toInt())
            }
        }
        panel.addView(text("توسعه‌دهنده و پشتیبان", 14f, false, muted))
        panel.addView(text("شرکت ترنج رایانه اسپادانا", 20f, true, green))
        panel.addView(text("کسری یاراحمدی", 18f, true, ink))
        panel.addView(text("شماره تماس: 09202209190", 16f, false, ink).apply {
            setOnClickListener {
                try { startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:09202209190"))) }
                catch (_: Exception) { Toast.makeText(this@MainActivity, "امکان باز کردن شماره‌گیر وجود ندارد", Toast.LENGTH_SHORT).show() }
            }
        })
        panel.addView(text("ایمیل: torangrayaneh@gmail.com", 16f, false, ink).apply {
            setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:torangrayaneh@gmail.com"))
                    intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "پشتیبانی مدیریت مالی من")
                    startActivity(intent)
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "برنامه ایمیل در دسترس نیست", Toast.LENGTH_SHORT).show() }
            }
        })
        add(panel)
        add(text("ترنج رایانه اسپادانا • طراحی و توسعه نرم‌افزارهای کاربردی", 13f, false, muted))
        add(button("تماس با پشتیبانی") {
            try { startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:09202209190"))) }
            catch (_: Exception) { Toast.makeText(this, "امکان باز کردن شماره‌گیر وجود ندارد", Toast.LENGTH_SHORT).show() }
        })
        add(button("ارسال ایمیل") {
            try { startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:torangrayaneh@gmail.com"))) }
            catch (_: Exception) { Toast.makeText(this, "برنامه ایمیل در دسترس نیست", Toast.LENGTH_SHORT).show() }
        })
        add(button("بازگشت به صفحه اصلی") { showHome() })
    }

    private fun card(label: String, value: Long, bg: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFFE5DCC8.toInt())
            }
        }
        box.addView(text(label, 14f, false, muted))
        box.addView(text("${DB.fmt(value)} تومان", 23f, true))
        return box
    }

    private fun showEntry(kind: String) {
        page()
        val income = kind == "income"
        add(text(if (income) "ثبت واریزی" else "ثبت هزینه", 24f, true, green))
        val title = field(if (income) "عنوان واریزی" else "عنوان هزینه (مثلاً کرایه تاکسی)")
        val amount = field("مبلغ به تومان")
        val day = field("تاریخ شمسی YYYY-MM-DD (مثلاً ${persianDigits(todayJalali())})").apply { setText(persianDigits(todayJalali())) }
        val note = field("توضیحات (اختیاری)")
        add(text("عنوان", 13f, false, muted)); add(title)
        val suggestion = TextView(this).apply { textSize = 13f; setTextColor(green); gravity = Gravity.RIGHT }
        if (!income) {
            add(suggestion)
            title.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val entered = s?.toString().orEmpty()
                    val matches = if (entered.isBlank()) emptyList() else db.suggestions(entered)
                    suggestion.text = if (matches.isEmpty()) "" else "پیشنهادها: " + matches.joinToString("، ")
                    val last = if (entered.isBlank()) null else db.lastAmount(entered)
                    if (last != null && amount.text.isNullOrBlank()) amount.setText(last.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        add(text("مبلغ", 13f, false, muted)); add(amount)
        add(text("تاریخ شمسی", 13f, false, muted)); add(day)
        add(text("توضیحات", 13f, false, muted)); add(note)
        add(button("ذخیره") {
            val titleValue = title.text.toString().trim()
            val amountValue = latinDigits(amount.text.toString()).replace(",", "").replace("٬", "").trim().toLongOrNull()
            val dateValue = latinDigits(day.text.toString()).trim()
            val parts = dateValue.split("-")
            val validDate = if (parts.size == 3 && parts.all { it.isNotEmpty() }) {
                val y = parts[0].toIntOrNull(); val m = parts[1].toIntOrNull(); val d = parts[2].toIntOrNull()
                y != null && m != null && d != null && parts[0].length == 4 && parts[1].length in 1..2 && parts[2].length in 1..2 && validJalaliDate(y, m, d)
            } else false
            if (titleValue.isEmpty() || amountValue == null || amountValue <= 0 || !validDate) {
                Toast.makeText(this, "عنوان، مبلغ معتبر و تاریخ شمسی صحیح را وارد کنید (YYYY-MM-DD)", Toast.LENGTH_LONG).show()
            } else {
                val normalizedDate = parts.joinToString("-") { it.toInt().toString().padStart(2, '0') }.let {
                    val p = it.split("-"); "${p[0].padStart(4, '0')}-${p[1]}-${p[2]}"
                }
                db.add(kind, titleValue, amountValue, normalizedDate, note.text.toString())
                Toast.makeText(this, "اطلاعات ذخیره شد", Toast.LENGTH_SHORT).show()
                showHome()
            }
        })
        add(button("بازگشت") { showHome() })
    }

    private fun showComprehensiveReport() {
        page()
        add(text("گزارش جامع مالی", 25f, true, green))
        add(text("خلاصه عملکرد مالی و گردش ثبت‌شده", 13f, false, muted))
        val month = currentMonth()
        val monthIncome = db.total("income", month)
        val monthExpense = db.total("expense", month)
        val allIncome = db.total("income")
        val allExpense = db.total("expense")
        add(card("واریزی این ماه", monthIncome, 0xFFE5F4EC.toInt()))
        add(card("هزینه این ماه", monthExpense, 0xFFFBE9E7.toInt()))
        add(card("مانده این ماه", monthIncome - monthExpense, 0xFFE8EEF9.toInt()))
        add(card("مجموع کل واریزی‌ها", allIncome, 0xFFE5F4EC.toInt()))
        add(card("مجموع کل هزینه‌ها", allExpense, 0xFFFBE9E7.toInt()))
        add(card("مانده کل", allIncome - allExpense, 0xFFE8EEF9.toInt()))
        add(text("آخرین گردش مالی", 19f, true))
        val c = db.readableDatabase.rawQuery(
            "SELECT kind,title,amount,day,note FROM entries ORDER BY day DESC,id DESC LIMIT 50", null
        )
        if (!c.moveToFirst()) add(text("تراکنشی ثبت نشده است.", 14f, false, muted))
        else do {
            val kind = c.getString(0)
            val title = c.getString(1)
            val amount = c.getLong(2)
            val day = c.getString(3)
            val note = c.getString(4).orEmpty()
            add(text((if (kind == "income") "واریزی" else "هزینه") + " • " + title, 15f, true))
            add(text(persianDigits(day) + "  |  " + DB.fmt(amount) + " تومان" +
                if (note.isNotBlank()) "  |  " + note else "", 13f, false, muted))
        } while (c.moveToNext())
        c.close()
        add(button("مشاهده گزارش جامع اشخاص") {
            startActivity(android.content.Intent(this, PersonAccountsActivity::class.java))
        })
        add(button("بازگشت به صفحه اصلی") { showHome() })
    }

    private fun showReport() {
        page()
        add(text("گزارش ماهانه", 24f, true, green))
        val month = field("ماه شمسی YYYY-MM (مثلاً ${persianDigits(currentMonth())})").apply { setText(persianDigits(currentMonth())) }
        add(text("ماه موردنظر", 14f, false, muted)); add(month)
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(result)
        fun update() {
            result.removeAllViews()
            val normalized = latinDigits(month.text.toString()).trim()
            val parts = normalized.split("-")
            val valid = parts.size == 2 && parts[0].length == 4 && (parts[1].toIntOrNull()?.let { it in 1..12 } == true)
            if (!valid) {
                result.addView(text("ماه را به صورت شمسی YYYY-MM وارد کنید", 14f))
                return
            }
            val m = "%04d-%02d".format(Locale.US, parts[0].toInt(), parts[1].toInt())
            val income = db.total("income", m)
            val expense = db.total("expense", m)
            result.addView(text("مجموع واریزی: ${DB.fmt(income)} تومان", 18f, true))
            result.addView(text("مجموع هزینه: ${DB.fmt(expense)} تومان", 18f, true))
            result.addView(text("مانده ماه: ${DB.fmt(income - expense)} تومان", 20f, true, green))
            result.addView(text("مانده کل ثبت‌شده: ${DB.fmt(db.total("income") - db.total("expense"))} تومان", 14f, false, muted))
        }
        add(button("نمایش گزارش") { update() })
        update()
        add(button("بازگشت") { showHome() })
    }
}
