from pathlib import Path


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, name: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f'pattern not found: {name} start')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f'pattern not found: {name} end')
    return text[:start] + replacement + text[end:]

# ---------------- GuideStore ----------------
store_path = Path('app/src/main/java/com/guide/app/GuideStore.kt')
store = store_path.read_text()

if 'data class AttendanceWorkSummary(' not in store:
    old_record = '''data class AttendanceRecord(
    val date: String,
    val status: String,
    val time: String = "",
    val updatedAt: Long = 0L
)
'''
    new_record = '''data class AttendanceRecord(
    val date: String,
    val status: String,
    val time: String = "",
    val updatedAt: Long = 0L,
    val workHours: Double = 0.0,
    val hourlyRate: Double = 0.0,
    val earnings: Double = 0.0
)

data class AttendanceWorkSummary(
    val workDays: Int,
    val totalHours: Double,
    val totalEarnings: Double
)
'''
    if old_record not in store:
        raise SystemExit('pattern not found: AttendanceRecord')
    store = store.replace(old_record, new_record, 1)

    attendance_block = '''    fun attendanceRecord(date: String = today()): AttendanceRecord {
        val details = attendanceDetailsObject().optJSONObject(date)
        if (details != null) {
            val hours = details.optDouble("workHours", 0.0).coerceAtLeast(0.0)
            val rate = details.optDouble("hourlyRate", 0.0).coerceAtLeast(0.0)
            val savedEarnings = details.optDouble("earnings", -1.0)
            return AttendanceRecord(
                date = date,
                status = details.optString("status", "Not marked"),
                time = details.optString("time", ""),
                updatedAt = details.optLong("updatedAt", 0L),
                workHours = hours,
                hourlyRate = rate,
                earnings = if (savedEarnings >= 0.0) savedEarnings else hours * rate
            )
        }
        val legacy = attendanceObject().optString(date, "Not marked")
        return AttendanceRecord(date = date, status = legacy)
    }

    fun attendanceDefaultWorkHours(): Double = prefs.getString("attendance_default_work_hours", "0")?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    fun attendanceDefaultHourlyRate(): Double = prefs.getString("attendance_default_hourly_rate", "0")?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    fun hasAttendanceWorkDefaults(): Boolean = attendanceDefaultWorkHours() > 0.0 && attendanceDefaultHourlyRate() > 0.0

    fun setAttendanceWorkDefaults(hours: Double, hourlyRate: Double) {
        if (hours <= 0.0 || hourlyRate <= 0.0) return
        prefs.edit()
            .putString("attendance_default_work_hours", hours.toString())
            .putString("attendance_default_hourly_rate", hourlyRate.toString())
            .apply()
    }

    fun setAttendance(
        status: String,
        date: String = today(),
        time: String = LocalTime.now().format(timeFormatter),
        workHours: Double = 0.0,
        hourlyRate: Double = 0.0
    ) {
        val legacy = attendanceObject()
        val details = attendanceDetailsObject()
        if (status == "Not marked") {
            legacy.remove(date)
            details.remove(date)
        } else {
            val resolvedHours = if (status == "Present") {
                if (workHours > 0.0) workHours else attendanceDefaultWorkHours()
            } else 0.0
            val resolvedRate = if (status == "Present") {
                if (hourlyRate > 0.0) hourlyRate else attendanceDefaultHourlyRate()
            } else 0.0
            val earnings = if (status == "Present") resolvedHours * resolvedRate else 0.0
            legacy.put(date, status)
            details.put(date, JSONObject().apply {
                put("status", status)
                put("time", time)
                put("updatedAt", System.currentTimeMillis())
                put("workHours", resolvedHours)
                put("hourlyRate", resolvedRate)
                put("earnings", earnings)
            })
        }
        prefs.edit().putString("attendance", legacy.toString()).putString("attendance_details", details.toString()).apply()
    }

    fun attendanceSummaryForCurrentMonth(): Map<String, Int> {
        val month = LocalDate.now().toString().substring(0, 7)
        val counts = linkedMapOf("Present" to 0, "Absent" to 0, "Leave" to 0)
        val dates = linkedSetOf<String>()
        val legacy = attendanceObject(); legacy.keys().forEach { dates.add(it) }
        val details = attendanceDetailsObject(); details.keys().forEach { dates.add(it) }
        dates.filter { it.startsWith(month) }.forEach { date ->
            val status = attendanceFor(date)
            if (counts.containsKey(status)) counts[status] = (counts[status] ?: 0) + 1
        }
        return counts
    }

    fun attendanceWorkSummaryForCurrentMonth(): AttendanceWorkSummary {
        val month = LocalDate.now().toString().substring(0, 7)
        val records = attendanceHistory(40).filter { it.date.startsWith(month) && it.status == "Present" }
        return AttendanceWorkSummary(
            workDays = records.size,
            totalHours = records.sumOf { it.workHours },
            totalEarnings = records.sumOf { it.earnings }
        )
    }

'''
    store = replace_between(
        store,
        '    fun attendanceRecord(date: String = today()): AttendanceRecord {',
        '    fun attendanceHistory(days: Int = 14): List<AttendanceRecord>',
        attendance_block,
        'attendance data block'
    )
    store_path.write_text(store)
    print('v3.1 GuideStore attendance wage patch applied')
