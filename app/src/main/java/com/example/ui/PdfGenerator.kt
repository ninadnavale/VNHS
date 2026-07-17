package com.example.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.AttendanceRecord
import com.example.data.Holiday
import com.example.data.Student
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore

object PdfGenerator {

    fun generateAttendancePdf(
        context: Context,
        year: Int,
        month: Int,
        students: List<Student>,
        holidays: List<Holiday>,
        attendanceRecords: List<AttendanceRecord>,
        schoolName: String = "St. Paul's Higher Secondary School",
        schoolAddress: String = "123 Education Way, School District",
        udiseNo: String = "27301202509",
        className: String = "Class 10",
        division: String = "A"
    ): File? {
        val yearMonth = YearMonth.of(year, month)
        val daysInMonth = yearMonth.lengthOfMonth()
        val monthLabel = "${yearMonth.month.name} $year"

        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = yearMonth.atEndOfMonth()
        val parsedStudents = students.filter { s ->
            val admDate = parseDateSafely(s.admissionDate)
            val remDate = parseDateSafely(s.removalDate)
            val admittedBeforeEnd = admDate == null || !admDate.isAfter(monthEnd)
            val removedAfterStart = !s.isRemoved || remDate == null || !remDate.isBefore(monthStart)
            admittedBeforeEnd && removedAfterStart
        }.sortedBy { it.rollNo.toIntOrNull() ?: 999 }

        // Set up Holiday Days Set
        val holidayDays = holidays.mapNotNull { holiday ->
            try {
                val date = LocalDate.parse(holiday.date)
                if (date.year == year && date.monthValue == month) {
                    date.dayOfMonth
                } else null
            } catch (e: Exception) {
                null
            }
        }.toSet()

        // Total working days
        val totalDays = daysInMonth
        val totalWorkingDays = totalDays - holidayDays.size

        // Student Counts
        val boysCount = parsedStudents.count { it.gender.equals("Boy", ignoreCase = true) }
        val girlsCount = parsedStudents.count { it.gender.equals("Girl", ignoreCase = true) }
        val totalStudents = parsedStudents.size

        // Create PDF document
        val pdfDocument = PdfDocument()

        // Page 1: A2 Landscape (1684 x 1190 points)
        val p1Width = 1684
        val p1Height = 1190
        val page1Info = PdfDocument.PageInfo.Builder(p1Width, p1Height, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        val canvas1 = page1.canvas

        // Paint definitions
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subTitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val pMarkerPaint = Paint().apply {
            color = Color.rgb(46, 125, 50) // Green for P
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val aMarkerPaint = Paint().apply {
            color = Color.rgb(198, 40, 40) // Red for A
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val headerBackgroundPaint = Paint().apply {
            color = Color.rgb(240, 244, 248)
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
        }
        val redHolidayPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
        }

        // Draw headers on Page 1 (Centered, without school name/address/udise as requested)
        val centerTitlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val centerSubTitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas1.drawText("STUDENT ATTENDANCE CATALOGUE", p1Width / 2f, 40f, centerTitlePaint)
        canvas1.drawText("Class: $className - Div $division  |  Month: $monthLabel", p1Width / 2f, 62f, centerSubTitlePaint)

        // Draw Meta Box
        val metaY = 82f
        canvas1.drawRect(50f, metaY, 1634f, metaY + 32f, headerBackgroundPaint)
        canvas1.drawRect(50f, metaY, 1634f, metaY + 32f, borderPaint)

        canvas1.drawText("Total Boys: $boysCount", 70f, metaY + 21f, boldTextPaint)
        canvas1.drawText("Total Girls: $girlsCount", 240f, metaY + 21f, boldTextPaint)
        canvas1.drawText("Total Students: $totalStudents", 410f, metaY + 21f, boldTextPaint)
        canvas1.drawText("Calendar Days: $totalDays", 600f, metaY + 21f, boldTextPaint)
        canvas1.drawText("Holidays: ${holidayDays.size}", 780f, metaY + 21f, boldTextPaint)
        canvas1.drawText("Total Working Days: $totalWorkingDays", 940f, metaY + 21f, boldTextPaint)

        // Draw Table Grid
        // Table layout boundaries
        val tableTop = 130f
        val startX = 50f
        val colRegWidth = 80f
        val colDobWidth = 85f
        val colCatWidth = 65f
        val colSubWidth = 65f
        val colRollWidth = 45f
        val colNameWidth = 130f
        val colDayWidth = 33f // 31 * 33 = 1023f
        val colPWidth = 50f
        val colAWidth = 50f
        val colTotalWidth = colPWidth + colAWidth

        val daysStartX = startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + colRollWidth + colNameWidth // 50 + 80 + 85 + 65 + 65 + 45 + 130 = 520
        val rightX = daysStartX + (daysInMonth * colDayWidth) // 520 + (daysInMonth * 33)
        val totalColStartX = rightX
        val endTableX = totalColStartX + colTotalWidth // 520 + daysInMonth * 33 + 100

        // Dynamic row scaling to guarantee page fits all students perfectly
        val rowHeight = when {
            parsedStudents.size <= 30 -> 24f
            parsedStudents.size <= 45 -> 18f
            parsedStudents.size <= 60 -> 14.8f // Optimized to fit 60 entries + 3 summary rows + header perfectly
            else -> 12f
        }
        val tableTextSize = when {
            parsedStudents.size <= 30 -> 11f
            parsedStudents.size <= 45 -> 10f
            parsedStudents.size <= 60 -> 8.5f
            else -> 7.5f
        }
        val tableTextPaint = Paint(textPaint).apply { textSize = tableTextSize }
        val tableBoldTextPaint = Paint(boldTextPaint).apply { textSize = tableTextSize }
        val tablePMarkerPaint = Paint(pMarkerPaint).apply { textSize = tableTextSize }
        val tableAMarkerPaint = Paint(aMarkerPaint).apply { textSize = tableTextSize }

        val textOffset = rowHeight * 0.7f

        // Header height
        val headerHeight = 30f

        // Draw Header row background
        canvas1.drawRect(startX, tableTop, endTableX, tableTop + headerHeight, headerBackgroundPaint)
        canvas1.drawRect(startX, tableTop, endTableX, tableTop + headerHeight, borderPaint)

        // Write headers text in specified sequence
        canvas1.drawText("Register No", startX + 8f, tableTop + 20f, boldTextPaint)
        canvas1.drawText("DOB", startX + colRegWidth + 8f, tableTop + 20f, boldTextPaint)
        canvas1.drawText("Category", startX + colRegWidth + colDobWidth + 8f, tableTop + 20f, boldTextPaint)
        canvas1.drawText("Caste", startX + colRegWidth + colDobWidth + colCatWidth + 8f, tableTop + 20f, boldTextPaint)
        canvas1.drawText("Roll", startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + 8f, tableTop + 20f, boldTextPaint)
        canvas1.drawText("Student Name", startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + colRollWidth + 8f, tableTop + 20f, boldTextPaint)

        for (day in 1..daysInMonth) {
            val dX = daysStartX + (day - 1) * colDayWidth
            // Center the day number text
            val textStr = day.toString()
            val textW = boldTextPaint.measureText(textStr)
            val isHoliday = holidayDays.contains(day)
            if (isHoliday) {
                // Background of holiday header light red
                val holBgPaint = Paint().apply {
                    color = Color.rgb(255, 235, 235)
                    style = Paint.Style.FILL
                }
                canvas1.drawRect(dX, tableTop, dX + colDayWidth, tableTop + headerHeight, holBgPaint)
            }
            canvas1.drawText(textStr, dX + (colDayWidth - textW) / 2f, tableTop + 20f, boldTextPaint)
        }
        val pLabelW = boldTextPaint.measureText("P")
        val aLabelW = boldTextPaint.measureText("A")
        canvas1.drawText("P", totalColStartX + (colPWidth - pLabelW) / 2f, tableTop + 20f, boldTextPaint)
        canvas1.drawText("A", totalColStartX + colPWidth + (colAWidth - aLabelW) / 2f, tableTop + 20f, boldTextPaint)
        canvas1.drawLine(totalColStartX + colPWidth, tableTop, totalColStartX + colPWidth, tableTop + headerHeight, borderPaint)

        // Draw Rows of Students
        var currentY = tableTop + headerHeight

        // Map for fast attendance queries
        // Key: studentId_day
        val attendanceMap = attendanceRecords.associateBy { "${it.studentId}_${LocalDate.parse(it.date).dayOfMonth}" }

        // Store holiday line X coordinates
        val holidayLineXs = mutableListOf<Float>()
        for (day in 1..daysInMonth) {
            if (holidayDays.contains(day)) {
                val dX = daysStartX + (day - 1) * colDayWidth + (colDayWidth / 2f)
                holidayLineXs.add(dX)
            }
        }

        // Draw Student Rows
        parsedStudents.forEachIndexed { index, student ->
            // Background zebra striping
            if (index % 2 == 1) {
                val stripePaint = Paint().apply {
                    color = Color.rgb(250, 252, 254)
                    style = Paint.Style.FILL
                }
                canvas1.drawRect(startX, currentY, endTableX, currentY + rowHeight, stripePaint)
            }
            canvas1.drawRect(startX, currentY, endTableX, currentY + rowHeight, borderPaint)

            // Draw student details in specified sequence
            canvas1.drawText(student.registerNo.ifBlank { "N/A" }, startX + 8f, currentY + textOffset, tableTextPaint)
            canvas1.drawText(student.dob.ifBlank { "N/A" }, startX + colRegWidth + 8f, currentY + textOffset, tableTextPaint)
            canvas1.drawText(student.category.ifBlank { "OPEN" }, startX + colRegWidth + colDobWidth + 8f, currentY + textOffset, tableTextPaint)
            canvas1.drawText(student.subCategory.ifBlank { "N/A" }, startX + colRegWidth + colDobWidth + colCatWidth + 8f, currentY + textOffset, tableTextPaint)
            canvas1.drawText(student.rollNo, startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + 8f, currentY + textOffset, tableTextPaint)

            // Limit name width if too long
            var displayName = student.name
            if (tableTextPaint.measureText(displayName) > colNameWidth - 10f) {
                while (tableTextPaint.measureText(displayName + "...") > colNameWidth - 10f && displayName.isNotEmpty()) {
                    displayName = displayName.dropLast(1)
                }
                displayName += "..."
            }
            canvas1.drawText(displayName, startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + colRollWidth + 8f, currentY + textOffset, tableTextPaint)

            // Calculate student present days and absent days
            var presentCount = 0
            var absentCount = 0

            for (day in 1..daysInMonth) {
                val dX = daysStartX + (day - 1) * colDayWidth
                val isHoliday = holidayDays.contains(day)

                if (isHoliday) {
                    // No marking required for holiday in the cell, we will draw the vertical red line later
                } else {
                    val dateOfRecord = LocalDate.of(year, month, day)
                    val isActive = run {
                        val admDate = parseDateSafely(student.admissionDate)
                        val remDate = parseDateSafely(student.removalDate)
                        val afterOrOnAdmission = admDate == null || !dateOfRecord.isBefore(admDate)
                        val beforeOrOnRemoval = !student.isRemoved || remDate == null || !dateOfRecord.isAfter(remDate)
                        afterOrOnAdmission && beforeOrOnRemoval
                    }

                    if (!isActive) {
                        val textW = tableTextPaint.measureText("-")
                        canvas1.drawText("-", dX + (colDayWidth - textW) / 2f, currentY + textOffset, tableTextPaint)
                    } else {
                        val record = attendanceMap["${student.id}_$day"]
                        if (record != null) {
                            if (record.status.equals("P", ignoreCase = true)) {
                                presentCount++
                                // Draw "P" green
                                val textW = tablePMarkerPaint.measureText("P")
                                canvas1.drawText("P", dX + (colDayWidth - textW) / 2f, currentY + textOffset, tablePMarkerPaint)
                            } else {
                                absentCount++
                                // Draw "A" red
                                val textW = tableAMarkerPaint.measureText("A")
                                canvas1.drawText("A", dX + (colDayWidth - textW) / 2f, currentY + textOffset, tableAMarkerPaint)
                            }
                        } else {
                            // Default to Dash or blank if not taken
                            val textW = tableTextPaint.measureText("-")
                            canvas1.drawText("-", dX + (colDayWidth - textW) / 2f, currentY + textOffset, tableTextPaint)
                        }
                    }
                }
            }

            // Draw separate Present and Absent count columns (only counts, no text/letters)
            val pStr = presentCount.toString()
            val aStr = absentCount.toString()
            canvas1.drawText(pStr, totalColStartX + (colPWidth - tableBoldTextPaint.measureText(pStr)) / 2f, currentY + textOffset, tableBoldTextPaint)
            canvas1.drawText(aStr, totalColStartX + colPWidth + (colAWidth - tableBoldTextPaint.measureText(aStr)) / 2f, currentY + textOffset, tableBoldTextPaint)
            canvas1.drawLine(totalColStartX + colPWidth, currentY, totalColStartX + colPWidth, currentY + rowHeight, borderPaint)

            currentY += rowHeight
        }

        // Draw Bottom Summary Rows: Daily Present Count, Absent Count and Total Students Count
        // 1. Row for "Present Count"
        val pRowY = currentY
        canvas1.drawRect(startX, pRowY, endTableX, pRowY + rowHeight, headerBackgroundPaint)
        canvas1.drawRect(startX, pRowY, endTableX, pRowY + rowHeight, borderPaint)
        canvas1.drawText("Total Present", startX + 8f, pRowY + textOffset, tableBoldTextPaint)

        // 2. Row for "Absent Count"
        val aRowY = pRowY + rowHeight
        canvas1.drawRect(startX, aRowY, endTableX, aRowY + rowHeight, headerBackgroundPaint)
        canvas1.drawRect(startX, aRowY, endTableX, aRowY + rowHeight, borderPaint)
        canvas1.drawText("Total Absent", startX + 8f, aRowY + textOffset, tableBoldTextPaint)

        // 3. Row for "Total Students"
        val sRowY = aRowY + rowHeight
        canvas1.drawRect(startX, sRowY, endTableX, sRowY + rowHeight, headerBackgroundPaint)
        canvas1.drawRect(startX, sRowY, endTableX, sRowY + rowHeight, borderPaint)
        canvas1.drawText("Total Students", startX + 8f, sRowY + textOffset, tableBoldTextPaint)

        // Fill counts for each day
        for (day in 1..daysInMonth) {
            val dX = daysStartX + (day - 1) * colDayWidth
            val isHoliday = holidayDays.contains(day)

            if (!isHoliday) {
                var dayPCount = 0
                var dayACount = 0
                parsedStudents.forEach { s ->
                    val dateOfRecord = LocalDate.of(year, month, day)
                    val isActive = run {
                        val admDate = parseDateSafely(s.admissionDate)
                        val remDate = parseDateSafely(s.removalDate)
                        val afterOrOnAdmission = admDate == null || !dateOfRecord.isBefore(admDate)
                        val beforeOrOnRemoval = !s.isRemoved || remDate == null || !dateOfRecord.isAfter(remDate)
                        afterOrOnAdmission && beforeOrOnRemoval
                    }
                    if (isActive) {
                        val rec = attendanceMap["${s.id}_$day"]
                        if (rec != null) {
                            if (rec.status.equals("P", ignoreCase = true)) dayPCount++
                            else if (rec.status.equals("A", ignoreCase = true)) dayACount++
                        }
                    }
                }
                // Draw present count
                val pStr = dayPCount.toString()
                val pStrW = tablePMarkerPaint.measureText(pStr)
                canvas1.drawText(pStr, dX + (colDayWidth - pStrW) / 2f, pRowY + textOffset, tablePMarkerPaint)

                // Draw absent count
                val aStr = dayACount.toString()
                val aStrW = tableAMarkerPaint.measureText(aStr)
                canvas1.drawText(aStr, dX + (colDayWidth - aStrW) / 2f, aRowY + textOffset, tableAMarkerPaint)

                // Draw total students active on this day
                val dayTotalStudents = parsedStudents.count { s ->
                    val dateOfRecord = LocalDate.of(year, month, day)
                    val admDate = parseDateSafely(s.admissionDate)
                    val remDate = parseDateSafely(s.removalDate)
                    val afterOrOnAdmission = admDate == null || !dateOfRecord.isBefore(admDate)
                    val beforeOrOnRemoval = !s.isRemoved || remDate == null || !dateOfRecord.isAfter(remDate)
                    afterOrOnAdmission && beforeOrOnRemoval
                }
                val sStr = dayTotalStudents.toString()
                val sStrW = tableBoldTextPaint.measureText(sStr)
                canvas1.drawText(sStr, dX + (colDayWidth - sStrW) / 2f, sRowY + textOffset, tableBoldTextPaint)
            }
        }

        val tableBottom = sRowY + rowHeight

        // Draw vertical column divider lines
        canvas1.drawLine(startX + colRegWidth, tableTop, startX + colRegWidth, tableBottom, borderPaint)
        canvas1.drawLine(startX + colRegWidth + colDobWidth, tableTop, startX + colRegWidth + colDobWidth, tableBottom, borderPaint)
        canvas1.drawLine(startX + colRegWidth + colDobWidth + colCatWidth, tableTop, startX + colRegWidth + colDobWidth + colCatWidth, tableBottom, borderPaint)
        canvas1.drawLine(startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth, tableTop, startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth, tableBottom, borderPaint)
        canvas1.drawLine(startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + colRollWidth, tableTop, startX + colRegWidth + colDobWidth + colCatWidth + colSubWidth + colRollWidth, tableBottom, borderPaint)
        canvas1.drawLine(daysStartX, tableTop, daysStartX, tableBottom, borderPaint)

        // Draw vertical lines for each day column
        for (day in 1..daysInMonth) {
            val dX = daysStartX + day * colDayWidth
            canvas1.drawLine(dX, tableTop, dX, tableBottom, borderPaint)
        }

        // Draw vertical lines for the summary column
        canvas1.drawLine(totalColStartX + colPWidth, tableTop, totalColStartX + colPWidth, tableBottom, borderPaint)

        // Draw red vertical lines for holidays through the entire grid (excluding headers and bottom stats, or straight through)
        // Teacher requirement: "it should look like red straight line in that day in that pdf report"
        // Let's draw it from the header top line down to the bottom of the table
        val lineBottomY = sRowY + rowHeight
        holidayLineXs.forEach { lineX ->
            canvas1.drawLine(lineX, tableTop, lineX, lineBottomY, redHolidayPaint)
        }

        // Signatures / Date Footer on Page 1 (Removed as requested)

        pdfDocument.finishPage(page1)


        // Page 2: Summary Page (A2 Landscape, separate page for summarizing counts)
        val page2Info = PdfDocument.PageInfo.Builder(p1Width, p1Height, 2).create()
        val page2 = pdfDocument.startPage(page2Info)
        val canvas2 = page2.canvas

        // --- CALCULATIONS FOR PAGE 2 TEMPLATE ---
        // Initialize counters for rows (0..9 where 9 is TOTAL)
        val firstDayBoys = IntArray(10)
        val firstDayGirls = IntArray(10)
        val firstDayTotal = IntArray(10)

        val admittedNewBoys = IntArray(10)
        val admittedNewGirls = IntArray(10)
        val admittedTransBoys = IntArray(10)
        val admittedTransGirls = IntArray(10)

        val withdrawnLeftBoys = IntArray(10)
        val withdrawnLeftGirls = IntArray(10)
        val withdrawnTransBoys = IntArray(10)
        val withdrawnTransGirls = IntArray(10)

        val lastDayBoys = IntArray(10)
        val lastDayGirls = IntArray(10)
        val lastDayTotal = IntArray(10)

        parsedStudents.forEach { s ->
            val rowIdx = getCategoryRowIndex(s)
            val isBoy = s.gender.equals("Boy", ignoreCase = true)
            
            val admDate = parseDateSafely(s.admissionDate)
            val remDate = parseDateSafely(s.removalDate)
            
            // First Day of Month state: admitted on/before start, and not removed or removed on/after start
            val activeOnFirstDay = (admDate == null || !admDate.isAfter(monthStart)) && 
                                   (!s.isRemoved || remDate == null || !remDate.isBefore(monthStart))
            
            if (activeOnFirstDay) {
                if (isBoy) firstDayBoys[rowIdx]++ else firstDayGirls[rowIdx]++
            }

            // Admissions during this month
            if (admDate != null && !admDate.isBefore(monthStart) && !admDate.isAfter(monthEnd)) {
                val isNew = s.admissionType.equals("New", ignoreCase = true)
                if (isNew) {
                    if (isBoy) admittedNewBoys[rowIdx]++ else admittedNewGirls[rowIdx]++
                } else {
                    if (isBoy) admittedTransBoys[rowIdx]++ else admittedTransGirls[rowIdx]++
                }
            }

            // Withdrawals during this month
            if (s.isRemoved && remDate != null && !remDate.isBefore(monthStart) && !remDate.isAfter(monthEnd)) {
                val isLeft = s.removalType.equals("Left", ignoreCase = true)
                if (isLeft) {
                    if (isBoy) withdrawnLeftBoys[rowIdx]++ else withdrawnLeftGirls[rowIdx]++
                } else {
                    if (isBoy) withdrawnTransBoys[rowIdx]++ else withdrawnTransGirls[rowIdx]++
                }
            }
        }

        // Compute Last Day counts using identity: Last Day = First Day + Admitted - Withdrawn
        for (i in 0..8) {
            lastDayBoys[i] = firstDayBoys[i] + admittedNewBoys[i] + admittedTransBoys[i] - withdrawnLeftBoys[i] - withdrawnTransBoys[i]
            lastDayGirls[i] = firstDayGirls[i] + admittedNewGirls[i] + admittedTransGirls[i] - withdrawnLeftGirls[i] - withdrawnTransGirls[i]
            lastDayTotal[i] = lastDayBoys[i] + lastDayGirls[i]
            firstDayTotal[i] = firstDayBoys[i] + firstDayGirls[i]
        }

        // Sum categories into the TOTAL row (index 9)
        for (i in 0..8) {
            firstDayBoys[9] += firstDayBoys[i]
            firstDayGirls[9] += firstDayGirls[i]
            firstDayTotal[9] += firstDayTotal[i]
            
            admittedNewBoys[9] += admittedNewBoys[i]
            admittedNewGirls[9] += admittedNewGirls[i]
            admittedTransBoys[9] += admittedTransBoys[i]
            admittedTransGirls[9] += admittedTransGirls[i]
            
            withdrawnLeftBoys[9] += withdrawnLeftBoys[i]
            withdrawnLeftGirls[9] += withdrawnLeftGirls[i]
            withdrawnTransBoys[9] += withdrawnTransBoys[i]
            withdrawnTransGirls[9] += withdrawnTransGirls[i]
            
            lastDayBoys[9] += lastDayBoys[i]
            lastDayGirls[9] += lastDayGirls[i]
            lastDayTotal[9] += lastDayTotal[i]
        }

        // Fetch pupil transactions for Table 3
        data class StudentTx(
            val rollNo: String,
            val regNo: String,
            val name: String,
            val date: String,
            val type: String, // "Admission" or "Withdrawal"
            val remark: String
        )
        val transactions = mutableListOf<StudentTx>()
        parsedStudents.forEach { s ->
            val admDate = parseDateSafely(s.admissionDate)
            val remDate = parseDateSafely(s.removalDate)
            
            if (admDate != null && !admDate.isBefore(monthStart) && !admDate.isAfter(monthEnd)) {
                transactions.add(
                    StudentTx(
                        rollNo = s.rollNo,
                        regNo = s.registerNo,
                        name = s.name,
                        date = s.admissionDate,
                        type = "Admission",
                        remark = s.admissionRemark.ifBlank {
                            if (s.admissionType.equals("By Transfer", ignoreCase = true)) "Admitted by transfer" else "New Admission"
                        }
                    )
                )
            }
            if (s.isRemoved && remDate != null && !remDate.isBefore(monthStart) && !remDate.isAfter(monthEnd)) {
                transactions.add(
                    StudentTx(
                        rollNo = s.rollNo,
                        regNo = s.registerNo,
                        name = s.name,
                        date = s.removalDate,
                        type = "Withdrawal",
                        remark = s.removalRemark.ifBlank { if (s.removalType.equals("By Transfer", ignoreCase = true)) "Withdrawn by transfer" else "Left School" }
                    )
                )
            }
        }
        transactions.sortBy { it.date }

        // --- DRAW LEFT HALF: PHOTO TEMPLATE REGISTER ---
        // Header
        val headerTextPaint = Paint(boldTextPaint).apply { 
            textSize = 17f 
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val headerSubPaint = Paint(textPaint).apply { 
            textSize = 10f 
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val boxTitlePaint = Paint(boldTextPaint).apply { 
            textSize = 12f 
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val regPaint = Paint(textPaint).apply { textSize = 9f }
        val boldRegPaint = Paint(boldTextPaint).apply { textSize = 9f }

        canvas2.drawText("बाहुसार शिक्षण प्रसारक मंडळ संचालित,", 421f, 38f, headerSubPaint)
        canvas2.drawText("विद्यानिकेतन हायस्कूल, सोलापूर", 421f, 62f, headerTextPaint)
        canvas2.drawText("२१/२२, साखर पेठ, सोलापूर-४१३ ००५   ph. 0217-2951090", 421f, 84f, headerSubPaint)
        canvas2.drawText("UDISE NO. 27301202509", 421f, 104f, headerSubPaint)

        // Title box
        val boxY = 130f
        canvas2.drawRect(50f, boxY, 792f, boxY + 30f, borderPaint)
        canvas2.drawText("उपस्थिती - पत्रक / MONTHLY ATTENDANCE REGISTER", 421f, boxY + 20f, boxTitlePaint)

        // Metadata rows (under title box)
        val metaTableTop = 175f
        canvas2.drawRect(50f, metaTableTop, 792f, metaTableTop + 90f, borderPaint)
        canvas2.drawLine(50f, metaTableTop + 30f, 792f, metaTableTop + 30f, borderPaint)
        canvas2.drawLine(50f, metaTableTop + 60f, 792f, metaTableTop + 60f, borderPaint)
        canvas2.drawLine(421f, metaTableTop, 421f, metaTableTop + 90f, borderPaint)

        // Calculations for Averages
        val onRollBoys = boysCount
        val onRollGirls = girlsCount
        val onRollTotal = totalStudents

        var absentBoys = 0
        var absentGirls = 0
        val studentMap = students.associateBy { it.id }

        attendanceRecords.forEach { record ->
            val s = studentMap[record.studentId]
            if (s != null && record.status.equals("A", ignoreCase = true)) {
                if (s.gender.equals("Boy", ignoreCase = true)) {
                    absentBoys++
                } else {
                    absentGirls++
                }
            }
        }
        val absentTotal = absentBoys + absentGirls

        val maxBoyDays = onRollBoys * totalWorkingDays
        val presentBoys = if (maxBoyDays > 0) maxBoyDays - absentBoys else 0
        val avgBoysPercent = if (maxBoyDays > 0) (presentBoys.toDouble() / maxBoyDays * 100) else 0.0
        val avgBoysPresentDays = if (onRollBoys > 0) (presentBoys.toDouble() / onRollBoys) else 0.0

        val maxGirlDays = onRollGirls * totalWorkingDays
        val presentGirls = if (maxGirlDays > 0) maxGirlDays - absentGirls else 0
        val avgGirlsPercent = if (maxGirlDays > 0) (presentGirls.toDouble() / maxGirlDays * 100) else 0.0
        val avgGirlsPresentDays = if (onRollGirls > 0) (presentGirls.toDouble() / onRollGirls) else 0.0

        val maxTotalDays = onRollTotal * totalWorkingDays
        val presentTotal = if (maxTotalDays > 0) maxTotalDays - absentTotal else 0
        val avgTotalPercent = if (maxTotalDays > 0) (presentTotal.toDouble() / maxTotalDays * 100) else 0.0
        val avgTotalPresentDays = if (onRollTotal > 0) (presentTotal.toDouble() / onRollTotal) else 0.0

        val avgOnRollVal = lastDayTotal[9].toDouble()
        val avgAttendanceVal = if (totalWorkingDays > 0) (lastDayTotal[9] * totalWorkingDays - absentTotal).toDouble() / totalWorkingDays else 0.0
        val avgAbsenceVal = if (totalWorkingDays > 0) absentTotal.toDouble() / totalWorkingDays else 0.0

        // Populate Metadata rows Left Column
        canvas2.drawText("इयत्ता/Std. : $className  तुकडी/Div. : $division", 60f, metaTableTop + 20f, boldRegPaint)
        canvas2.drawText("महिना व सन /Month and year: $monthLabel", 60f, metaTableTop + 50f, boldRegPaint)
        canvas2.drawText("कामाते दिवस/Working Days: $totalWorkingDays", 60f, metaTableTop + 80f, boldRegPaint)

        // Populate Metadata rows Right Column
        canvas2.drawText("सरासरी पटावर/Avarage on Roll: ${String.format("%.1f", avgOnRollVal)}", 431f, metaTableTop + 20f, boldRegPaint)
        canvas2.drawText("सरासरी हजेरी/Avarage Attendance: ${String.format("%.1f", avgAttendanceVal)}", 431f, metaTableTop + 50f, boldRegPaint)
        canvas2.drawText("सरासरी गैरहजर/Avarage Absence: ${String.format("%.1f", avgAbsenceVal)}", 431f, metaTableTop + 80f, boldRegPaint)

        // Table 1: No. of Pupils
        val table1Top = 280f
        val p2HeaderHeight = 100f
        val table1Bottom = table1Top + p2HeaderHeight + rowHeight * 10
        canvas2.drawRect(50f, table1Top, 792f, table1Top + p2HeaderHeight, headerBackgroundPaint)
        canvas2.drawRect(50f, table1Top, 792f, table1Bottom, borderPaint)

        // Draw horizontal lines for headers
        canvas2.drawLine(319f, table1Top + 25f, 663f, table1Top + 25f, borderPaint)
        canvas2.drawLine(319f, table1Top + 50f, 663f, table1Top + 50f, borderPaint)
        canvas2.drawLine(190f, table1Top + 75f, 792f, table1Top + 75f, borderPaint)

        // Draw vertical column separators
        canvas2.drawLine(190f, table1Top, 190f, table1Bottom, borderPaint)
        canvas2.drawLine(319f, table1Top, 319f, table1Bottom, borderPaint)
        canvas2.drawLine(663f, table1Top, 663f, table1Bottom, borderPaint)

        // Internal lines for Col 1-3 (First Day subdivisions)
        canvas2.drawLine(233f, table1Top + 75f, 233f, table1Bottom, borderPaint)
        canvas2.drawLine(276f, table1Top + 75f, 276f, table1Bottom, borderPaint)

        // Admitted vs Withdrawn divider (under Row 1)
        canvas2.drawLine(491f, table1Top + 25f, 491f, table1Bottom, borderPaint)

        // Admitted subdivisions (New vs By Trans, under Row 2)
        canvas2.drawLine(405f, table1Top + 50f, 405f, table1Bottom, borderPaint)

        // Withdrawn subdivisions (Left vs By Trans, under Row 2)
        canvas2.drawLine(577f, table1Top + 50f, 577f, table1Bottom, borderPaint)

        // Boy/Girl subdivision lines (under Row 3)
        canvas2.drawLine(362f, table1Top + 75f, 362f, table1Bottom, borderPaint)
        canvas2.drawLine(448f, table1Top + 75f, 448f, table1Bottom, borderPaint)
        canvas2.drawLine(534f, table1Top + 75f, 534f, table1Bottom, borderPaint)
        canvas2.drawLine(620f, table1Top + 75f, 620f, table1Bottom, borderPaint)

        // Internal lines for Col 12-14 (Last Day subdivisions, under Row 3)
        canvas2.drawLine(706f, table1Top + 75f, 706f, table1Bottom, borderPaint)
        canvas2.drawLine(749f, table1Top + 75f, 749f, table1Bottom, borderPaint)

        // Write row headers / text
        val hPaint = Paint(boldTextPaint).apply { textSize = 8.5f }
        canvas2.drawText("Description", 55f, table1Top + 30f, hPaint)
        canvas2.drawText("of Pupils", 55f, table1Top + 45f, hPaint)
        canvas2.drawText("वर्ग", 55f, table1Top + 60f, hPaint)

        // First day of Month
        val firstDayText1 = "First day of"
        val firstDayText2 = "Month"
        val f1W = hPaint.measureText(firstDayText1)
        val f2W = hPaint.measureText(firstDayText2)
        canvas2.drawText(firstDayText1, 190f + (129f - f1W) / 2f, table1Top + 32f, hPaint)
        canvas2.drawText(firstDayText2, 190f + (129f - f2W) / 2f, table1Top + 48f, hPaint)

        // No. of Pupils
        val noPupilsText = "No. of Pupils"
        val npW = hPaint.measureText(noPupilsText)
        canvas2.drawText(noPupilsText, 319f + (344f - npW) / 2f, table1Top + 17f, hPaint)

        // Last day of Month
        val lastDayText1 = "Last day of"
        val lastDayText2 = "Month"
        val l1W = hPaint.measureText(lastDayText1)
        val l2W = hPaint.measureText(lastDayText2)
        canvas2.drawText(lastDayText1, 663f + (129f - l1W) / 2f, table1Top + 32f, hPaint)
        canvas2.drawText(lastDayText2, 663f + (129f - l2W) / 2f, table1Top + 48f, hPaint)

        // Row 2: Admitted / Withdrawn
        val admText = "Admitted"
        val admW = hPaint.measureText(admText)
        canvas2.drawText(admText, 319f + (172f - admW) / 2f, table1Top + 42f, hPaint)

        val wdText = "Withdrawn"
        val wdW = hPaint.measureText(wdText)
        canvas2.drawText(wdText, 491f + (172f - wdW) / 2f, table1Top + 42f, hPaint)

        // Row 3: New / By Transfer / Left / By Transfer
        val newText = "New"
        val newW = hPaint.measureText(newText)
        canvas2.drawText(newText, 319f + (86f - newW) / 2f, table1Top + 67f, hPaint)

        val transText = "By Transfer"
        val transW = hPaint.measureText(transText)
        canvas2.drawText(transText, 405f + (86f - transW) / 2f, table1Top + 67f, hPaint)

        val leftText = "Left"
        val leftW = hPaint.measureText(leftText)
        canvas2.drawText(leftText, 491f + (86f - leftW) / 2f, table1Top + 67f, hPaint)

        canvas2.drawText(transText, 577f + (86f - transW) / 2f, table1Top + 67f, hPaint)

        // Row 4: Boys / Girls / Total
        val bW = hPaint.measureText("Boys")
        val gW = hPaint.measureText("Girls")
        val tW = hPaint.measureText("Total")

        // under First Day
        canvas2.drawText("Boys", 190f + (43f - bW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Girls", 233f + (43f - gW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Total", 276f + (43f - tW) / 2f, table1Top + 92f, hPaint)

        // under New
        canvas2.drawText("Boys", 319f + (43f - bW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Girls", 362f + (43f - gW) / 2f, table1Top + 92f, hPaint)

        // under By Trans Admitted
        canvas2.drawText("Boys", 405f + (43f - bW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Girls", 448f + (43f - gW) / 2f, table1Top + 92f, hPaint)

        // under Left
        canvas2.drawText("Boys", 491f + (43f - bW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Girls", 534f + (43f - gW) / 2f, table1Top + 92f, hPaint)

        // under By Trans Withdrawn
        canvas2.drawText("Boys", 577f + (43f - bW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Girls", 620f + (43f - gW) / 2f, table1Top + 92f, hPaint)

        // under Last Day
        canvas2.drawText("Boys", 663f + (43f - bW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Girls", 706f + (43f - gW) / 2f, table1Top + 92f, hPaint)
        canvas2.drawText("Total", 749f + (43f - tW) / 2f, table1Top + 92f, hPaint)

        // Draw Table 1 values
        var currentY2 = table1Top + p2HeaderHeight
        val rowLabels = listOf("S.C.", "S.T.", "V.J./N.T.", "S.B.C.", "O.B.C.", "OPEN", "MIN.", "BC ONCE", "PAYING", "TOTAL")

        for (i in 0..9) {
            val isTotal = i == 9
            val rowPaint = if (isTotal) boldRegPaint else regPaint
            
            canvas2.drawLine(50f, currentY2 + rowHeight, 792f, currentY2 + rowHeight, borderPaint)
            canvas2.drawText(rowLabels[i], 55f, currentY2 + 15f, rowPaint)
            
            fun drawCell(text: String, x: Float, w: Float) {
                val tw = rowPaint.measureText(text)
                canvas2.drawText(text, x + (w - tw) / 2f, currentY2 + 15f, rowPaint)
            }
            
            fun fmt(v: Int): String = if (v <= 0) "-" else v.toString()
            
            drawCell(fmt(firstDayBoys[i]), 190f, 43f)
            drawCell(fmt(firstDayGirls[i]), 233f, 43f)
            drawCell(fmt(firstDayTotal[i]), 276f, 43f)
            
            drawCell(fmt(admittedNewBoys[i]), 319f, 43f)
            drawCell(fmt(admittedNewGirls[i]), 362f, 43f)
            drawCell(fmt(admittedTransBoys[i]), 405f, 43f)
            drawCell(fmt(admittedTransGirls[i]), 448f, 43f)
            
            drawCell(fmt(withdrawnLeftBoys[i]), 491f, 43f)
            drawCell(fmt(withdrawnLeftGirls[i]), 534f, 43f)
            drawCell(fmt(withdrawnTransBoys[i]), 577f, 43f)
            drawCell(fmt(withdrawnTransGirls[i]), 620f, 43f)
            
            drawCell(fmt(lastDayBoys[i]), 663f, 43f)
            drawCell(fmt(lastDayGirls[i]), 706f, 43f)
            drawCell(fmt(lastDayTotal[i]), 749f, 43f)
            
            currentY2 += rowHeight
        }

        // Table 2: DESCRIPTION OF PUPILS
        val table2Top = currentY2 + 20f
        canvas2.drawText("विद्यार्थ्यांची वर्गवारी - DESCRIPTION OF PUPILS", 50f, table2Top - 10f, boldRegPaint)

        val col2Width = 742f / 11f
        canvas2.drawRect(50f, table2Top, 792f, table2Top + 50f, borderPaint)
        canvas2.drawRect(50f, table2Top, 792f, table2Top + 25f, headerBackgroundPaint)
        canvas2.drawLine(50f, table2Top + 25f, 792f, table2Top + 25f, borderPaint)

        for (col in 1..10) {
            val cx = 50f + col * col2Width
            canvas2.drawLine(cx, table2Top, cx, table2Top + 50f, borderPaint)
        }

        val t2Headers = listOf("EBC", "BC", "SBC", "PTC", "STC", "FREEDOM", "Soldiers", "Other", "MIN.", "Pay", "TOTAL")
        val t2HeadersLine2 = listOf("", "(SC+ST+VJ)", "", "CHILD", "", "FIGHTER", "Child", "", "Minority", "", "")

        for (col in 0..10) {
            val cx = 50f + col * col2Width
            val hw = boldRegPaint.measureText(t2Headers[col])
            canvas2.drawText(t2Headers[col], cx + (col2Width - hw) / 2f, table2Top + 10f, boldRegPaint)
            if (t2HeadersLine2[col].isNotEmpty()) {
                val hw2 = boldRegPaint.measureText(t2HeadersLine2[col])
                canvas2.drawText(t2HeadersLine2[col], cx + (col2Width - hw2) / 2f, table2Top + 20f, boldRegPaint)
            }
        }

        val ebcVal = lastDayTotal[4] + lastDayTotal[5]
        val bcVal = lastDayTotal[0] + lastDayTotal[1] + lastDayTotal[2]
        val sbcVal = lastDayTotal[3]
        val minVal = lastDayTotal[6]
        val totalVal = lastDayTotal[9]

        val t2Values = listOf(
            if (ebcVal > 0) ebcVal.toString() else "-",
            if (bcVal > 0) bcVal.toString() else "-",
            if (sbcVal > 0) sbcVal.toString() else "-",
            "-", "-", "-", "-", "-",
            if (minVal > 0) minVal.toString() else "-",
            "-",
            totalVal.toString()
        )

        for (col in 0..10) {
            val cx = 50f + col * col2Width
            val valText = t2Values[col]
            val vw = (if (col == 10) boldRegPaint else regPaint).measureText(valText)
            canvas2.drawText(valText, cx + (col2Width - vw) / 2f, table2Top + 42f, if (col == 10) boldRegPaint else regPaint)
        }

        // Table 3: Report On Pupils Withdrawn or Admitted
        val table3Top = table2Top + 85f
        canvas2.drawText("प्रवेश घेतलेल्या व शाळा सोडून गेलेल्या पाल्यासंबंधी अहवाल", 50f, table3Top - 24f, boldRegPaint)
        canvas2.drawText("Report On Pupils Withdrawn or Admitted", 50f, table3Top - 10f, boldRegPaint)

        val col3Widths = listOf(40f, 40f, 80f, 240f, 180f, 162f)
        val col3Starts = mutableListOf<Float>()
        var startX3 = 50f
        col3Widths.forEach { w ->
            col3Starts.add(startX3)
            startX3 += w
        }

        val table3Rows = 10
        val table3Height = 30f + 22f * table3Rows

        canvas2.drawRect(50f, table3Top, 792f, table3Top + table3Height, borderPaint)
        canvas2.drawRect(50f, table3Top, 792f, table3Top + 30f, headerBackgroundPaint)
        canvas2.drawLine(50f, table3Top + 30f, 792f, table3Top + 30f, borderPaint)

        col3Starts.forEachIndexed { index, sx ->
            if (index > 0) {
                canvas2.drawLine(sx, table3Top, sx, table3Top + table3Height, borderPaint)
            }
        }

        val t3Headers = listOf("SR.NO.", "ROLL", "G.R. NO.", "FULL NAME OF PUPIL", "DATE OF ADMISSION / W.D.", "REMARK")
        t3Headers.forEachIndexed { col, text ->
            val sx = col3Starts[col]
            val cw = col3Widths[col]
            val hw = boldRegPaint.measureText(text)
            canvas2.drawText(text, sx + (cw - hw) / 2f, table3Top + 18f, boldRegPaint)
        }

        var t3Y = table3Top + 30f
        for (r in 0 until table3Rows) {
            canvas2.drawLine(50f, t3Y + 22f, 792f, t3Y + 22f, borderPaint)
            
            if (r < transactions.size) {
                val tx = transactions[r]
                val srNoText = (r + 1).toString()
                val rollText = tx.rollNo
                val grNoText = tx.regNo.ifBlank { "N/A" }
                val nameText = tx.name
                val dateText = tx.date
                val remarkText = tx.remark
                
                canvas2.drawText(srNoText, col3Starts[0] + (col3Widths[0] - regPaint.measureText(srNoText)) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText(rollText, col3Starts[1] + (col3Widths[1] - regPaint.measureText(rollText)) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText(grNoText, col3Starts[2] + (col3Widths[2] - regPaint.measureText(grNoText)) / 2f, t3Y + 15f, regPaint)
                
                var dispName = nameText
                if (regPaint.measureText(dispName) > col3Widths[3] - 10f) {
                    while (regPaint.measureText(dispName + "...") > col3Widths[3] - 10f && dispName.isNotEmpty()) {
                        dispName = dispName.dropLast(1)
                    }
                    dispName += "..."
                }
                canvas2.drawText(dispName, col3Starts[3] + 8f, t3Y + 15f, regPaint)
                canvas2.drawText(dateText, col3Starts[4] + (col3Widths[4] - regPaint.measureText(dateText)) / 2f, t3Y + 15f, regPaint)
                
                var dispRemark = remarkText
                if (regPaint.measureText(dispRemark) > col3Widths[5] - 10f) {
                    while (regPaint.measureText(dispRemark + "...") > col3Widths[5] - 10f && dispRemark.isNotEmpty()) {
                        dispRemark = dispRemark.dropLast(1)
                    }
                    dispRemark += "..."
                }
                canvas2.drawText(dispRemark, col3Starts[5] + 8f, t3Y + 15f, regPaint)
            } else {
                canvas2.drawText("-", col3Starts[0] + (col3Widths[0] - regPaint.measureText("-")) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText("-", col3Starts[1] + (col3Widths[1] - regPaint.measureText("-")) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText("-", col3Starts[2] + (col3Widths[2] - regPaint.measureText("-")) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText("-", col3Starts[3] + (col3Widths[3] - regPaint.measureText("-")) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText("-", col3Starts[4] + (col3Widths[4] - regPaint.measureText("-")) / 2f, t3Y + 15f, regPaint)
                canvas2.drawText("-", col3Starts[5] + (col3Widths[5] - regPaint.measureText("-")) / 2f, t3Y + 15f, regPaint)
            }
            t3Y += 22f
        }

        // Left Half Class Teacher Signature
        canvas2.drawText("वर्गशिक्षक", 60f, 1070f, boldRegPaint)
        canvas2.drawText("Class Teacher", 60f, 1088f, boldRegPaint)

        // --- DRAW CENTRAL FOLDING GUIDE LINE ---
        val foldPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
        canvas2.drawLine(842f, 0f, 842f, p1Height.toFloat(), foldPaint)

        // --- DRAW RIGHT HALF: CONSOLIDATION METRIC TABLE ---
        val rightStartX = 892f
        val sumTableLeft = rightStartX + 30f
        val sumColMetricWidth = 262f
        val sumColGenderWidth = 150f
        val sumTableRight = sumTableLeft + sumColMetricWidth + sumColGenderWidth * 3
        val sumTableTop = 180f
        val sumRowHeight = 50f
        val sumTableBottom = sumTableTop + sumRowHeight * 4

        // Headers right half
        canvas2.drawText(schoolName, rightStartX + 20f, 45f, titlePaint)
        canvas2.drawText(schoolAddress, rightStartX + 20f, 68f, subTitlePaint)
        canvas2.drawText("End of Month Consolidation Statement | $className - Div $division | Month: $monthLabel", rightStartX + 20f, 92f, subTitlePaint)
        canvas2.drawLine(rightStartX + 20f, 115f, rightStartX + 722f, 115f, linePaint)

        val headerPaint2 = Paint().apply {
            color = Color.rgb(230, 238, 245)
            style = Paint.Style.FILL
        }
        val sumHeaderPaint = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val sumBodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val sumBodyBoldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas2.drawRect(sumTableLeft, sumTableTop, sumTableRight, sumTableTop + sumRowHeight, headerPaint2)
        canvas2.drawRect(sumTableLeft, sumTableTop, sumTableRight, sumTableBottom, borderPaint)

        for (i in 1..4) {
            val y = sumTableTop + i * sumRowHeight
            canvas2.drawLine(sumTableLeft, y, sumTableRight, y, borderPaint)
        }

        canvas2.drawLine(sumTableLeft + sumColMetricWidth, sumTableTop, sumTableLeft + sumColMetricWidth, sumTableBottom, borderPaint)
        canvas2.drawLine(sumTableLeft + sumColMetricWidth + sumColGenderWidth, sumTableTop, sumTableLeft + sumColMetricWidth + sumColGenderWidth, sumTableBottom, borderPaint)
        canvas2.drawLine(sumTableLeft + sumColMetricWidth + sumColGenderWidth * 2, sumTableTop, sumTableLeft + sumColMetricWidth + sumColGenderWidth * 2, sumTableBottom, borderPaint)

        // Draw Centered Headers
        val boysHeaderW = sumHeaderPaint.measureText("Boys")
        val girlsHeaderW = sumHeaderPaint.measureText("Girls")
        val totalHeaderW = sumHeaderPaint.measureText("Total / Combined")

        canvas2.drawText("Consolidation Metric", sumTableLeft + 15f, sumTableTop + 32f, sumHeaderPaint)
        canvas2.drawText("Boys", sumTableLeft + sumColMetricWidth + (sumColGenderWidth - boysHeaderW) / 2f, sumTableTop + 32f, sumHeaderPaint)
        canvas2.drawText("Girls", sumTableLeft + sumColMetricWidth + sumColGenderWidth + (sumColGenderWidth - girlsHeaderW) / 2f, sumTableTop + 32f, sumHeaderPaint)
        canvas2.drawText("Total / Combined", sumTableLeft + sumColMetricWidth + sumColGenderWidth * 2 + (sumColGenderWidth - totalHeaderW) / 2f, sumTableTop + 32f, sumHeaderPaint)

        // Row 1: On roll
        var rowY = sumTableTop + sumRowHeight
        val onRollBoysText = "$onRollBoys boys"
        val onRollGirlsText = "$onRollGirls girls"
        val onRollTotalText = "$onRollTotal students"

        canvas2.drawText("On Roll (Total Enrolled)", sumTableLeft + 15f, rowY + 30f, sumBodyBoldPaint)
        canvas2.drawText(onRollBoysText, sumTableLeft + sumColMetricWidth + (sumColGenderWidth - sumBodyPaint.measureText(onRollBoysText)) / 2f, rowY + 30f, sumBodyPaint)
        canvas2.drawText(onRollGirlsText, sumTableLeft + sumColMetricWidth + sumColGenderWidth + (sumColGenderWidth - sumBodyPaint.measureText(onRollGirlsText)) / 2f, rowY + 30f, sumBodyPaint)
        canvas2.drawText(onRollTotalText, sumTableLeft + sumColMetricWidth + sumColGenderWidth * 2 + (sumColGenderWidth - sumBodyBoldPaint.measureText(onRollTotalText)) / 2f, rowY + 30f, sumBodyBoldPaint)

        // Row 2: Absent Days
        rowY += sumRowHeight
        val absentBoysText = "$absentBoys student-days"
        val absentGirlsText = "$absentGirls student-days"
        val absentTotalText = "$absentTotal student-days"

        canvas2.drawText("Absent Days (Total Days Marked)", sumTableLeft + 15f, rowY + 30f, sumBodyBoldPaint)
        canvas2.drawText(absentBoysText, sumTableLeft + sumColMetricWidth + (sumColGenderWidth - sumBodyPaint.measureText(absentBoysText)) / 2f, rowY + 30f, sumBodyPaint)
        canvas2.drawText(absentGirlsText, sumTableLeft + sumColMetricWidth + sumColGenderWidth + (sumColGenderWidth - sumBodyPaint.measureText(absentGirlsText)) / 2f, rowY + 30f, sumBodyPaint)
        canvas2.drawText(absentTotalText, sumTableLeft + sumColMetricWidth + sumColGenderWidth * 2 + (sumColGenderWidth - sumBodyBoldPaint.measureText(absentTotalText)) / 2f, rowY + 30f, sumBodyBoldPaint)

        // Row 3: Average Attendance
        rowY += sumRowHeight
        val avgBoysText = String.format("%.1f%% (%.1f d / %d)", avgBoysPercent, avgBoysPresentDays, totalWorkingDays)
        val avgGirlsText = String.format("%.1f%% (%.1f d / %d)", avgGirlsPercent, avgGirlsPresentDays, totalWorkingDays)
        val avgTotalText = String.format("%.1f%% (%.1f d / %d)", avgTotalPercent, avgTotalPresentDays, totalWorkingDays)

        canvas2.drawText("Average Attendance Rate (%)", sumTableLeft + 15f, rowY + 30f, sumBodyBoldPaint)
        canvas2.drawText(avgBoysText, sumTableLeft + sumColMetricWidth + (sumColGenderWidth - sumBodyPaint.measureText(avgBoysText)) / 2f, rowY + 30f, sumBodyPaint)
        canvas2.drawText(avgGirlsText, sumTableLeft + sumColMetricWidth + sumColGenderWidth + (sumColGenderWidth - sumBodyPaint.measureText(avgGirlsText)) / 2f, rowY + 30f, sumBodyPaint)
        canvas2.drawText(avgTotalText, sumTableLeft + sumColMetricWidth + sumColGenderWidth * 2 + (sumColGenderWidth - sumBodyBoldPaint.measureText(avgTotalText)) / 2f, rowY + 30f, sumBodyBoldPaint)

        // Signatures Page 2 Right Half
        canvas2.drawText("Date: ____________________", sumTableLeft, p1Height - 80f, textPaint)
        canvas2.drawText("Class Teacher Signature", sumTableLeft, p1Height - 60f, boldTextPaint)
        canvas2.drawText("Supervisor Signature: ____________________", sumTableLeft + 250f, p1Height - 80f, textPaint)
        canvas2.drawText("Principal Signature: ____________________", sumTableRight - 220f, p1Height - 80f, textPaint)

        pdfDocument.finishPage(page2)

        val cleanMonthLabel = monthLabel.replace(" ", "_")
        val fileName = "Attendance_Report_${className}_Div${division}_${cleanMonthLabel}.pdf"

        // First, save the file to app's external files directory (always accessible and shared via FileProvider)
        val privateDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val localFile = File(privateDownloadsDir, fileName)

        try {
            if (!privateDownloadsDir.exists()) {
                privateDownloadsDir.mkdirs()
            }
            val outputStream = FileOutputStream(localFile)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
        } catch (e: IOException) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }

        return localFile
    }

    private fun parseDateSafely(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        return try {
            if (dateStr.contains("-")) {
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    if (parts[0].length == 4) {
                        // YYYY-MM-DD
                        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                    } else {
                        // DD-MM-YYYY
                        LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                    }
                } else null
            } else {
                LocalDate.parse(dateStr)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getCategoryRowIndex(student: Student): Int {
        val cat = student.category.uppercase().trim()
        val sub = student.subCategory.uppercase().trim()
        
        if (cat == "BC ONCE") return 7
        if (cat == "PAYING") return 8
        if (cat == "MINORITY" || cat == "MIN" || cat == "MIN.") return 6
        if (cat == "SC" || cat == "S.C.") return 0
        if (cat == "ST" || cat == "S.T.") return 1
        if (cat == "VJ/NT" || cat == "V.J./N.T." || cat == "VJ" || cat == "NT" || cat == "VJNT") return 2
        if (cat == "SBC" || cat == "S.B.C.") return 3
        if (cat == "OBC" || cat == "O.B.C.") return 4
        if (sub.contains("MINORITY") || sub.contains("MIN")) return 6
        return 5 // OPEN
    }
}
