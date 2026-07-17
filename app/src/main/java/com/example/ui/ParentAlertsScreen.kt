package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AttendanceRecord
import com.example.data.Holiday
import com.example.data.Student
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentAlertsScreen(
    viewModel: AttendanceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsStateWithLifecycle()

    var pendingSmsData by remember { mutableStateOf<Pair<String, String>?>(null) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Direct SMS permission granted!", Toast.LENGTH_SHORT).show()
            pendingSmsData?.let { (phone, msg) ->
                sendDirectSMSInternal(context, phone, msg)
                pendingSmsData = null
            }
        } else {
            Toast.makeText(context, "Direct SMS permission denied. Redirecting to SMS App instead.", Toast.LENGTH_LONG).show()
            pendingSmsData?.let { (phone, msg) ->
                sendSmsRedirectInternal(context, phone, msg)
                pendingSmsData = null
            }
        }
    }

    val onSendSmsDirect = remember(context) {
        { phone: String, message: String ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                sendDirectSMSInternal(context, phone, message)
            } else {
                pendingSmsData = phone to message
                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }
        }
    }

    val holidays by viewModel.holidaysInSelectedMonth.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceInSelectedMonth.collectAsStateWithLifecycle()
    
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val className by viewModel.className.collectAsStateWithLifecycle()
    val division by viewModel.division.collectAsStateWithLifecycle()

    // Threshold state (default 75%)
    var threshold by remember { mutableStateOf(75f) }
    
    // Alert communication channel selection: "WhatsApp", "Direct SMS", or "SMS App"
    var selectedChannel by remember { mutableStateOf("Direct SMS") }
    
    // Custom template state
    var templateText by remember {
        mutableStateOf(
            "Dear Parent, this is to inform you that your child {name} (Roll No {roll_no}) of Class {class} Div {division} has low attendance of {attendance}% ({present_days}/{working_days} days) in {month} {year}. Please ensure they attend classes regularly. - Class Teacher"
        )
    }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Resolve working days in month
    val ym = YearMonth.of(year, month)
    val totalDays = ym.lengthOfMonth()
    val holidayDaysCount = holidays.count {
        try {
            val d = LocalDate.parse(it.date)
            d.year == year && d.monthValue == month
        } catch (e: Exception) {
            false
        }
    }
    val workingDays = totalDays - holidayDaysCount
    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Filter students by active status in this class/division
    val activeClassStudents = remember(students, className, division) {
        students.filter {
            it.classStandard.equals(className, ignoreCase = true) &&
            it.division.equals(division, ignoreCase = true) &&
            !it.isRemoved
        }
    }

    // Map students to their attendance details and check threshold
    val studentAlertList = remember(activeClassStudents, attendanceRecords, workingDays, threshold, searchQuery) {
        val studentMap = activeClassStudents.associateBy { it.id }
        
        // Count absent days per student
        val absentMap = mutableMapOf<Int, Int>()
        attendanceRecords.forEach { record ->
            if (record.status.equals("A", ignoreCase = true)) {
                absentMap[record.studentId] = (absentMap[record.studentId] ?: 0) + 1
            }
        }

        activeClassStudents.map { student ->
            val absents = absentMap[student.id] ?: 0
            val presents = if (workingDays > 0) workingDays - absents else 0
            val percent = if (workingDays > 0) (presents.toDouble() / workingDays * 100) else 100.0
            StudentAlertInfo(
                student = student,
                absentDays = absents,
                presentDays = presents,
                attendancePercentage = percent
            )
        }.filter { info ->
            // Filter by threshold
            info.attendancePercentage < threshold &&
            // Filter by search query
            (searchQuery.isBlank() || info.student.name.contains(searchQuery, ignoreCase = true) || info.student.rollNo == searchQuery)
        }.sortedBy { it.attendancePercentage }
    }

    // Statistics counts
    val totalInClass = activeClassStudents.size
    val totalBelowThreshold = remember(activeClassStudents, attendanceRecords, workingDays, threshold) {
        val absentMap = mutableMapOf<Int, Int>()
        attendanceRecords.forEach { record ->
            if (record.status.equals("A", ignoreCase = true)) {
                absentMap[record.studentId] = (absentMap[record.studentId] ?: 0) + 1
            }
        }
        activeClassStudents.count { s ->
            val absents = absentMap[s.id] ?: 0
            val presents = if (workingDays > 0) workingDays - absents else 0
            val percent = if (workingDays > 0) (presents.toDouble() / workingDays * 100) else 100.0
            percent < threshold
        }
    }
    val missingPhonesCount = studentAlertList.count { it.student.parentPhone.isBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Parent Alert Drafts", fontWeight = FontWeight.Bold)
                        Text(
                            "Class $className - Div $division",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Configuration Card - Static/Scrollable Top Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Intro text
                Text(
                    text = "A modern tool to automate student outreach. Set an attendance threshold, review customized templates, and send SMS or WhatsApp alerts directly to parents in 1-tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Stat Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Statistics",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$monthName $year",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatWidget(
                                label = "Class Students",
                                value = totalInClass.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatWidget(
                                label = "Below Threshold",
                                value = "$totalBelowThreshold",
                                valueColor = if (totalBelowThreshold > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            StatWidget(
                                label = "Missing Phones",
                                value = "$missingPhonesCount",
                                valueColor = if (missingPhonesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Threshold Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Attendance Threshold",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${threshold.toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = threshold,
                            onValueChange = { threshold = it },
                            valueRange = 10f..100f,
                            steps = 17, // allows step size of 5% (10, 15, 20... 95, 100)
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Any student with less than ${threshold.toInt()}% attendance in this month is listed below for alert notification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Channel Selection Segmented Row
                        Text(
                            "Preferred Communication Channel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("WhatsApp", "Direct SMS", "SMS App").forEach { channel ->
                                val isSelected = selectedChannel == channel
                                val chipColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipColor)
                                        .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
                                        .clickable { selectedChannel = channel }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (channel == "SMS App") Icons.Default.Sms else Icons.Default.Send,
                                            contentDescription = null,
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = channel,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            style = if (channel.contains(" ")) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedChannel == "Direct SMS") {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Direct SMS Mode (1-Tap)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Sends text messages directly from your phone's carrier SIM instantly. Avoids manual redirection! Requires SEND_SMS permission.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Template Customization Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Custom Message Template",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(onClick = {
                                templateText = "Dear Parent, this is to inform you that your child {name} (Roll No {roll_no}) of Class {class} Div {division} has low attendance of {attendance}% ({present_days}/{working_days} days) in {month} {year}. Please ensure they attend classes regularly. - Class Teacher"
                            }) {
                                Text("Reset Default", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedTextField(
                            value = templateText,
                            onValueChange = { templateText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("Write message template here...") },
                            maxLines = 5,
                            shape = MaterialTheme.shapes.medium
                        )

                        // Insert Helper Chips Row
                        Text(
                            "Tap tags to insert into template:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tags = listOf(
                                "{name}" to "Name",
                                "{roll_no}" to "Roll No",
                                "{class}" to "Class",
                                "{division}" to "Div",
                                "{attendance}" to "Attd %",
                                "{present_days}" to "P-Days",
                                "{working_days}" to "W-Days",
                                "{month}" to "Month",
                                "{year}" to "Year"
                            )

                            tags.forEach { (placeholder, label) ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        templateText = "$templateText $placeholder"
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }

                // Filter search textfield
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Filter Students by Name or Roll No") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Text(
                    text = "Drafts for Students Below Threshold (${studentAlertList.size} found)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // LazyColumn section for performance with long list of student drafts
            if (studentAlertList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "No Alerts Needed",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            "No Student Alerts Required!",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "All class entries have attendance above the chosen ${threshold.toInt()}% threshold in $monthName.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(studentAlertList, key = { it.student.id }) { item ->
                        val student = item.student
                        val formattedPercentage = String.format("%.1f", item.attendancePercentage)
                        
                        // Live draft preview resolution
                        val liveDraftText = remember(templateText, student, formattedPercentage, item.presentDays, workingDays, monthName, year) {
                            resolveTemplate(
                                template = templateText,
                                studentName = student.name,
                                rollNo = student.rollNo,
                                classStandard = student.classStandard,
                                division = student.division,
                                attendancePercent = formattedPercentage,
                                presentDays = item.presentDays,
                                workingDays = workingDays,
                                monthName = monthName,
                                year = year
                            )
                        }

                        // Inline Phone Editing State
                        var phoneInputText by remember(student.id, student.parentPhone) { mutableStateOf(student.parentPhone) }
                        var isPhoneEdited by remember(student.id, student.parentPhone, phoneInputText) {
                            mutableStateOf(phoneInputText != student.parentPhone)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (student.parentPhone.isBlank()) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header student details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Gender visual representation
                                        val genderColor = if (student.gender.equals("Boy", ignoreCase = true)) Color(0xFF1976D2) else Color(0xFFC2185B)
                                        Surface(
                                            color = genderColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "No. ${student.rollNo}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = genderColor
                                            )
                                        }

                                        Text(
                                            text = student.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    // Attendance Rate Indicator
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "$formattedPercentage%",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${item.presentDays} Present / $workingDays Working Days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Parent Phone Row
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Parent Phone Number",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (student.parentPhone.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isPhoneEdited) {
                                            TextButton(
                                                onClick = {
                                                    val cleanedPhone = phoneInputText.filter { it.isDigit() }
                                                    viewModel.updateStudent(student.copy(parentPhone = cleanedPhone))
                                                    Toast.makeText(context, "Phone number updated for ${student.name}!", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Save Phone", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = phoneInputText,
                                        onValueChange = { phoneInputText = it },
                                        placeholder = { Text("Enter parent's 10-digit mobile number") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth(),
                                        isError = student.parentPhone.isBlank(),
                                        shape = MaterialTheme.shapes.small
                                    )
                                }

                                // Message Draft Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "Draft Alert Preview",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Text(
                                        text = liveDraftText,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp
                                    )
                                }

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Primary Chosen Channel Action Button
                                    Button(
                                        onClick = {
                                            if (phoneInputText.isBlank()) {
                                                Toast.makeText(context, "Please save parent phone number first!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                when (selectedChannel) {
                                                    "WhatsApp" -> {
                                                        sendParentAlert(
                                                            context = context,
                                                            phone = phoneInputText,
                                                            message = liveDraftText,
                                                            channel = "WhatsApp"
                                                        )
                                                    }
                                                    "Direct SMS" -> {
                                                        onSendSmsDirect(phoneInputText, liveDraftText)
                                                    }
                                                    else -> {
                                                        sendSmsRedirectInternal(context, phoneInputText, liveDraftText)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = when (selectedChannel) {
                                                "WhatsApp" -> Color(0xFF2E7D32)
                                                "Direct SMS" -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.secondary
                                            }
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (selectedChannel == "SMS App") Icons.Default.Sms else Icons.Default.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Send $selectedChannel",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    // Alternative Quick Toggle Option
                                    val altChannel = if (selectedChannel == "Direct SMS") "SMS App" else "Direct SMS"
                                    OutlinedButton(
                                        onClick = {
                                            if (phoneInputText.isBlank()) {
                                                Toast.makeText(context, "Please save parent phone number first!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (altChannel == "Direct SMS") {
                                                    onSendSmsDirect(phoneInputText, liveDraftText)
                                                } else {
                                                    sendSmsRedirectInternal(context, phoneInputText, liveDraftText)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(0.8f),
                                        colors = ButtonDefaults.outlinedButtonColors()
                                    ) {
                                        Text(
                                            text = "Via $altChannel",
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatWidget(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

// Function to resolve place-holder template values
fun resolveTemplate(
    template: String,
    studentName: String,
    rollNo: String,
    classStandard: String,
    division: String,
    attendancePercent: String,
    presentDays: Int,
    workingDays: Int,
    monthName: String,
    year: Int
): String {
    return template
        .replace("{name}", studentName)
        .replace("{roll_no}", rollNo)
        .replace("{class}", classStandard)
        .replace("{division}", division)
        .replace("{attendance}", attendancePercent)
        .replace("{present_days}", presentDays.toString())
        .replace("{working_days}", workingDays.toString())
        .replace("{month}", monthName)
        .replace("{year}", year.toString())
}

// Launches standard Intent sharing client-side safely without needing external SMS API server costs
fun sendParentAlert(
    context: Context,
    phone: String,
    message: String,
    channel: String
) {
    val cleanedPhone = phone.filter { it.isDigit() }
    if (cleanedPhone.isBlank()) {
        Toast.makeText(context, "Invalid phone number!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        if (channel.equals("WhatsApp", ignoreCase = true)) {
            // Include country code (91) default if not already present
            val formattedPhone = if (cleanedPhone.length == 10) "91$cleanedPhone" else cleanedPhone
            val uriString = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uriString)
            }
            context.startActivity(intent)
        } else {
            // SMS channel
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$cleanedPhone")
                putExtra("sms_body", message)
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open communication application: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun sendDirectSMSInternal(context: Context, phone: String, message: String) {
    val cleanedPhone = phone.filter { it.isDigit() }
    if (cleanedPhone.isBlank()) {
        Toast.makeText(context, "Invalid phone number!", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val smsManager: android.telephony.SmsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(android.telephony.SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(cleanedPhone, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(cleanedPhone, null, message, null, null)
        }
        Toast.makeText(context, "Direct SMS sent successfully to $cleanedPhone!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Direct SMS failed: ${e.localizedMessage}. Falling back to default SMS App.", Toast.LENGTH_LONG).show()
        sendSmsRedirectInternal(context, cleanedPhone, message)
    }
}

fun sendSmsRedirectInternal(context: Context, phone: String, message: String) {
    val cleanedPhone = phone.filter { it.isDigit() }
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$cleanedPhone")
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intentFallback = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:$cleanedPhone")
                putExtra("sms_body", message)
            }
            context.startActivity(intentFallback)
        } catch (ex: Exception) {
            Toast.makeText(context, "Could not open any SMS application: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

data class StudentAlertInfo(
    val student: Student,
    val absentDays: Int,
    val presentDays: Int,
    val attendancePercentage: Double
)