else:
    print('v3.1 GuideStore patch already applied')

# ---------------- MainActivity ----------------
main_path = Path('app/src/main/java/com/guide/app/MainActivity.kt')
main = main_path.read_text()

if 'কাজের ঘণ্টা ও আয়ের হিসাব' not in main:
    attendance_page = '''    private fun attendancePage(): LinearLayout {
        val root = page()
        detailHeader(root, "হাজিরা ও কাজের হিসাব", "উপস্থিতি, কাজের ঘণ্টা এবং আয়ের হিসাব একসাথে।")

        val summary = store.attendanceSummaryForCurrentMonth()
        val work = store.attendanceWorkSummaryForCurrentMonth()
        val stats = card("#182342")
        stats.addView(text("এই মাসের সারাংশ", 12f, "#8795BB", bold = true))
        stats.addView(text("উপস্থিত ${summary["Present"] ?: 0} • অনুপস্থিত ${summary["Absent"] ?: 0} • ছুটি ${summary["Leave"] ?: 0}", 16f, "#FFFFFF", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        stats.addView(text("কাজের দিন ${work.workDays} • মোট ${String.format(Locale.US, "%.2f", work.totalHours)} ঘণ্টা", 14f, "#B9C6E8", bold = true).apply { setPadding(0, dp(8), 0, 0) })
        stats.addView(text("মোট আয় ${String.format(Locale.US, "%.2f", work.totalEarnings)} রিয়াল", 22f, "#69D0AA", bold = true).apply { setPadding(0, dp(5), 0, 0) })
        if (store.hasAttendanceWorkDefaults()) {
            stats.addView(text("ডিফল্ট: ${String.format(Locale.US, "%.2f", store.attendanceDefaultWorkHours())} ঘণ্টা × ${String.format(Locale.US, "%.2f", store.attendanceDefaultHourlyRate())} রিয়াল/ঘণ্টা", 12f, "#8795BB").apply { setPadding(0, dp(7), 0, 0) })
        }
        stats.addView(space(12))
        stats.addView(pillButton("কাজের সেটিং পরিবর্তন", "#405586") { showAttendanceWorkSettings() })
        root.addView(stats)
        root.addView(space(16))

        root.addView(sectionTitle("আজকের হাজিরা"))
        val markCard = card("#17213E")
        val today = store.attendanceRecord()
        markCard.addView(text(if (today.status == "Not marked") "এখনও নির্বাচন করা হয়নি" else "${statusBn(today.status)} • ${today.time}", 16f, "#FFFFFF", bold = true))
        markCard.addView(text(friendlyDate(store.today()), 12f, "#8694BC").apply { setPadding(0, dp(4), 0, dp(6)) })
        if (today.status == "Present" && today.workHours > 0.0) {
            markCard.addView(text("${String.format(Locale.US, "%.2f", today.workHours)} ঘণ্টা × ${String.format(Locale.US, "%.2f", today.hourlyRate)} রিয়াল = ${String.format(Locale.US, "%.2f", today.earnings)} রিয়াল", 13f, "#73D6B4", bold = true).apply { setPadding(0, 0, 0, dp(12)) })
        } else {
            markCard.addView(space(8))
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(smallAction("উপস্থিত", "#237B64") { markAttendance("Present") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        buttons.addView(hSpace(8))
        buttons.addView(smallAction("অনুপস্থিত", "#A14E58") { markAttendance("Absent") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        buttons.addView(hSpace(8))
        buttons.addView(smallAction("ছুটি", "#9B7331") { markAttendance("Leave") }, LinearLayout.LayoutParams(0, dp(44), 1f))
        markCard.addView(buttons)
        root.addView(markCard)
        root.addView(space(20))

        root.addView(sectionTitle("সেভ করা হাজিরা ও আয়"))
        val records = store.markedAttendanceHistory(365).take(120)
        if (records.isEmpty()) root.addView(emptyCard("কোনো রেকর্ড নেই", "হাজিরা সেভ করলে তারিখ, সময়, ঘণ্টা ও আয় এখানে দেখা যাবে।"))
        else records.forEachIndexed { i, record ->
            root.addView(attendanceRecordCard(record) { editAttendanceDate(record.date, record.status) })
            if (i < records.lastIndex) root.addView(space(8))
        }
        return root
    }

'''
    main = replace_between(
        main,
        '    private fun attendancePage(): LinearLayout {',
        '    private fun routineActions(item: RoutineItem)',
        attendance_page,
        'attendance page'
    )

    attendance_actions = '''    private fun editAttendanceDate(date: String, current: String) {
        val options = arrayOf("উপস্থিত • ডিফল্ট ঘণ্টা/রেট", "উপস্থিত • এই দিনের ঘণ্টা/রেট আলাদা দিন", "অনুপস্থিত", "ছুটি", "রেকর্ড মুছুন")
        AlertDialog.Builder(this).setTitle(friendlyDate(date)).setItems(options) { _, which ->
            when (which) {
                0 -> {
                    if (store.hasAttendanceWorkDefaults()) {
                        store.setAttendance("Present", date, workHours = store.attendanceDefaultWorkHours(), hourlyRate = store.attendanceDefaultHourlyRate())
                        render()
                    } else showAttendanceWorkSettings(date)
                }
                1 -> editAttendanceWorkDate(date)
                2 -> { store.setAttendance("Absent", date); render() }
                3 -> { store.setAttendance("Leave", date); render() }
                4 -> { store.setAttendance("Not marked", date); render() }
            }
        }.show()
    }

    private fun markAttendance(status: String) {
        if (status == "Present") {
            if (!store.hasAttendanceWorkDefaults()) {
                showAttendanceWorkSettings(store.today())
                return
            }
            store.setAttendance("Present", workHours = store.attendanceDefaultWorkHours(), hourlyRate = store.attendanceDefaultHourlyRate())
        } else {
            store.setAttendance(status)
        }
        val saved = store.attendanceRecord()
        val extra = if (saved.status == "Present" && saved.workHours > 0.0) " • ${String.format(Locale.US, "%.2f", saved.workHours)} ঘণ্টা • ${String.format(Locale.US, "%.2f", saved.earnings)} রিয়াল" else ""
        Toast.makeText(this, "${statusBn(saved.status)} সেভ হয়েছে$extra", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun showAttendanceWorkSettings(markDateAfterSave: String? = null) {
        val box = formBox()
        val hours = input("প্রতিদিন কত ঘণ্টা কাজ").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (store.attendanceDefaultWorkHours() > 0.0) setText(store.attendanceDefaultWorkHours().toString())
        }
        val rate = input("প্রতি ঘণ্টা কত রিয়াল").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (store.attendanceDefaultHourlyRate() > 0.0) setText(store.attendanceDefaultHourlyRate().toString())
        }
        box.addView(text("একবার সেভ করলে পরের দিন থেকে শুধু উপস্থিত চাপলেই এই ঘণ্টা ও রেট অটোমেটিক হিসাব হবে।", 12f, "#7C89AC"))
        box.addView(space(10)); box.addView(hours); box.addView(space(8)); box.addView(rate)
        val dialog = AlertDialog.Builder(this)
            .setTitle("কাজের ডিফল্ট সেটিং")
            .setView(box)
            .setPositiveButton("সেভ", null)
            .setNegativeButton("বাতিল", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val h = hours.text.toString().toDoubleOrNull()
                val r = rate.text.toString().toDoubleOrNull()
                if (h == null || h <= 0.0 || h > 24.0) {
                    hours.error = "সঠিক কাজের ঘণ্টা দিন"
                    return@setOnClickListener
                }
                if (r == null || r <= 0.0) {
                    rate.error = "সঠিক ঘণ্টাপ্রতি রিয়াল দিন"
                    return@setOnClickListener
                }
                store.setAttendanceWorkDefaults(h, r)
                if (markDateAfterSave != null) store.setAttendance("Present", markDateAfterSave, workHours = h, hourlyRate = r)
                dialog.dismiss()
                Toast.makeText(this, "কাজের সেটিং সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                render()
            }
        }
        dialog.show()
    }

    private fun editAttendanceWorkDate(date: String) {
        val record = store.attendanceRecord(date)
        val box = formBox()
        val hours = input("এই দিনে কত ঘণ্টা কাজ").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val value = if (record.workHours > 0.0) record.workHours else store.attendanceDefaultWorkHours()
            if (value > 0.0) setText(value.toString())
        }
        val rate = input("এই দিনে প্রতি ঘণ্টা কত রিয়াল").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val value = if (record.hourlyRate > 0.0) record.hourlyRate else store.attendanceDefaultHourlyRate()
            if (value > 0.0) setText(value.toString())
        }
        box.addView(hours); box.addView(space(8)); box.addView(rate)
        val dialog = AlertDialog.Builder(this).setTitle("${friendlyDate(date)} • কাজের হিসাব").setView(box).setPositiveButton("সেভ", null).setNegativeButton("বাতিল", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val h = hours.text.toString().toDoubleOrNull()
                val r = rate.text.toString().toDoubleOrNull()
                if (h == null || h <= 0.0 || h > 24.0) { hours.error = "সঠিক ঘণ্টা দিন"; return@setOnClickListener }
                if (r == null || r <= 0.0) { rate.error = "সঠিক রিয়াল দিন"; return@setOnClickListener }
                store.setAttendance("Present", date, workHours = h, hourlyRate = r)
                dialog.dismiss(); render()
            }
        }
        dialog.show()
    }

    private fun attendanceRecordCard(record: AttendanceRecord, onClick: () -> Unit): LinearLayout {
        val color = when (record.status) { "Present" -> "#2B8B70"; "Absent" -> "#A9515C"; else -> "#9B7535" }
        val c = card("#151F3A", padding = 14)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(statusBn(record.status).take(1), 16f, "#FFFFFF", bold = true).apply { gravity = Gravity.CENTER; background = rounded(color, 12) }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        labels.addView(text(statusBn(record.status), 15f, "#FFFFFF", bold = true))
        labels.addView(text("${friendlyDate(record.date)}${if (record.time.isNotBlank()) " • ${record.time}" else ""}", 12f, "#8C9AC1"))
        if (record.status == "Present" && record.workHours > 0.0) {
            labels.addView(text("${String.format(Locale.US, "%.2f", record.workHours)} ঘণ্টা × ${String.format(Locale.US, "%.2f", record.hourlyRate)} রিয়াল = ${String.format(Locale.US, "%.2f", record.earnings)} রিয়াল", 12f, "#73D6B4", bold = true).apply { setPadding(0, dp(3), 0, 0) })
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 25f, "#66759E"))
        c.addView(row); c.setOnClickListener { onClick() }; return c
    }

'''
    main = replace_between(
        main,
        '    private fun editAttendanceDate(date: String, current: String) {',
        '    private fun alarmCard(item: AlarmItem',
        attendance_actions,
        'attendance actions'
    )

    old_track = 'attendanceCard.addView(text("এই মাসে: উপস্থিত ${summary["Present"] ?: 0}  •  অনুপস্থিত ${summary["Absent"] ?: 0}  •  ছুটি ${summary["Leave"] ?: 0}", 13f, "#A2AED0").apply { setPadding(0, dp(3), 0, dp(14)) })'
    if old_track in main:
        new_track = old_track + '''\n        val monthlyWork = store.attendanceWorkSummaryForCurrentMonth()\n        attendanceCard.addView(text("কাজ: ${monthlyWork.workDays} দিন • ${String.format(Locale.US, \"%.2f\", monthlyWork.totalHours)} ঘণ্টা • ${String.format(Locale.US, \"%.2f\", monthlyWork.totalEarnings)} রিয়াল", 13f, "#73D6B4", bold = true).apply { setPadding(0, 0, 0, dp(14)) })'''
        main = main.replace(old_track, new_track, 1)
    else:
        raise SystemExit('pattern not found: track work summary')

    main_path.write_text(main)
    print('v3.1 MainActivity attendance wage patch applied')
