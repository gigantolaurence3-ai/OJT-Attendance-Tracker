package com.laurence.ojtattendance

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val records = mutableListOf<JSONObject>()
    private val prefs by lazy { getSharedPreferences("ojt_records", MODE_PRIVATE) }
    private lateinit var dateField: EditText
    private lateinit var inField: EditText
    private lateinit var outField: EditText
    private lateinit var progressLabel: TextView
    private lateinit var remainingLabel: TextView
    private lateinit var scheduleLabel: TextView
    private lateinit var recordsBox: LinearLayout
    private var editingId: String? = null

    private val targetHours get() = prefs.getFloat("target_hours", 1800f).toDouble()
    private val morningStart get() = prefs.getString("morning_start", "08:00")!!
    private val morningEnd get() = prefs.getString("morning_end", "12:00")!!
    private val afternoonStart get() = prefs.getString("afternoon_start", "13:00")!!
    private val afternoonEnd get() = prefs.getString("afternoon_end", "17:00")!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadRecords()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFFF3F5F8.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "OJT Attendance Tracker"
            textSize = 25f
            setTextColor(0xFF14243A.toInt())
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(label("Offline attendance record"))
        progressLabel = label("")
        progressLabel.textSize = 19f
        progressLabel.setTypeface(null, Typeface.BOLD)
        root.addView(progressLabel)
        remainingLabel = label("")
        root.addView(remainingLabel)
        root.addView(button("Customize Target & Work Hours") { showSettings() })
        scheduleLabel = label("")
        root.addView(scheduleLabel)

        root.addView(section("DAILY ATTENDANCE"))
        dateField = field("Date (YYYY-MM-DD)", today())
        dateField.setOnClickListener { chooseDate() }
        root.addView(dateField)
        inField = field("Time In (HH:MM)", "08:00")
        inField.setOnClickListener { chooseTime(inField) }
        root.addView(inField)
        outField = field("Time Out (HH:MM)", "17:00")
        outField.setOnClickListener { chooseTime(outField) }
        root.addView(outField)
        root.addView(label("Late time and overtime are calculated automatically from your Time In and Time Out. Lunch break is excluded."))
        root.addView(button("Save Attendance") { saveRecord() })
        root.addView(button("Clear Form") { clearForm() })
        root.addView(button("Export CSV") { exportCsv() })
        root.addView(section("ATTENDANCE RECORDS"))
        recordsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(recordsBox)
        refresh()
    }

    private fun label(s: String) = TextView(this).apply {
        text = s; textSize = 14f; setTextColor(0xFF344054.toInt()); setPadding(0, 10, 0, 10)
    }
    private fun section(s: String) = TextView(this).apply {
        text = s; textSize = 13f; setTypeface(null, Typeface.BOLD)
        setTextColor(0xFF176B87.toInt()); setPadding(0, 22, 0, 8)
    }
    private fun field(h: String, v: String) = EditText(this).apply {
        hint = h; setText(v); textSize = 16f; setSingleLine(true); setPadding(14, 10, 14, 10)
        setBackgroundColor(0xFFFFFFFF.toInt())
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 }
    }
    private fun button(s: String, action: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { action() }
    }
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun chooseDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            dateField.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun chooseTime(target: EditText) {
        val c = Calendar.getInstance()
        val parts = target.text.toString().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: c.get(Calendar.HOUR_OF_DAY)
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: c.get(Calendar.MINUTE)
        TimePickerDialog(this, { _, h, m ->
            target.setText(String.format(Locale.US, "%02d:%02d", h, m))
        }, hour, minute, true).show()
    }
    private fun minutes(value: String): Int? {
        val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value) ?: return null
        val h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }
    private fun shiftMinutes(start: String, end: String): Int {
        val a = minutes(start) ?: return -1
        val b = minutes(end) ?: return -1
        val finish = if (b <= a) b + 1440 else b
        val lunchStart = minutes(morningEnd)!!
        val lunchEnd = minutes(afternoonStart)!!
        val lunchOverlap = (minOf(finish, lunchEnd) - maxOf(a, lunchStart)).coerceAtLeast(0)
        return (finish - a - lunchOverlap).coerceAtLeast(0)
    }

    private fun lateMinutes(start: String): Int {
        val a = minutes(start) ?: return 0
        return (a - minutes(morningStart)!!).coerceAtLeast(0)
    }

    private fun overtimeMinutes(start: String, end: String): Int {
        val a = minutes(start) ?: return 0
        val b = minutes(end) ?: return 0
        val finish = if (b <= a) b + 1440 else b
        val early = (minutes(morningStart)!! - a).coerceAtLeast(0)
        val after = (finish - minutes(afternoonEnd)!!).coerceAtLeast(0)
        return early + after
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val target = field("Required total hours (example: 1800)", targetHours.toString().removeSuffix(".0"))
        val ms = field("Morning start", morningStart)
        val me = field("Morning end", morningEnd)
        val asf = field("Afternoon start", afternoonStart)
        val ae = field("Afternoon end", afternoonEnd)
        listOf(target, ms, me, asf, ae).forEach { panel.addView(it) }
        listOf(ms, me, asf, ae).forEach { edit ->
            edit.setOnClickListener { chooseTime(edit) }
        }
        AlertDialog.Builder(this)
            .setTitle("Customize OJT Hours")
            .setMessage("Default schedule: 8:00 AM–12:00 NN and 1:00 PM–5:00 PM. Change any value below.")
            .setView(panel)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val goal = target.text.toString().trim().toDoubleOrNull()
                val m1 = minutes(ms.text.toString().trim())
                val m2 = minutes(me.text.toString().trim())
                val a1 = minutes(asf.text.toString().trim())
                val a2 = minutes(ae.text.toString().trim())
                if (goal == null || goal <= 0 || m1 == null || m2 == null || a1 == null || a2 == null ||
                    m1 >= m2 || m2 > a1 || a1 >= a2) {
                    Toast.makeText(this, "Check the target and times. Use start, end, lunch break, then afternoon hours.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit()
                    .putFloat("target_hours", goal.toFloat())
                    .putString("morning_start", ms.text.toString().trim())
                    .putString("morning_end", me.text.toString().trim())
                    .putString("afternoon_start", asf.text.toString().trim())
                    .putString("afternoon_end", ae.text.toString().trim())
                    .apply()
                refresh()
                Toast.makeText(this, "Work hours updated.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun saveRecord() {
        val date = dateField.text.toString().trim()
        val tin = inField.text.toString().trim()
        val tout = outField.text.toString().trim()
        val workedMinutes = shiftMinutes(tin, tout)
        val late = lateMinutes(tin)
        val overtime = overtimeMinutes(tin, tout)
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || workedMinutes <= 0) {
            Toast.makeText(this, "Please enter a valid date and times within your work schedule.", Toast.LENGTH_LONG).show()
            return
        }
        val id = editingId ?: System.currentTimeMillis().toString()
        val hours = workedMinutes / 60.0
        val item = JSONObject().put("id", id).put("date", date).put("timeIn", tin).put("timeOut", tout)
            .put("hours", hours).put("lateMinutes", late).put("overtimeMinutes", overtime)
        val index = records.indexOfFirst { it.optString("id") == id }
        if (index >= 0) records[index] = item else records.add(item)
        records.sortBy { it.optString("date") }
        persist()
        clearForm()
        refresh()
        Toast.makeText(this, "Attendance saved.", Toast.LENGTH_SHORT).show()
    }
    private fun clearForm() {
        editingId = null
        dateField.setText(today())
        inField.setText(morningStart)
        outField.setText(afternoonEnd)
    }
    private fun loadRecords() {
        records.clear()
        val a = JSONArray(prefs.getString("records", "[]"))
        for (i in 0 until a.length()) records.add(a.getJSONObject(i))
    }
    private fun persist() {
        val a = JSONArray()
        records.forEach { a.put(it) }
        prefs.edit().putString("records", a.toString()).apply()
    }
    private fun formatMinutes(value: Int): String = "${value / 60}h ${value % 60}m"

    private fun refresh() {
        val total = records.sumOf { it.optDouble("hours") }
        progressLabel.text = "Progress: " + String.format(Locale.US, "%.1f", total) + " / " + String.format(Locale.US, "%.1f", targetHours) + " hours"
        remainingLabel.text = "Remaining: " + String.format(Locale.US, "%.1f", (targetHours - total).coerceAtLeast(0.0)) + " hours"
        scheduleLabel.text = "Work schedule: $morningStart–$morningEnd  •  $afternoonStart–$afternoonEnd"
        recordsBox.removeAllViews()
        if (records.isEmpty()) {
            recordsBox.addView(label("No attendance records yet."))
            return
        }
        records.asReversed().forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 8)
                setBackgroundColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
            }
            val late = item.optInt("lateMinutes", 0)
            val overtime = item.optInt("overtimeMinutes", 0)
            card.addView(label(item.optString("date") + "   •   " + item.optString("timeIn") + " - " + item.optString("timeOut") +
                "\\n" + String.format(Locale.US, "%.2f", item.optDouble("hours")) + " hours worked" +
                "\\nLate: " + formatMinutes(late) + "   •   Overtime: " + formatMinutes(overtime)))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            row.addView(Button(this).apply {
                text = "Edit"; isAllCaps = false
                setOnClickListener {
                    editingId = item.optString("id")
                    dateField.setText(item.optString("date"))
                    inField.setText(item.optString("timeIn"))
                    outField.setText(item.optString("timeOut"))
                    Toast.makeText(this@MainActivity, "Edit the fields above, then tap Save.", Toast.LENGTH_SHORT).show()
                }
            })
            row.addView(Button(this).apply {
                text = "Delete"; isAllCaps = false
                setOnClickListener {
                    records.removeAll { it.optString("id") == item.optString("id") }
                    persist(); refresh()
                }
            })
            card.addView(row)
            recordsBox.addView(card)
        }
    }
    private fun exportCsv() {
        if (records.isEmpty()) {
            Toast.makeText(this, "No records to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val csv = StringBuilder("Date,Time In,Time Out,Hours,Late Minutes,Overtime Minutes\\n")
        records.forEach {
            csv.append(it.optString("date")).append(",")
                .append(it.optString("timeIn")).append(",")
                .append(it.optString("timeOut")).append(",")
                .append(it.optDouble("hours")).append(",")
                .append(it.optInt("lateMinutes", 0)).append(",")
                .append(it.optInt("overtimeMinutes", 0)).append("\\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "OJT Attendance Records")
            putExtra(Intent.EXTRA_TEXT, csv.toString())
        }
        startActivity(Intent.createChooser(intent, "Share attendance CSV"))
    }
}
