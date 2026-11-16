package com.laurence.ojtattendance

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class MainActivity : Activity() {
    private val records = mutableListOf<JSONObject>()
    private val prefs by lazy {
        val key = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            applicationContext, "ojt_secure_records", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private lateinit var root: LinearLayout
    private lateinit var completedText: TextView
    private lateinit var remainingText: TextView
    private lateinit var percentText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var estimateText: TextView
    private lateinit var scheduleText: TextView
    private lateinit var recordsBox: LinearLayout
    private lateinit var dateField: EditText
    private lateinit var inField: EditText
    private lateinit var outField: EditText
    private lateinit var lateField: EditText
    private lateinit var overtimeField: EditText
    private lateinit var presentButton: Button
    private lateinit var absentButton: Button
    private var status = "Present"
    private var editingId: String? = null

    private val target get() = prefs.getFloat("target", 1800f).toDouble()
    private val morningStart get() = prefs.getString("morning_start", "08:00")!!
    private val morningEnd get() = prefs.getString("morning_end", "12:00")!!
    private val afternoonStart get() = prefs.getString("afternoon_start", "13:00")!!
    private val afternoonEnd get() = prefs.getString("afternoon_end", "17:00")!!

    private val navy = 0xFF173B63.toInt()
    private val blue = 0xFF2563EB.toInt()
    private val ink = 0xFF172B4D.toInt()
    private val muted = 0xFF66758A.toInt()
    private val page = 0xFFF3F6FA.toInt()
    private val green = 0xFF16845B.toInt()
    private val red = 0xFFC0393A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadRecords()
        buildScreen()
        refresh()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply { setBackgroundColor(page) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 22, 18, 30)
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = rounded(navy, 20)
        }
        header.addView(text("OJT ATTENDANCE", 12f, 0xFFBBD3F4.toInt(), true))
        header.addView(text("Your training,\\nyour progress.", 27f, 0xFFFFFFFF.toInt(), true))
        header.addView(text("Keep your 1,800-hour requirement on track.", 13f, 0xFFE2EAF5.toInt()))
        root.addView(header, params(bottom = 14))

        val progress = card()
        progress.addView(text("TOTAL PROGRESS", 12f, muted, true))
        val totals = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        completedText = text("0.0", 31f, ink, true)
        totals.addView(completedText, LinearLayout.LayoutParams(0, -2, 1f))
        percentText = text("0%", 18f, blue, true)
        totals.addView(percentText)
        progress.addView(totals)
        progress.addView(text("hours completed", 12f, muted))
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE5EAF1.toInt())
        }
        progress.addView(progressBar, params(top = 12, bottom = 8))
        remainingText = text("1,800 hours remaining", 14f, ink, true)
        progress.addView(remainingText)
        estimateText = text("", 12f, muted)
        progress.addView(estimateText, params(top = 6))
        root.addView(progress, params(bottom = 12))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(actionButton("Calendar", navy) { showCalendar() }, LinearLayout.LayoutParams(0, 48, 1f).apply { rightMargin = 6 })
        quick.addView(actionButton("Settings", 0xFFE5EDF8.toInt(), navy) { showSettings() }, LinearLayout.LayoutParams(0, 48, 1f).apply { leftMargin = 6 })
        root.addView(quick, params(bottom = 12))

        val form = card()
        form.addView(text("DAILY ATTENDANCE", 12f, blue, true))
        form.addView(text("Add your workday", 20f, ink, true), params(top = 3, bottom = 10))
        dateField = field("Attendance date", today()).apply { setOnClickListener { chooseDate() } }
        form.addView(dateField)
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        inField = field("Time in", morningStart).apply { setOnClickListener { chooseTime(this) } }
        outField = field("Time out", afternoonEnd).apply { setOnClickListener { chooseTime(this) } }
        times.addView(inField, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 5 })
        times.addView(outField, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 5 })
        form.addView(times)
        form.addView(text("Attendance status", 12f, muted, true), params(top = 5, bottom = 4))
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presentButton = actionButton("Present", green) { setStatus("Present") }
        absentButton = actionButton("Absent", 0xFFE9EDF3.toInt(), muted) { setStatus("Absent") }
        statusRow.addView(presentButton, LinearLayout.LayoutParams(0, 46, 1f).apply { rightMargin = 5 })
        statusRow.addView(absentButton, LinearLayout.LayoutParams(0, 46, 1f).apply { leftMargin = 5 })
        form.addView(statusRow)
        val extras = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateField = field("Late hours", "0")
        overtimeField = field("Overtime hours", "0")
        lateField.setOnClickListener { askHours("Enter late hours", lateField) }
        overtimeField.setOnClickListener { askHours("Enter overtime hours", overtimeField) }
        extras.addView(lateField, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 5 })
        extras.addView(overtimeField, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 5 })
        form.addView(extras)
        form.addView(text("Tap either field to enter the exact number of hours. Example: 0.5 = 30 minutes.", 11f, muted))
        form.addView(actionButton("Save attendance", blue) { saveRecord() }, params(top = 12))
        form.addView(actionButton("Clear form", 0xFFE9EDF3.toInt(), navy) { clearForm() }, params(top = 6))
        root.addView(form, params(bottom = 12))

        val history = card()
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text("ATTENDANCE HISTORY", 12f, blue, true), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(actionButton("Export CSV", 0xFFE5EDF8.toInt(), navy) { exportCsv() })
        history.addView(titleRow)
        scheduleText = text("", 12f, muted)
        history.addView(scheduleText, params(top = 8, bottom = 6))
        recordsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        history.addView(recordsBox)
        root.addView(history)
        root.addView(text("Saved securely on this device • Works offline", 11f, muted).apply { gravity = Gravity.CENTER }, params(top = 14))
    }

    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16, 16, 16, 16)
        background = rounded(0xFFFFFFFF.toInt(), 18)
        elevation = dp(2).toFloat()
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun field(hintText: String, value: String): EditText =
        EditText(this).apply {
            hint = hintText; setText(value); textSize = 14f; isSingleLine = true
            setTextColor(ink); setHintTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(0xFFF5F7FA.toInt(), 10)
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) }
        }
    private fun actionButton(label: String, color: Int, textColor: Int = 0xFFFFFFFF.toInt(), click: () -> Unit) =
        Button(this).apply {
            text = label; isAllCaps = false; textSize = 13f
            setTextColor(textColor); background = rounded(color, 11)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { click() }
        }

    private fun setStatus(value: String) {
        status = value
        presentButton.background = rounded(if (value == "Present") green else 0xFFE9EDF3.toInt(), 11)
        presentButton.setTextColor(if (value == "Present") 0xFFFFFFFF.toInt() else muted)
        absentButton.background = rounded(if (value == "Absent") red else 0xFFE9EDF3.toInt(), 11)
        absentButton.setTextColor(if (value == "Absent") 0xFFFFFFFF.toInt() else muted)
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun chooseDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            dateField.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun chooseTime(targetField: EditText) {
        val parts = targetField.text.toString().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(this, { _, hour, minute ->
            targetField.setText(String.format(Locale.US, "%02d:%02d", hour, minute))
        }, h, m, true).show()
    }
    private fun askHours(title: String, destination: EditText) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Hours (0.5 = 30 minutes)"
            setText(destination.text.toString())
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim().ifEmpty { "0" }.toDoubleOrNull()
                if (n == null || n < 0) toast("Enter a valid number.") else destination.setText(n.toString())
            }.show()
    }
    private fun minutes(s: String): Int? {
        val p = s.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }
    private fun dailyMinutes(start: String, end: String): Int {
        val a = minutes(start) ?: return 0
        val b = minutes(end) ?: return 0
        return (b - a).coerceAtLeast(0)
    }
    private fun workedHours(start: String, end: String): Double {
        val a = minutes(start) ?: return -1.0
        val b = minutes(end) ?: return -1.0
        if (b <= a) return -1.0
        val ms = minutes(morningStart) ?: return -1.0
        val me = minutes(morningEnd) ?: return -1.0
        val asf = minutes(afternoonStart) ?: return -1.0
        val ae = minutes(afternoonEnd) ?: return -1.0
        fun overlap(x1: Int, x2: Int, y1: Int, y2: Int) =
            (minOf(x2, y2) - maxOf(x1, y1)).coerceAtLeast(0)
        val regular = overlap(a, b, ms, me) + overlap(a, b, asf, ae)
        return regular / 60.0
    }

    private fun saveRecord() {
        val date = dateField.text.toString().trim()
        val late = lateField.text.toString().trim().ifEmpty { "0" }.toDoubleOrNull()
        val overtime = overtimeField.text.toString().trim().ifEmpty { "0" }.toDoubleOrNull()
        val worked = if (status == "Present") workedHours(inField.text.toString(), outField.text.toString()) else 0.0
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || late == null || overtime == null ||
            late < 0 || overtime < 0 || (status == "Present" && worked <= 0)) {
            toast("Check the date, time in/out, late and overtime values.")
            return
        }
        val id = editingId ?: System.currentTimeMillis().toString()
        val item = JSONObject()
            .put("id", id).put("date", date).put("timeIn", inField.text.toString())
            .put("timeOut", outField.text.toString()).put("status", status)
            .put("hours", worked + if (status == "Present") overtime else 0.0)
            .put("late", late).put("overtime", overtime)
        val old = records.indexOfFirst { it.optString("id") == id || it.optString("date") == date }
        if (old >= 0) records[old] = item else records.add(item)
        records.sortBy { it.optString("date") }
        persist()
        clearForm()
        refresh()
        toast("Attendance saved.")
    }
    private fun clearForm() {
        editingId = null
        dateField.setText(today())
        inField.setText(morningStart)
        outField.setText(afternoonEnd)
        lateField.setText("0")
        overtimeField.setText("0")
        setStatus("Present")
    }
    private fun loadRecords() {
        records.clear()
        val array = JSONArray(prefs.getString("records", "[]"))
        for (i in 0 until array.length()) records.add(array.getJSONObject(i))
    }
    private fun persist() {
        val array = JSONArray()
        records.forEach { array.put(it) }
        prefs.edit().putString("records", array.toString()).apply()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun refresh() {
        val done = records.sumOf { it.optDouble("hours", 0.0) }
        val safeTarget = target.coerceAtLeast(1.0)
        val percent = (done / safeTarget * 100).coerceIn(0.0, 100.0)
        completedText.text = String.format(Locale.US, "%.1f", done)
        percentText.text = String.format(Locale.US, "%.0f%%", percent)
        progressBar.progress = (percent * 10).toInt()
        remainingText.text = String.format(Locale.US, "%.1f hours remaining of %.1f", (safeTarget - done).coerceAtLeast(0.0), safeTarget)
        scheduleText.text = "Work schedule: " + morningStart + "–" + morningEnd + " and " + afternoonStart + "–" + afternoonEnd
        updateEstimate(done)
        recordsBox.removeAllViews()
        if (records.isEmpty()) {
            recordsBox.addView(text("No attendance records yet. Add your first day above.", 13f, muted))
            return
        }
        records.asReversed().forEach { item ->
            val entry = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(0xFFF5F7FA.toInt(), 12)
                layoutParams = params(bottom = 8)
            }
            val statusColor = if (item.optString("status") == "Absent") red else green
            entry.addView(text(item.optString("date") + "  •  " + item.optString("status"), 14f, statusColor, true))
            entry.addView(text(item.optString("timeIn") + " to " + item.optString("timeOut") +
                "  |  " + String.format(Locale.US, "%.2f", item.optDouble("hours")) + " credited hours", 12f, ink))
            entry.addView(text("Late: " + String.format(Locale.US, "%.2f", item.optDouble("late")) +
                " h    Overtime: " + String.format(Locale.US, "%.2f", item.optDouble("overtime")) + " h", 11f, muted))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            buttons.addView(actionButton("Edit", 0xFFE5EDF8.toInt(), navy) { edit(item) })
            buttons.addView(actionButton("Delete", 0xFFFCE8E8.toInt(), red) {
                AlertDialog.Builder(this@MainActivity).setMessage("Delete this attendance record?")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        records.removeAll { it.optString("id") == item.optString("id") }
                        persist(); refresh()
                    }.show()
            }, LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(6) })
            entry.addView(buttons, params(top = 5))
            recordsBox.addView(entry)
        }
    }
    private fun edit(item: JSONObject) {
        editingId = item.optString("id")
        dateField.setText(item.optString("date"))
        inField.setText(item.optString("timeIn"))
        outField.setText(item.optString("timeOut"))
        lateField.setText(item.optDouble("late", 0.0).toString())
        overtimeField.setText(item.optDouble("overtime", 0.0).toString())
        setStatus(item.optString("status", "Present"))
        Toast.makeText(this, "Update the fields and tap Save attendance.", Toast.LENGTH_SHORT).show()
    }

    private fun updateEstimate(done: Double) {
        val remaining = (target - done).coerceAtLeast(0.0)
        if (remaining <= 0) {
            estimateText.text = "Requirement completed. Great work!"
            return
        }
        val dayHours = workedHours(morningStart, afternoonEnd)
        if (dayHours <= 0) {
            estimateText.text = "Set valid working hours in Settings to estimate completion."
            return
        }
        var days = ceil(remaining / dayHours).toInt()
        val date = Calendar.getInstance()
        date.add(Calendar.DAY_OF_MONTH, 1)
        while (days > 0) {
            if (date.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) days--
            if (days > 0) date.add(Calendar.DAY_OF_MONTH, 1)
        }
        estimateText.text = "Estimated finish: " + SimpleDateFormat("MMM d, yyyy", Locale.US).format(date.time) +
            "  •  assumes 6 workdays/week, no holidays"
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val goal = field("Required OJT hours", target.toString().removeSuffix(".0"))
        val ms = field("Morning start", morningStart)
        val me = field("Morning end", morningEnd)
        val asf = field("Afternoon start", afternoonStart)
        val ae = field("Afternoon end", afternoonEnd)
        listOf(goal, ms, me, asf, ae).forEach { panel.addView(it) }
        listOf(ms, me, asf, ae).forEach { f -> f.setOnClickListener { chooseTime(f) } }
        AlertDialog.Builder(this).setTitle("Customize OJT settings")
            .setMessage("Default: 8 AM–12 NN and 1 PM–5 PM. You can change the target and schedule.")
            .setView(panel).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val g = goal.text.toString().toDoubleOrNull()
                val a = minutes(ms.text.toString()); val b = minutes(me.text.toString())
                val c = minutes(asf.text.toString()); val d = minutes(ae.text.toString())
                if (g == null || g <= 0 || a == null || b == null || c == null || d == null || a >= b || b > c || c >= d) {
                    toast("Please check the target and working times.")
                } else {
                    prefs.edit().putFloat("target", g.toFloat())
                        .putString("morning_start", ms.text.toString())
                        .putString("morning_end", me.text.toString())
                        .putString("afternoon_start", asf.text.toString())
                        .putString("afternoon_end", ae.text.toString()).apply()
                    inField.setText(morningStart); outField.setText(afternoonEnd)
                    refresh(); toast("Settings saved.")
                }
            }.show()
    }

    private fun showCalendar() {
        val month = Calendar.getInstance()
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(4)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = text("", 16f, ink, true).apply { gravity = Gravity.CENTER }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun draw() {
            title.text = SimpleDateFormat("MMMM yyyy", Locale.US).format(month.time)
            grid.removeAllViews()
            val names = listOf("S", "M", "T", "W", "T", "F", "S")
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            names.forEach { n -> head.addView(text(n, 11f, muted, true).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f) }) }
            grid.addView(head)
            val first = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val offset = first.get(Calendar.DAY_OF_WEEK) - 1
            val max = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            var day = 1
            for (week in 0..5) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (col in 0..6) {
                    val cell = text("", 11f, ink).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f) }
                    if ((week > 0 || col >= offset) && day <= max) {
                        val key = String.format(Locale.US, "%04d-%02d-%02d", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1, day)
                        val record = records.firstOrNull { it.optString("date") == key }
                        cell.text = if (record == null) day.toString() else day.toString() + "\\n" + if (record.optString("status") == "Absent") "A" else "P"
                        if (record != null) {
                            cell.setTextColor(if (record.optString("status") == "Absent") red else green)
                            cell.setTypeface(null, Typeface.BOLD)
                        }
                        day++
                    }
                    row.addView(cell)
                }
                grid.addView(row)
                if (day > max) break
            }
        }
        header.addView(actionButton("‹", 0xFFE5EDF8.toInt(), navy) { month.add(Calendar.MONTH, -1); draw() })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("›", 0xFFE5EDF8.toInt(), navy) { month.add(Calendar.MONTH, 1); draw() })
        panel.addView(header); panel.addView(grid)
        panel.addView(text("P = Present     A = Absent     Blank = No record", 11f, muted))
        draw()
        AlertDialog.Builder(this).setTitle("Attendance calendar").setView(panel).setPositiveButton("Close", null).show()
    }

    private fun exportCsv() {
        if (records.isEmpty()) { toast("No attendance records to export."); return }
        val csv = StringBuilder("Date,Status,Time In,Time Out,Credited Hours,Late Hours,Overtime Hours\\n")
        records.forEach { item ->
            csv.append(item.optString("date")).append(",")
                .append(item.optString("status")).append(",")
                .append(item.optString("timeIn")).append(",")
                .append(item.optString("timeOut")).append(",")
                .append(item.optDouble("hours")).append(",")
                .append(item.optDouble("late")).append(",")
                .append(item.optDouble("overtime")).append("\\n")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "OJT Attendance Records")
            putExtra(Intent.EXTRA_TEXT, csv.toString())
        }
        startActivity(Intent.createChooser(send, "Export attendance CSV"))
    }
}