else:
    print('v3.1 MainActivity patch already applied')

# ---------------- PDF attendance report ----------------
pdf_path = Path('app/src/main/java/com/guide/app/PdfReportsActivity.kt')
pdf = pdf_path.read_text()
if 'মোট কাজের ঘণ্টা' not in pdf:
    pdf_attendance = '''    private fun writeAttendance(w: PdfWriter, store: GuideStore) {
        val summary = store.attendanceSummaryForCurrentMonth()
        val work = store.attendanceWorkSummaryForCurrentMonth()
        w.heading("হাজিরা ও কাজের হিসাব রিপোর্ট")
        w.info("এই মাস: উপস্থিত ${summary["Present"] ?: 0} • অনুপস্থিত ${summary["Absent"] ?: 0} • ছুটি ${summary["Leave"] ?: 0}")
        w.info("কাজের দিন ${work.workDays} • মোট কাজের ঘণ্টা ${money(work.totalHours)} • মোট আয় ${money(work.totalEarnings)} রিয়াল")
        w.rule()
        val records = store.markedAttendanceHistory(365)
        if (records.isEmpty()) w.info("কোনো হাজিরা রেকর্ড নেই।")
        records.forEach { r ->
            val status = when (r.status) { "Present" -> "উপস্থিত"; "Absent" -> "অনুপস্থিত"; "Leave" -> "ছুটি"; else -> r.status }
            val workText = if (r.status == "Present" && r.workHours > 0.0) " • ${money(r.workHours)} ঘণ্টা × ${money(r.hourlyRate)} রিয়াল = ${money(r.earnings)} রিয়াল" else ""
            w.row(r.date, "$status${if (r.time.isNotBlank()) " • ${r.time}" else ""}$workText")
        }
    }

'''
    pdf = replace_between(
        pdf,
        '    private fun writeAttendance(w: PdfWriter, store: GuideStore) {',
        '    private fun writeAccounts(w: PdfWriter',
        pdf_attendance,
        'PDF attendance'
    )
    pdf_path.write_text(pdf)
    print('v3.1 PDF attendance wage patch applied')
else:
    print('v3.1 PDF patch already applied')
