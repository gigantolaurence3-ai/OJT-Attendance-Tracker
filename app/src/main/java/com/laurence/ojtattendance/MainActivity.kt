package com.laurence.ojtattendance

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
    private lateinit var recordsBox: LinearLayout
    private var editingId: String? = null

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
        root.addView(label("Offline attendance record • 1,800-hour target"))
        progressLabel = label("")
        progressLabel.textSize = 19f
        progressLabel.setTypeface(null, Typeface.BOLD)
        root.addView(progressLabel)
        remainingLabel = label("")
        root.addView(remainingLabel)

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
        TimePickerDialog(this, { _, h, m ->
            target.setText(String.format(Locale.US, "%02d:%02d", h, m))
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }
    private fun calculateHours(start: String, end: String): Double {
        return try {
            val fmt = SimpleDateFormat("HH:mm", Locale.US).apply { isLenient = false }
            val a = fmt.parse(start)!!.time
            var b = fmt.parse(end)!!.time
            if (b <= a) b += 86400000L
            ((b - a) / 3600000.0).coerceAtMost(24.0)
        } catch (_: Exception) { -1.0 }
    }
    private fun saveRecord() {
        val date = dateField.text.toString().trim()
        val tin = inField.text.toString().trim()
        val tout = outField.text.toString().trim()
        val hours = calculateHours(tin, tout)
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || hours <= 0.0) {
            Toast.makeText(this, "Please enter a valid date and time.", Toast.LENGTH_LONG).show()
            return
        }
        val id = editingId ?: System.currentTimeMillis().toString()
        val item = JSONObject().put("id", id).put("date", date).put("timeIn", tin).put("timeOut", tout).put("hours", hours)
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
        inField.setText("08:00")
        outField.setText("17:00")
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
    private fun refresh() {
        val total = records.sumOf { it.optDouble("hours") }
        progressLabel.text = "Progress: " + String.format(Locale.US, "%.1f", total) + " / 1,800 hours"
        remainingLabel.text = "Remaining: " + String.format(Locale.US, "%.1f", (1800.0 - total).coerceAtLeast(0.0)) + " hours"
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
            card.addView(label(item.optString("date") + "   •   " + item.optString("timeIn") + " - " + item.optString("timeOut") +
                "\n" + String.format(Locale.US, "%.2f", item.optDouble("hours")) + " hours"))
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
        val csv = StringBuilder("Date,Time In,Time Out,Hours\n")
        records.forEach {
            csv.append(it.optString("date")).append(",")
                .append(it.optString("timeIn")).append(",")
                .append(it.optString("timeOut")).append(",")
                .append(it.optDouble("hours")).append("\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "OJT Attendance Records")
            putExtra(Intent.EXTRA_TEXT, csv.toString())
        }
        startActivity(Intent.createChooser(intent, "Share attendance CSV"))
    }
}
