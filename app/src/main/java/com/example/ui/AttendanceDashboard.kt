package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.data.AttendanceRecord
import com.example.data.Holiday
import com.example.data.Student
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class Screen {
    LOGIN,
    REGISTER,
    HOME,
    DASHBOARD,
    ENROLL,
    ADMIT,
    PARENT_ALERTS
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

private fun isStudentActiveOnDate(student: Student, date: LocalDate): Boolean {
    val admDate = parseDateSafely(student.admissionDate)
    val remDate = parseDateSafely(student.removalDate)
    
    val afterOrOnAdmission = admDate == null || !date.isBefore(admDate)
    val beforeOrOnRemoval = !student.isRemoved || remDate == null || !date.isAfter(remDate)
    
    return afterOrOnAdmission && beforeOrOnRemoval
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDashboard(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionManager = remember { TeacherSessionManager(context) }
    
    var currentScreen by remember {
        mutableStateOf(
            if (sessionManager.getLoggedInTeacher() != null) Screen.HOME else Screen.LOGIN
        )
    }
    var loggedInTeacherName by remember {
        mutableStateOf(sessionManager.getLoggedInTeacher() ?: "")
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDemoCountDialog by remember { mutableStateOf(false) }

    val students by viewModel.students.collectAsStateWithLifecycle()
    val holidays by viewModel.holidaysInSelectedMonth.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceInSelectedMonth.collectAsStateWithLifecycle()
    val datesInMonth by viewModel.datesInSelectedMonth.collectAsStateWithLifecycle()

    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()

    val schoolName by viewModel.schoolName.collectAsStateWithLifecycle()
    val className by viewModel.className.collectAsStateWithLifecycle()
    val division by viewModel.division.collectAsStateWithLifecycle()

    // Active date state for daily attendance taking (defaults to current day)
    var activeDate by remember { mutableStateOf(LocalDate.now()) }

    // Keep activeDate in sync with the selected month/year
    LaunchedEffect(year, month) {
        if (activeDate.year != year || activeDate.monthValue != month) {
            val today = LocalDate.now()
            activeDate = if (today.year == year && today.monthValue == month) {
                today
            } else {
                LocalDate.of(year, month, 1)
            }
        }
    }

    // Filter students by selected class standard and division (including students active during the selected month/year)
    val filteredStudents = remember(students, className, division, year, month) {
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = YearMonth.of(year, month).atEndOfMonth()
        students.filter { s ->
            s.classStandard.equals(className, ignoreCase = true) &&
            s.division.equals(division, ignoreCase = true) &&
            run {
                val admDate = parseDateSafely(s.admissionDate)
                val remDate = parseDateSafely(s.removalDate)
                val admittedBeforeEnd = admDate == null || !admDate.isAfter(monthEnd)
                val removedAfterStart = !s.isRemoved || remDate == null || !remDate.isBefore(monthStart)
                admittedBeforeEnd && removedAfterStart
            }
        }.sortedBy { it.rollNo.toIntOrNull() ?: 999 }
    }

    // Keep all students in class (including removed ones) for management/recovery
    val allStudentsInClass = remember(students, className, division) {
        students.filter {
            it.classStandard.equals(className, ignoreCase = true) &&
            it.division.equals(division, ignoreCase = true)
        }
    }

    var showManageStudentsDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var studentToRemoveForDetailDialog by remember { mutableStateOf<Student?>(null) }

    when (currentScreen) {
        Screen.LOGIN -> {
            LoginScreen(
                sessionManager = sessionManager,
                onLoginSuccess = { teacherName ->
                    loggedInTeacherName = teacherName
                    currentScreen = Screen.HOME
                },
                onNavigateToRegister = {
                    currentScreen = Screen.REGISTER
                }
            )
        }
        Screen.REGISTER -> {
            RegisterScreen(
                sessionManager = sessionManager,
                onRegisterSuccess = {
                    currentScreen = Screen.LOGIN
                },
                onNavigateToLogin = {
                    currentScreen = Screen.LOGIN
                }
            )
        }
        Screen.HOME -> {
            TeacherHomeScreen(
                viewModel = viewModel,
                teacherName = loggedInTeacherName,
                onLogout = {
                    sessionManager.logoutTeacher()
                    currentScreen = Screen.LOGIN
                },
                onTakeAttendance = { date, classStd, div ->
                    activeDate = date
                    // Set class and division details in ViewModel for reports and filtering
                    viewModel.className.value = classStd
                    viewModel.division.value = div
                    viewModel.selectedYear.value = date.year
                    viewModel.selectedMonth.value = date.monthValue
                    
                    currentScreen = Screen.DASHBOARD
                }
            )
        }
        Screen.DASHBOARD -> {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(320.dp),
                        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        // Header with gradient and school name
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        )
                                    )
                                )
                                .padding(horizontal = 24.dp, vertical = 32.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "School Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text(
                                    text = schoolName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Classroom Catalogue: $className - Div $division",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Navigation Items inside drawer
                        // 1st: Back to Home
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                            label = { Text("Back to Home Portal", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                currentScreen = Screen.HOME
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 2nd: Enroll New Student
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                            label = { Text("Enroll New Student", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                currentScreen = Screen.ENROLL
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 2.5th: Admit Student (Newly Admitted Student during month)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.AppRegistration, contentDescription = null) },
                            label = { Text("Admit Student (Monthly)", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                currentScreen = Screen.ADMIT
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 3rd: Remove/Recover Student
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                            label = { Text("Remove / Recover Students", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                showManageStudentsDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 4th: Attendance Catalogue (Generate PDF Report renamed to Attendance Catalogue)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            label = { Text("Attendance Catalogue", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                viewModel.generateAndSaveReport(context) { file ->
                                    if (file != null) {
                                        openPdfIntent(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to generate class PDF report.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 4.5th: Parent Alert Drafts
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Send, contentDescription = null) },
                            label = { Text("Parent Alert Drafts", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                currentScreen = Screen.PARENT_ALERTS
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 5th: Settings (Configure Ledger Info renamed to Settings)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                showConfigDialog = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = "School Logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Attendify",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                    modifier = Modifier.testTag("dashboard_menu_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Three Line Menu")
                                }
                            },
                            actions = {
                                // Populate quick demo button if current class standard / division is empty of students
                                if (filteredStudents.isEmpty()) {
                                    TextButton(
                                        onClick = {
                                            showDemoCountDialog = true
                                        },
                                        modifier = Modifier.testTag("demo_load_button")
                                    ) {
                                        Icon(Icons.Default.AutoMode, contentDescription = "Load Demo Data")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Load Demo")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Daily Register") },
                                label = { Text("Daily Register", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tab_daily_register")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.People, contentDescription = "Students") },
                                label = { Text("Students", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tab_students")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Holidays") },
                                label = { Text("Holidays", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tab_holidays")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = "Catalogue / Export") },
                                label = { Text("Catalogue", fontSize = 11.sp) },
                                modifier = Modifier.testTag("tab_ledger")
                            )
                        }
                    },
                    modifier = modifier
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Configuration Header (Month, Year, School, Class, Division editing)
                        if (selectedTab != 0 && selectedTab != 2) {
                            MonthSelectionHeader(viewModel = viewModel)
                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> DailyRegisterTab(
                                    viewModel = viewModel,
                                    students = filteredStudents,
                                    holidays = holidays,
                                    attendanceRecords = attendanceRecords,
                                    datesInMonth = datesInMonth,
                                    activeDate = activeDate,
                                    onDateSelected = { activeDate = it },
                                    teacherName = loggedInTeacherName
                                )
                                1 -> StudentsDirectoryTab(
                                    viewModel = viewModel,
                                    students = filteredStudents,
                                    onEnrollClick = { currentScreen = Screen.ENROLL }
                                )
                                2 -> HolidaysTab(
                                    viewModel = viewModel,
                                    datesInMonth = datesInMonth,
                                    holidays = holidays
                                )
                                3 -> LedgerReportTab(
                                    viewModel = viewModel,
                                    students = filteredStudents,
                                    holidays = holidays,
                                    attendanceRecords = attendanceRecords
                                )
                            }
                        }
                    }
                }
            }

            // Unified Config Dialog
            if (showConfigDialog) {
                var tempSchool by remember { mutableStateOf(schoolName) }
                val schoolAddress by viewModel.schoolAddress.collectAsStateWithLifecycle()
                var tempAddress by remember { mutableStateOf(schoolAddress) }
                val principalName by viewModel.principalName.collectAsStateWithLifecycle()
                var tempPrincipal by remember { mutableStateOf(principalName) }
                
                val classes by viewModel.classesList.collectAsStateWithLifecycle()
                val divisions by viewModel.divisionsList.collectAsStateWithLifecycle()
                
                var newClassText by remember { mutableStateOf("") }
                var newDivisionText by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showConfigDialog = false },
                    title = { Text("School Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "School Information",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            OutlinedTextField(
                                value = tempSchool,
                                onValueChange = { tempSchool = it },
                                label = { Text("School Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = tempAddress,
                                onValueChange = { tempAddress = it },
                                label = { Text("School Address") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = tempPrincipal,
                                onValueChange = { tempPrincipal = it },
                                label = { Text("Principal / In-Charge Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            
                            Text(
                                text = "Manage Classes / Standards",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Classes chips list
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                classes.forEach { cls ->
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        label = { Text(cls) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.removeClass(cls) }
                                            )
                                        }
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newClassText,
                                    onValueChange = { newClassText = it },
                                    label = { Text("Add New Class") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newClassText.isNotBlank()) {
                                            viewModel.addClass(newClassText.trim())
                                            newClassText = ""
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Class")
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            
                            Text(
                                text = "Manage Divisions / Sections",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Divisions chips list
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                divisions.forEach { div ->
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        label = { Text(div) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.removeDivision(div) }
                                            )
                                        }
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newDivisionText,
                                    onValueChange = { newDivisionText = it },
                                    label = { Text("Add New Division") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newDivisionText.isNotBlank()) {
                                            viewModel.addDivision(newDivisionText.trim())
                                            newDivisionText = ""
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Division")
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                            Text(
                                text = "Demo Data Generator",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Quickly pre-populate the selected standard/class ($className - Div $division) with detailed demo student entries (including unique names, roll numbers, DOB, categories, caste details, etc.) for testing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            var settingsDemoCountText by remember { mutableStateOf("60") }
                            var isSettingsDemoError by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = settingsDemoCountText,
                                    onValueChange = { newValue ->
                                        if (newValue.all { it.isDigit() }) {
                                            settingsDemoCountText = newValue
                                            isSettingsDemoError = newValue.toIntOrNull() == null || newValue.toInt() <= 0
                                        }
                                    },
                                    label = { Text("Demo Student Count") },
                                    isError = isSettingsDemoError,
                                    placeholder = { Text("e.g. 60") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        val count = settingsDemoCountText.toIntOrNull()
                                        if (count != null && count > 0) {
                                            viewModel.prePopulateDemoData(className, division, count)
                                            Toast.makeText(
                                                context,
                                                "Successfully generated $count demo student records for Class $className Div $division!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            isSettingsDemoError = true
                                        }
                                    },
                                    enabled = !isSettingsDemoError && settingsDemoCountText.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.AutoMode, contentDescription = "Generate")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Generate")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(10, 30, 60).forEach { count ->
                                    val isSelected = settingsDemoCountText == count.toString()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            settingsDemoCountText = count.toString()
                                            isSettingsDemoError = false
                                        },
                                        label = { Text("$count Students") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.schoolName.value = tempSchool
                                viewModel.schoolAddress.value = tempAddress
                                viewModel.principalName.value = tempPrincipal
                                showConfigDialog = false
                            }
                        ) {
                            Text("Save Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfigDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDemoCountDialog) {
                var demoCountText by remember { mutableStateOf("60") }
                var isError by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showDemoCountDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Load Demo Students",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Choose or enter the number of demo student entries to generate with full details (names, roll numbers, DOB, categories, and caste).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Choice chips for quick selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(10, 30, 60).forEach { count ->
                                    val isSelected = demoCountText == count.toString()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            demoCountText = count.toString()
                                            isError = false
                                        },
                                        label = { Text("$count Students") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = demoCountText,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() }) {
                                        demoCountText = newValue
                                        isError = newValue.toIntOrNull() == null || newValue.toInt() <= 0
                                    }
                                },
                                label = { Text("Number of Entries") },
                                isError = isError,
                                placeholder = { Text("e.g. 60") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (isError) {
                                Text(
                                    "Please enter a valid number greater than 0",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val count = demoCountText.toIntOrNull()
                                if (count != null && count > 0) {
                                    viewModel.prePopulateDemoData(className, division, count)
                                    showDemoCountDialog = false
                                    Toast.makeText(
                                        context,
                                        "Successfully generated $count demo student records!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    isError = true
                                }
                            },
                            enabled = !isError && demoCountText.isNotBlank()
                        ) {
                            Text("Generate")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDemoCountDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- DIALOG FOR STUDENT REMOVAL TRANSACTION DETAILS ---
            if (studentToRemoveForDetailDialog != null) {
                val student = studentToRemoveForDetailDialog!!
                var removalDate by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
                var removalType by remember { mutableStateOf("Left") }
                var removalRemark by remember { mutableStateOf("") }
                
                AlertDialog(
                    onDismissRequest = { studentToRemoveForDetailDialog = null },
                    title = { Text("Student Removal Details", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Please specify details for removing ${student.name} (Roll No: ${student.rollNo})", fontSize = 13.sp)
                            
                            // Removal Date
                            OutlinedTextField(
                                value = removalDate,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Removal Date") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val picker = android.app.DatePickerDialog(
                                            context,
                                            { _, yearVal, monthOfYear, dayOfMonth ->
                                                removalDate = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                            },
                                            java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue - 1, java.time.LocalDate.now().dayOfMonth
                                        )
                                        picker.show()
                                    }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val picker = android.app.DatePickerDialog(
                                        context,
                                        { _, yearVal, monthOfYear, dayOfMonth ->
                                            removalDate = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                        },
                                        java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue - 1, java.time.LocalDate.now().dayOfMonth
                                    )
                                    picker.show()
                                },
                                singleLine = true
                            )
                            
                            // Removal Type selection
                            Column {
                                Text("Removal Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SuggestionChip(
                                        onClick = { removalType = "Left" },
                                        label = { Text("Left School") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (removalType == "Left") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        ),
                                        border = if (removalType == "Left") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                    )
                                    SuggestionChip(
                                        onClick = { removalType = "By Transfer" },
                                        label = { Text("By Transfer") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (removalType == "By Transfer") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        ),
                                        border = if (removalType == "By Transfer") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                    )
                                }
                            }
                            
                            // Removal Remark
                            OutlinedTextField(
                                value = removalRemark,
                                onValueChange = { removalRemark = it },
                                label = { Text("Removal Remark / Reason") },
                                placeholder = { Text("e.g. Left Solapur, transfer certificate issued") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.removeStudent(student, removalDate, removalType, removalRemark)
                                studentToRemoveForDetailDialog = null
                                Toast.makeText(context, "Student removed successfully", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Confirm Removal", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { studentToRemoveForDetailDialog = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- DIALOG FOR MANAGING REMOVED STUDENTS (SOFT DELETE & RECOVERY) ---
            if (showManageStudentsDialog) {
                AlertDialog(
                    onDismissRequest = { showManageStudentsDialog = false },
                    title = { Text("Remove or Recover Students", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Remove student from active class tracking or recover previously removed students at any time.", style = MaterialTheme.typography.bodyMedium)

                            var showActiveTab by remember { mutableStateOf(true) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showActiveTab = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (showActiveTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = "Active (${allStudentsInClass.count { !it.isRemoved }})",
                                        color = if (showActiveTab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showActiveTab = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!showActiveTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = "Removed (${allStudentsInClass.count { it.isRemoved }})",
                                        color = if (!showActiveTab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val listToShow = if (showActiveTab) {
                                    allStudentsInClass.filter { !it.isRemoved }
                                } else {
                                    allStudentsInClass.filter { it.isRemoved }
                                }

                                if (listToShow.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No students in this list", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                } else {
                                    items(listToShow) { student ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(student.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    Text("Roll No: ${student.rollNo} • Reg: ${student.registerNo.ifBlank { "N/A" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                if (showActiveTab) {
                                                    Button(
                                                        onClick = { studentToRemoveForDetailDialog = student },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.testTag("dialog_remove_btn_${student.rollNo}")
                                                    ) {
                                                        Text("Remove", color = Color.White, fontSize = 11.sp)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { viewModel.restoreStudent(student) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.testTag("dialog_recover_btn_${student.rollNo}")
                                                    ) {
                                                        Text("Recover", color = Color.White, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showManageStudentsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // --- DIALOG FOR ENROLLING NEW STUDENT (ADD STUDENT FROM TOP MENU ONLY) ---
            if (showAddStudentDialog) {
                var name by remember { mutableStateOf("") }
                var rollNo by remember { mutableStateOf("") }
                var selectedGender by remember { mutableStateOf("Boy") }
                var registerNo by remember { mutableStateOf("") }
                var dob by remember { mutableStateOf("") }
                var category by remember { mutableStateOf("OPEN") }
                var subCategory by remember { mutableStateOf("") }
                var parentPhone by remember { mutableStateOf("") }
                var admissionDate by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
                var admissionType by remember { mutableStateOf("New") }

                val categories = listOf("SC", "ST", "VJ/NT", "SBC", "OBC", "OPEN", "Minority", "BC ONCE", "PAYING")

                // Auto-set next roll number
                LaunchedEffect(filteredStudents) {
                    val nextRoll = (filteredStudents.mapNotNull { it.rollNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
                    rollNo = nextRoll.toString()
                }

                AlertDialog(
                    onDismissRequest = { showAddStudentDialog = false },
                    title = { Text("Enroll New Student", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = rollNo,
                                    onValueChange = { rollNo = it },
                                    label = { Text("Roll No") },
                                    modifier = Modifier
                                        .weight(0.4f)
                                        .testTag("dialog_input_roll_no"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = registerNo,
                                    onValueChange = { registerNo = it },
                                    label = { Text("Register No") },
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .testTag("dialog_input_register_no"),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Student Full Name") },
                                    placeholder = { Text("John Doe") },
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .testTag("dialog_input_student_name"),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = dob,
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text("Date of Birth") },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val datePickerDialog = android.app.DatePickerDialog(
                                                context,
                                                { _, yearVal, monthOfYear, dayOfMonth ->
                                                    dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                                },
                                                2015, 0, 1
                                            )
                                            datePickerDialog.show()
                                        }) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.4f)
                                        .clickable {
                                            val datePickerDialog = android.app.DatePickerDialog(
                                                context,
                                                { _, yearVal, monthOfYear, dayOfMonth ->
                                                    dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                                },
                                                2015, 0, 1
                                            )
                                            datePickerDialog.show()
                                        }
                                        .testTag("dialog_input_student_dob"),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.5f)) {
                                    Text("Gender", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { selectedGender = "Boy" }
                                        ) {
                                            RadioButton(
                                                selected = selectedGender == "Boy",
                                                onClick = { selectedGender = "Boy" },
                                                modifier = Modifier.testTag("dialog_radio_gender_boy")
                                            )
                                            Text("Boy", fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { selectedGender = "Girl" }
                                        ) {
                                            RadioButton(
                                                selected = selectedGender == "Girl",
                                                onClick = { selectedGender = "Girl" },
                                                modifier = Modifier.testTag("dialog_radio_gender_girl")
                                            )
                                            Text("Girl", fontSize = 13.sp)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = subCategory,
                                    onValueChange = { subCategory = it },
                                    label = { Text("Caste") },
                                    placeholder = { Text("e.g. Maratha, Mali") },
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .testTag("dialog_input_sub_category"),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = admissionDate,
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text("Admission Date") },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val picker = android.app.DatePickerDialog(
                                                context,
                                                { _, yearVal, monthOfYear, dayOfMonth ->
                                                    admissionDate = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                                },
                                                java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue - 1, java.time.LocalDate.now().dayOfMonth
                                            )
                                            picker.show()
                                        }) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .clickable {
                                            val picker = android.app.DatePickerDialog(
                                                context,
                                                { _, yearVal, monthOfYear, dayOfMonth ->
                                                    admissionDate = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                                },
                                                java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue - 1, java.time.LocalDate.now().dayOfMonth
                                            )
                                            picker.show()
                                        },
                                    singleLine = true
                                )

                                Column(modifier = Modifier.weight(0.5f)) {
                                    Text("Admission Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SuggestionChip(
                                            onClick = { admissionType = "New" },
                                            label = { Text("New") },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (admissionType == "New") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            ),
                                            border = if (admissionType == "New") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                        )
                                        SuggestionChip(
                                            onClick = { admissionType = "By Transfer" },
                                            label = { Text("By Trans") },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (admissionType == "By Transfer") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            ),
                                            border = if (admissionType == "By Transfer") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = parentPhone,
                                onValueChange = { parentPhone = it },
                                label = { Text("Parent Phone Number") },
                                placeholder = { Text("e.g. 9876543210") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("dialog_input_parent_phone"),
                                singleLine = true
                            )

                            Column {
                                Text("Category", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(categories) { cat ->
                                        val isSelected = category == cat
                                        SuggestionChip(
                                            onClick = { category = cat },
                                            label = { Text(cat) },
                                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    name = ""
                                    registerNo = ""
                                    dob = ""
                                    subCategory = ""
                                    category = "OPEN"
                                    admissionDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                    admissionType = "New"
                                    val nextRoll = (filteredStudents.mapNotNull { it.rollNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
                                    rollNo = nextRoll.toString()
                                    Toast.makeText(context, "Form Cleared!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Clear")
                            }
                            Button(
                                onClick = {
                                    if (name.isBlank() || rollNo.isBlank()) {
                                        Toast.makeText(context, "Name and Roll No are required!", Toast.LENGTH_SHORT).show()
                                    } else if (filteredStudents.any { it.rollNo == rollNo }) {
                                        Toast.makeText(context, "Roll No $rollNo already exists in this class!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.addStudent(
                                            name = name.trim(),
                                            rollNo = rollNo.trim(),
                                            gender = selectedGender,
                                            classStandard = viewModel.className.value,
                                            division = viewModel.division.value,
                                            registerNo = registerNo.trim(),
                                            dob = dob,
                                            category = category,
                                            subCategory = subCategory.trim(),
                                            parentPhone = parentPhone.trim(),
                                            admissionDate = admissionDate,
                                            admissionType = admissionType
                                        )
                                        Toast.makeText(context, "Saved! Ready for next.", Toast.LENGTH_SHORT).show()
                                        val currentRollInt = rollNo.toIntOrNull() ?: 1
                                        rollNo = (currentRollInt + 1).toString()
                                        name = ""
                                        registerNo = ""
                                        dob = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Save & Next")
                            }
                            Button(
                                onClick = {
                                    if (name.isBlank() || rollNo.isBlank()) {
                                        Toast.makeText(context, "Name and Roll No are required!", Toast.LENGTH_SHORT).show()
                                    } else if (filteredStudents.any { it.rollNo == rollNo }) {
                                        Toast.makeText(context, "Roll No $rollNo already exists in this class!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.addStudent(
                                            name = name.trim(),
                                            rollNo = rollNo.trim(),
                                            gender = selectedGender,
                                            classStandard = viewModel.className.value,
                                            division = viewModel.division.value,
                                            registerNo = registerNo.trim(),
                                            dob = dob,
                                            category = category,
                                            subCategory = subCategory.trim(),
                                            parentPhone = parentPhone.trim(),
                                            admissionDate = admissionDate,
                                            admissionType = admissionType
                                        )
                                        Toast.makeText(context, "Student Enrolled Successfully!", Toast.LENGTH_SHORT).show()
                                        showAddStudentDialog = false
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddStudentDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
        Screen.ENROLL -> {
            EnrollStudentScreen(
                viewModel = viewModel,
                filteredStudents = allStudentsInClass,
                onBack = { currentScreen = Screen.DASHBOARD }
            )
        }
        Screen.ADMIT -> {
            AdmitStudentScreen(
                viewModel = viewModel,
                filteredStudents = allStudentsInClass,
                onBack = { currentScreen = Screen.DASHBOARD }
            )
        }
        Screen.PARENT_ALERTS -> {
            ParentAlertsScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.DASHBOARD }
            )
        }
    }
}

// --- SUB-COMPONENTS & TABS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectionHeader(viewModel: AttendanceViewModel) {
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val className by viewModel.className.collectAsStateWithLifecycle()
    val division by viewModel.division.collectAsStateWithLifecycle()

    // Dropdown expanding state
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val years = listOf(2025, 2026, 2027)

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Bold Class & Division at the Start of Page
            Text(
                text = "Class $className - Div $division",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("classroom_header_title")
            )

            // Month and Year selectors aligned to the right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Month selector
                Box {
                    Button(
                        onClick = { monthExpanded = true },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("month_select_button")
                    ) {
                        Text(months[month - 1], fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }
                    DropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false }
                    ) {
                        months.forEachIndexed { idx, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.selectedMonth.value = idx + 1
                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }

                // Year selector
                Box {
                    Button(
                        onClick = { yearExpanded = true },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("year_select_button")
                    ) {
                        Text(year.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }
                    DropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false }
                    ) {
                        years.forEach { y ->
                            DropdownMenuItem(
                                text = { Text(y.toString()) },
                                onClick = {
                                    viewModel.selectedYear.value = y
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- TABS DETAILED ---

@Composable
fun DailyRegisterTab(
    viewModel: AttendanceViewModel,
    students: List<Student>,
    holidays: List<Holiday>,
    attendanceRecords: List<AttendanceRecord>,
    datesInMonth: List<LocalDate>,
    activeDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    teacherName: String = ""
) {
    val context = LocalContext.current
    
    var isRegisterPanelOpen by remember { mutableStateOf(true) }
    var tempDate by remember { mutableStateOf(activeDate) }
    var showCustomDatePickerClosed by remember { mutableStateOf(false) }
    var showCustomDatePickerOpen by remember { mutableStateOf(false) }

    // Sync tempDate with activeDate if activeDate changes from outside
    LaunchedEffect(activeDate) {
        tempDate = activeDate
        isRegisterPanelOpen = true
    }

    val activeDateStr = activeDate.toString()
    val isHoliday = holidays.any { it.date == activeDateStr }
    val holidayReason = holidays.find { it.date == activeDateStr }?.reason ?: "Holiday"

    // Sub-list of attendance records for the active date
    val activeDayRecords = remember(attendanceRecords, activeDateStr) {
        attendanceRecords.filter { it.date == activeDateStr }
    }
    val recordMapByStudentId = remember(activeDayRecords) {
        activeDayRecords.associateBy { it.studentId }
    }

    // Keep draft state in sync with actual db records when active date or actual db records change
    var draftRecords by remember(activeDateStr, recordMapByStudentId) {
        mutableStateOf(recordMapByStudentId.mapValues { it.value.status })
    }

    val activeStudents = remember(students, activeDate) {
        students.filter { isStudentActiveOnDate(it, activeDate) }
    }

    val allMarked = remember(draftRecords, activeStudents) {
        activeStudents.isNotEmpty() && activeStudents.all { s ->
            val status = draftRecords[s.id] ?: ""
            status == "P" || status == "A"
        }
    }

    val className by viewModel.className.collectAsStateWithLifecycle()
    val division by viewModel.division.collectAsStateWithLifecycle()

    if (!isRegisterPanelOpen) {
        // 1. Calendar picker only selection page
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = "Daily Register",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Daily Register Portal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select the day and month to start taking class attendance for Class $className - Div $division.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Calendar Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showCustomDatePickerClosed = true
                    },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Attendance Date",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Big beautiful Calendar Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Display friendly formatted selected date
                    Text(
                        text = tempDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Tap here to select date using Calendar Picker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Open Register Action Button
            Button(
                onClick = {
                    viewModel.selectedYear.value = tempDate.year
                    viewModel.selectedMonth.value = tempDate.monthValue
                    onDateSelected(tempDate)
                    isRegisterPanelOpen = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_attendance_panel_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = "Open Register")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Attendance Register", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    } else {
        // 2. Attendance Register Panel (Once Open)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Navigation & Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            showCustomDatePickerOpen = true
                        },
                        modifier = Modifier.testTag("back_to_date_select_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Change Date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (teacherName.isNotBlank()) {
                            Text(
                                text = "Class Teacher: $teacherName",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        showCustomDatePickerOpen = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Change Date", fontSize = 11.sp)
                }
            }

            // Main display depending on Holiday vs Working day
            if (isHoliday) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick Holiday Switch Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Celebration,
                                contentDescription = "Holiday icon",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Marked as Holiday",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = isHoliday,
                            onCheckedChange = {
                                viewModel.toggleHoliday(activeDate, if (activeDate.dayOfWeek == java.time.DayOfWeek.SUNDAY) "Sunday" else "Holiday")
                            },
                            modifier = Modifier.testTag("holiday_switch_active_date")
                        )
                    }

                    HolidayWarningState(
                        dateLabel = activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                        reason = holidayReason,
                        onToggleWorkingDay = {
                            viewModel.toggleHoliday(activeDate)
                        }
                    )
                }
            } else {
                // Working day: Display Students Register with Present/Absent Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Take Attendance",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Students enrolled: ${activeStudents.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Bulk marking buttons with Auto-Save - SMALL SCALE
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val newMap = activeStudents.associate { it.id to "P" }
                                draftRecords = newMap
                                val recordsToSave = activeStudents.map { student ->
                                    AttendanceRecord(activeDateStr, student.id, "P")
                                }
                                viewModel.saveAttendance(activeDate, recordsToSave)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp).testTag("mark_all_present_btn")
                        ) {
                            Text("All P", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                val newMap = activeStudents.associate { it.id to "A" }
                                draftRecords = newMap
                                val recordsToSave = activeStudents.map { student ->
                                    AttendanceRecord(activeDateStr, student.id, "A")
                                }
                                viewModel.saveAttendance(activeDate, recordsToSave)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp).testTag("mark_all_absent_btn")
                        ) {
                            Text("All A", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Compact attendance summary at the top, but below all P & all A with a small row
                val presentCount = activeStudents.count { draftRecords[it.id] == "P" }
                val absentCount = activeStudents.count { draftRecords[it.id] == "A" }
                val markedCount = presentCount + absentCount
                val totalCount = activeStudents.size
                val progress = if (totalCount > 0) markedCount.toFloat() / totalCount else 0f

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .testTag("daily_attendance_summary_row"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (allMarked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (allMarked) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: status/completion indicator
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(
                                    imageVector = if (allMarked) Icons.Default.CheckCircle else Icons.Default.Pending,
                                    contentDescription = null,
                                    tint = if (allMarked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (allMarked) "Complete" else "Progress",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (allMarked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Middle/Right: Counts
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("P: $presentCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("A: $absentCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                Text("Total: $totalCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        
                        // Tiny linear progress bar at the bottom of the summary row
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = if (allMarked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }

                if (activeStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No students enrolled yet!\nGo to 'Students' tab to add or load demo students.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick Holiday Switch Row inside scrollable list so it scrolls as we scroll student attendance
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp)
                                     )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Holiday icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mark as Holiday?",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Switch(
                                    checked = isHoliday,
                                    onCheckedChange = {
                                        viewModel.toggleHoliday(activeDate, if (activeDate.dayOfWeek == java.time.DayOfWeek.SUNDAY) "Sunday" else "Holiday")
                                    },
                                    modifier = Modifier
                                        .scale(0.8f)
                                        .testTag("holiday_switch_active_date")
                                )
                            }
                        }

                        items(activeStudents) { student ->
                            val status = draftRecords[student.id] ?: ""

                            StudentRegisterCard(
                                student = student,
                                status = status,
                                onStatusSelected = { newStatus ->
                                    val updatedMap = draftRecords.toMutableMap().apply {
                                        put(student.id, newStatus)
                                    }
                                    draftRecords = updatedMap
                                    val recordsToSave = activeStudents.map { s ->
                                        AttendanceRecord(activeDateStr, s.id, updatedMap[s.id] ?: "")
                                    }
                                    viewModel.saveAttendance(activeDate, recordsToSave)
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }


            }
        }
    }

    if (showCustomDatePickerClosed) {
        CustomDatePickerDialog(
            initialDate = tempDate,
            onDateSelected = { tempDate = it },
            onDismissRequest = { showCustomDatePickerClosed = false }
        )
    }

    if (showCustomDatePickerOpen) {
        CustomDatePickerDialog(
            initialDate = activeDate,
            onDateSelected = { newDate ->
                viewModel.selectedYear.value = newDate.year
                viewModel.selectedMonth.value = newDate.monthValue
                onDateSelected(newDate)
            },
            onDismissRequest = { showCustomDatePickerOpen = false }
        )
    }
}

@Composable
fun HolidayWarningState(
    dateLabel: String,
    reason: String,
    onToggleWorkingDay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "Holiday",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = dateLabel,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Text(
                text = "This day is marked as a Holiday ($reason). Daily attendance register is locked.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onToggleWorkingDay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Toggle to Working Day")
            }
        }
    }
}

@Composable
fun StudentRegisterCard(
    student: Student,
    status: String,
    onStatusSelected: (String) -> Unit
) {
    val isPresent = status.equals("P", ignoreCase = true)
    val isAbsent = status.equals("A", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_register_card_${student.rollNo}"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPresent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                isAbsent -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isPresent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                isAbsent -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Elegant Circle Avatar with Roll No
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = when {
                                isPresent -> MaterialTheme.colorScheme.primary
                                isAbsent -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.rollNo,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = when {
                            isPresent || isAbsent -> Color.White
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Beautiful interactive P/A switches
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Present Button "P"
                Button(
                    onClick = { onStatusSelected("P") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPresent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isPresent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .width(52.dp)
                        .testTag("p_btn_${student.rollNo}")
                ) {
                    Text(
                        text = "P",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                // Absent Button "A"
                Button(
                    onClick = { onStatusSelected("A") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAbsent) Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isAbsent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .width(52.dp)
                        .testTag("a_btn_${student.rollNo}")
                ) {
                    Text(
                        text = "A",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GenderBadge(gender: String) {
    val isBoy = gender.equals("Boy", ignoreCase = true)
    val color = if (isBoy) Color(0xFF1976D2) else Color(0xFFE91E63)
    val bgColor = if (isBoy) Color(0xFFE3F2FD) else Color(0xFFFCE4EC)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = gender.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// --- TAB 1: STUDENTS DIRECTORY MANAGEMENT ---

@Composable
fun StudentsDirectoryTab(
    viewModel: AttendanceViewModel,
    students: List<Student>,
    onEnrollClick: () -> Unit
) {
    val context = LocalContext.current
    val boysCount = students.count { it.gender.equals("Boy", ignoreCase = true) }
    val girlsCount = students.count { it.gender.equals("Girl", ignoreCase = true) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var selectedStudentForDetails by remember { mutableStateOf<Student?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Enrolled Counts Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Enrolled", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("${students.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Boys Count", fontSize = 11.sp, color = Color(0xFF1976D2))
                        Text("$boysCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC))
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Girls Count", fontSize = 11.sp, color = Color(0xFFE91E63))
                        Text("$girlsCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE91E63))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enrolled Catalogue list",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (students.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No students enrolled!\nClick the '+' button below to enroll a new student.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(students) { student ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStudentForDetails = student }
                                .testTag("student_directory_item_${student.rollNo}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = student.rollNo,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = student.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { editingStudent = student },
                                        modifier = Modifier.testTag("edit_student_btn_${student.rollNo}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Student Details",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Beautiful floating action button for enrolling a student
        ExtendedFloatingActionButton(
            onClick = onEnrollClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_enroll_student"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Enroll") },
            text = { Text("New Enroll", fontWeight = FontWeight.Bold) }
        )
    }

    // --- DIALOG FOR EDITING STUDENT DETAILS ---
    if (editingStudent != null) {
        val studentToEdit = editingStudent!!
        var name by remember { mutableStateOf(studentToEdit.name) }
        var rollNo by remember { mutableStateOf(studentToEdit.rollNo) }
        var selectedGender by remember { mutableStateOf(studentToEdit.gender) }
        var registerNo by remember { mutableStateOf(studentToEdit.registerNo) }
        var dob by remember { mutableStateOf(studentToEdit.dob) }
        var category by remember { mutableStateOf(studentToEdit.category.ifBlank { "OPEN" }) }
        var subCategory by remember { mutableStateOf(studentToEdit.subCategory) }
        var parentPhone by remember { mutableStateOf(studentToEdit.parentPhone) }

        val categories = listOf("SC", "ST", "VJ/NT", "SBC", "OBC", "OPEN", "Minority", "BC ONCE", "PAYING")

        AlertDialog(
            onDismissRequest = { editingStudent = null },
            title = { Text("Edit Student Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Roll Number
                    OutlinedTextField(
                        value = rollNo,
                        onValueChange = { rollNo = it },
                        label = { Text("Roll Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_input_roll_no"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 2. Register Number
                    OutlinedTextField(
                        value = registerNo,
                        onValueChange = { registerNo = it },
                        label = { Text("Register Number") },
                        placeholder = { Text("e.g. REG12345") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_input_register_no"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 3. Student Full Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student Full Name") },
                        placeholder = { Text("e.g. John Doe") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_input_student_name"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 4. Date of Birth
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Date of Birth") },
                        placeholder = { Text("DD-MM-YYYY") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    2015, 0, 1
                                )
                                datePickerDialog.show()
                            }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    2015, 0, 1
                                )
                                datePickerDialog.show()
                            }
                            .testTag("edit_input_student_dob"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 5. Gender selection card style
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Gender",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("Boy", "Girl").forEach { gender ->
                                val isSelected = selectedGender == gender
                                Card(
                                    onClick = { selectedGender = gender },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = gender,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Category Selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                SuggestionChip(
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    // 7. Caste
                    OutlinedTextField(
                        value = subCategory,
                        onValueChange = { subCategory = it },
                        label = { Text("Caste") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_input_sub_category"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 8. Parent Phone Number
                    OutlinedTextField(
                        value = parentPhone,
                        onValueChange = { parentPhone = it },
                        label = { Text("Parent Phone Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_input_parent_phone"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.deleteStudent(studentToEdit)
                            Toast.makeText(context, "${studentToEdit.name} removed from active class directory.", Toast.LENGTH_LONG).show()
                            editingStudent = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_student_btn_${studentToEdit.rollNo}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Student from Class")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || rollNo.isBlank()) {
                            Toast.makeText(context, "Name and Roll No are required!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateStudent(
                                studentToEdit.copy(
                                    name = name.trim(),
                                    rollNo = rollNo.trim(),
                                    gender = selectedGender,
                                    registerNo = registerNo.trim(),
                                    dob = dob,
                                    category = category,
                                    subCategory = subCategory.trim(),
                                    parentPhone = parentPhone.trim()
                                )
                            )
                            Toast.makeText(context, "Student updated successfully!", Toast.LENGTH_SHORT).show()
                            editingStudent = null
                        }
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudent = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedStudentForDetails?.let { student ->
        AlertDialog(
            onDismissRequest = { selectedStudentForDetails = null },
            confirmButton = {
                TextButton(
                    onClick = { selectedStudentForDetails = null },
                    modifier = Modifier.testTag("close_student_details_btn")
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student.rollNo,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "Student Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row {
                            Text("Name: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(student.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row {
                            Text("Roll No: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(student.rollNo, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gender: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            GenderBadge(gender = student.gender)
                        }
                        if (student.registerNo.isNotBlank()) {
                            Row {
                                Text("Register No: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(student.registerNo, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (student.dob.isNotBlank()) {
                            Row {
                                Text("DOB: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(student.dob, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (student.category.isNotBlank() || student.subCategory.isNotBlank()) {
                            Row {
                                Text("Category: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = student.category + if (student.subCategory.isNotBlank()) " (${student.subCategory})" else "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("student_details_dialog")
        )
    }
}

// --- TAB 2: HOLIDAYS MANAGEMENT ---

@Composable
fun HolidaysTab(
    viewModel: AttendanceViewModel,
    datesInMonth: List<LocalDate>,
    holidays: List<Holiday>
) {
    if (datesInMonth.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No dates in this month")
        }
        return
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    val currentYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val currentMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    val firstOfMonth = datesInMonth.first()
    val monthName = firstOfMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
    val yearStr = firstOfMonth.year.toString()
    
    val firstDayOfWeek = firstOfMonth.dayOfWeek.value
    val firstDayOfWeekIndex = if (firstDayOfWeek == 7) 0 else firstDayOfWeek

    val weeks = remember(datesInMonth, firstDayOfWeekIndex) {
        val list = mutableListOf<List<LocalDate?>>()
        var currentWeek = mutableListOf<LocalDate?>()

        // Add empty placeholders for the first week
        for (i in 0 until firstDayOfWeekIndex) {
            currentWeek.add(null)
        }

        // Add dates
        for (date in datesInMonth) {
            currentWeek.add(date)
            if (currentWeek.size == 7) {
                list.add(currentWeek)
                currentWeek = mutableListOf()
            }
        }

        // Add empty placeholders for the last week to complete 7 cells
        if (currentWeek.isNotEmpty()) {
            while (currentWeek.size < 7) {
                currentWeek.add(null)
            }
            list.add(currentWeek)
        }
        list
    }

    val weekdaysLabels = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month and Year Title Header - Clickable to change Month/Year in Holiday Menu
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePickerDialog = true }
                .testTag("holiday_month_year_header"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Change Month/Year",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$monthName $yearStr (Tap to Change)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.0.sp
                )
            }
        }

        // Custom easy Month and Year Picker dialog
        if (showDatePickerDialog) {
            var tempMonth by remember { mutableStateOf(currentMonth) }
            var tempYear by remember { mutableStateOf(currentYear) }
            val monthsList = listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val yearsList = listOf(2025, 2026, 2027)

            AlertDialog(
                onDismissRequest = { showDatePickerDialog = false },
                title = { Text("Select Month & Year", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Year selector
                        Text("Select Year", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            yearsList.forEach { y ->
                                val isSelected = tempYear == y
                                Card(
                                    onClick = { tempYear = y },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(y.toString(), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Month selector (Grid structure: 3 columns x 4 rows)
                        Text("Select Month", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val monthIdx = row * 3 + col
                                        val isSelected = tempMonth == (monthIdx + 1)
                                        Card(
                                            onClick = { tempMonth = monthIdx + 1 },
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                Text(monthsList[monthIdx].substring(0, 3).uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.selectedMonth.value = tempMonth
                            viewModel.selectedYear.value = tempYear
                            showDatePickerDialog = false
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Text(
            text = "Tap any date to toggle Holiday/Working Day status:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Custom Standard Calendar Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row of Weekday Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekdaysLabels.forEachIndexed { index, label ->
                        val isSunday = index == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isSunday) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Grid of Weeks
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        week.forEachIndexed { colIndex, date ->
                            if (date == null) {
                                // Empty placeholder cell
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                )
                            } else {
                                val isSunday = colIndex == 0
                                val dateStr = date.toString()
                                val holidayMark = holidays.find { it.date == dateStr }
                                val isHoliday = holidayMark != null

                                val cardContainerColor = when {
                                    isHoliday -> MaterialTheme.colorScheme.errorContainer
                                    isSunday -> Color(0xFFFFEBEE).copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                }

                                val cardBorderColor = when {
                                    isHoliday -> MaterialTheme.colorScheme.error
                                    isSunday -> Color(0xFFEF5350).copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clickable {
                                            viewModel.toggleHoliday(date, if (isSunday) "Sunday" else "Festival")
                                        }
                                        .testTag("holiday_grid_day_${date.dayOfMonth}"),
                                    colors = CardDefaults.cardColors(containerColor = cardContainerColor),
                                    border = BorderStroke(
                                        width = if (isHoliday) 1.5.dp else 1.dp,
                                        color = cardBorderColor
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = date.dayOfMonth.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (isHoliday) MaterialTheme.colorScheme.error else if (isSunday) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isHoliday) {
                                                Text(
                                                    text = "HOLIDAY",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.error
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
    }
}

// --- TAB 3: MONTHLY LEDGER PREVIEW & PDF GENERATOR ---

@Composable
fun LedgerReportTab(
    viewModel: AttendanceViewModel,
    students: List<Student>,
    holidays: List<Holiday>,
    attendanceRecords: List<AttendanceRecord>
) {
    val context = LocalContext.current
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val schoolName by viewModel.schoolName.collectAsStateWithLifecycle()
    val className by viewModel.className.collectAsStateWithLifecycle()
    val division by viewModel.division.collectAsStateWithLifecycle()

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

    // Summary calculations
    val boys = students.count { it.gender.equals("Boy", ignoreCase = true) }
    val girls = students.count { it.gender.equals("Girl", ignoreCase = true) }
    val total = students.size

    // Calculate absent records
    var absentBoys = 0
    var absentGirls = 0
    val studentMap = students.associateBy { it.id }

    attendanceRecords.forEach { record ->
        val s = studentMap[record.studentId]
        if (s != null && record.status.equals("A", ignoreCase = true)) {
            if (s.gender.equals("Boy", ignoreCase = true)) absentBoys++
            else absentGirls++
        }
    }
    val absentTotal = absentBoys + absentGirls

    // Calculate Average attendance
    val totalWorkingBoyDays = boys * workingDays
    val totalWorkingGirlDays = girls * workingDays
    val totalWorkingStudentDays = total * workingDays

    val presentBoys = if (totalWorkingBoyDays > 0) totalWorkingBoyDays - absentBoys else 0
    val presentGirls = if (totalWorkingGirlDays > 0) totalWorkingGirlDays - absentGirls else 0
    val presentTotal = if (totalWorkingStudentDays > 0) totalWorkingStudentDays - absentTotal else 0

    val avgBoyRate = if (totalWorkingBoyDays > 0) (presentBoys.toDouble() / totalWorkingBoyDays * 100) else 0.0
    val avgGirlRate = if (totalWorkingGirlDays > 0) (presentGirls.toDouble() / totalWorkingGirlDays * 100) else 0.0
    val avgTotalRate = if (totalWorkingStudentDays > 0) (presentTotal.toDouble() / totalWorkingStudentDays * 100) else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Print PDF Button (moved to top above summary stats)
        Button(
            onClick = {
                viewModel.generateAndSaveReport(context) { file ->
                    if (file != null) {
                        Toast.makeText(context, "PDF Report successfully saved to Downloads directory!", Toast.LENGTH_LONG).show()
                        openPdfIntent(context, file)
                    } else {
                        Toast.makeText(context, "Error generating report. Check storage permissions.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("generate_pdf_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download catalogue", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Highly visible statistics card (works perfectly in both light & dark theme)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Consolidated Summary Stats",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Consolidation Table - strictly contains ONLY numbers in data columns
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsolidationSummaryRow(metric = "Metric", boysVal = "Boys", girlsVal = "Girls", totalVal = "Total", isHeader = true)
                    ConsolidationSummaryRow(metric = "On Roll", boysVal = boys.toString(), girlsVal = girls.toString(), totalVal = total.toString())
                    ConsolidationSummaryRow(metric = "Absent", boysVal = absentBoys.toString(), girlsVal = absentGirls.toString(), totalVal = absentTotal.toString())
                    ConsolidationSummaryRow(
                        metric = "Average",
                        boysVal = String.format("%.1f", avgBoyRate),
                        girlsVal = String.format("%.1f", avgGirlRate),
                        totalVal = String.format("%.1f", avgTotalRate),
                        highlight = true
                    )
                }
            }
        }

        val holidayDays = remember(holidays, year, month) {
            holidays.mapNotNull {
                try {
                    val d = LocalDate.parse(it.date)
                    if (d.year == year && d.monthValue == month) d.dayOfMonth else null
                } catch (e: Exception) {
                    null
                }
            }.toSet()
        }

        val recordMap = remember(attendanceRecords) {
            attendanceRecords.associateBy { "${it.studentId}_${it.date}" }
        }

        Text(
            text = "Monthly Catalogue Sheet Preview",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        val sharedScrollState = rememberScrollState()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Roll & Name
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Roll & Name", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    // Middle: Days (Scrollable)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(sharedScrollState)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Reg No", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                            Text("DOB", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                            Text("Category", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                            Text("Caste", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (day in 1..totalDays) {
                                    val isHol = holidayDays.contains(day)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                if (isHol) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isHol) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right: Present Count Header
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("P", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    // Right: Absent Count Header
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Scrollable Student Rows
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        students.forEach { student ->
                            var presentCount = 0
                            var absentCount = 0

                            for (day in 1..totalDays) {
                                if (!holidayDays.contains(day)) {
                                    val dateStr = LocalDate.of(year, month, day).toString()
                                    val rec = recordMap["${student.id}_$dateStr"]
                                    if (rec != null) {
                                        if (rec.status.equals("P", ignoreCase = true)) presentCount++
                                        else if (rec.status.equals("A", ignoreCase = true)) absentCount++
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Student Info
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .padding(start = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "${student.rollNo}. ${student.name}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Middle: Day cells (Scrollable)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(sharedScrollState)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(student.registerNo.ifBlank { "N/A" }, fontSize = 11.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                                        Text(student.dob.ifBlank { "N/A" }, fontSize = 11.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                                        Text(student.category.ifBlank { "General" }, fontSize = 11.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                                        Text(student.subCategory.ifBlank { "None" }, fontSize = 11.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            for (day in 1..totalDays) {
                                                val isHol = holidayDays.contains(day)
                                                if (isHol) {
                                                    Box(
                                                        modifier = Modifier.size(28.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("-", fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                                    }
                                                } else {
                                                    val dateStr = LocalDate.of(year, month, day).toString()
                                                    val rec = recordMap["${student.id}_$dateStr"]
                                                    val status = rec?.status ?: ""

                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .background(
                                                                color = when (status) {
                                                                    "P" -> Color(0xFFE8F5E9)
                                                                    "A" -> Color(0xFFFFEBEE)
                                                                    else -> Color.Transparent
                                                                },
                                                                shape = CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (status.isNotEmpty()) status else "-",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = when (status) {
                                                                "P" -> Color(0xFF2E7D32)
                                                                "A" -> Color(0xFFC62828)
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Right: Present count
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .padding(end = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = presentCount.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                // Right: Absent count
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .padding(end = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = absentCount.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Bottom Summary Rows Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 4.dp)
                ) {
                    // Row 1: Total Present
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("Total Present", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(sharedScrollState)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(298.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (day in 1..totalDays) {
                                        val isHol = holidayDays.contains(day)
                                        var dayP = 0
                                        if (!isHol) {
                                            val dateStr = LocalDate.of(year, month, day).toString()
                                            students.forEach { s ->
                                                val r = recordMap["${s.id}_$dateStr"]
                                                if (r?.status == "P") dayP++
                                            }
                                        }
                                        Box(
                                            modifier = Modifier.size(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isHol) "-" else dayP.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isHol) Color.LightGray else Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.width(80.dp)) // empty spacer
                    }

                    // Row 2: Total Absent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("Total Absent", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFC62828))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(sharedScrollState)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(298.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (day in 1..totalDays) {
                                        val isHol = holidayDays.contains(day)
                                        var dayA = 0
                                        if (!isHol) {
                                            val dateStr = LocalDate.of(year, month, day).toString()
                                            students.forEach { s ->
                                                val r = recordMap["${s.id}_$dateStr"]
                                                if (r?.status == "A") dayA++
                                            }
                                        }
                                        Box(
                                            modifier = Modifier.size(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isHol) "-" else dayA.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isHol) Color.LightGray else Color(0xFFC62828)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.width(80.dp)) // empty spacer
                    }

                    // Row 3: Total Students
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("Total Students", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(sharedScrollState)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(298.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (day in 1..totalDays) {
                                        val isHol = holidayDays.contains(day)
                                        var dayTotal = 0
                                        if (!isHol) {
                                            val dateStr = LocalDate.of(year, month, day).toString()
                                            students.forEach { s ->
                                                val r = recordMap["${s.id}_$dateStr"]
                                                if (r != null && (r.status == "P" || r.status == "A")) dayTotal++
                                            }
                                        }
                                        Box(
                                            modifier = Modifier.size(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isHol) "-" else dayTotal.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isHol) Color.LightGray else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.width(80.dp)) // empty spacer
                    }
                }
            }
        }
    }
}

@Composable
fun ConsolidationSummaryRow(
    metric: String,
    boysVal: String,
    girlsVal: String,
    totalVal: String,
    isHeader: Boolean = false,
    highlight: Boolean = false
) {
    val weightM = 1.4f
    val weightV = 1.0f

    val textStyle = if (isHeader || highlight) {
        MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold, 
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    } else {
        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = metric, modifier = Modifier.weight(weightM), style = textStyle)
        Text(text = boysVal, modifier = Modifier.weight(weightV), style = textStyle, textAlign = TextAlign.End)
        Text(text = girlsVal, modifier = Modifier.weight(weightV), style = textStyle, textAlign = TextAlign.End)
        Text(text = totalVal, modifier = Modifier.weight(weightV), style = textStyle, textAlign = TextAlign.End)
    }
}

// Intent logic to view generated PDF
fun openPdfIntent(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(Intent.createChooser(intent, "Open Attendance Catalogue PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Saved to Downloads folder. Install a PDF reader to view it.", Toast.LENGTH_LONG).show()
    }
}

// --- NEW COMPOSABLES FOR TEACHER LOGIN, REGISTRATION & HOME ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    sessionManager: TeacherSessionManager,
    onLoginSuccess: (String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var teacherName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "School Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Text(
                    text = "Teacher Login",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Access school attendance portals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Teacher Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User") },
                    modifier = Modifier.fillMaxWidth().testTag("login_teacher_name"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("login_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (teacherName.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        } else {
                            val success = sessionManager.loginTeacher(teacherName, password)
                            if (success) {
                                onLoginSuccess(sessionManager.getLoggedInTeacher() ?: teacherName)
                            } else {
                                Toast.makeText(context, "Invalid name or password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        sessionManager.registerTeacher("Demo Teacher", "password")
                        val success = sessionManager.loginTeacher("Demo Teacher", "password")
                        if (success) {
                            onLoginSuccess(sessionManager.getLoggedInTeacher() ?: "Demo Teacher")
                        } else {
                            Toast.makeText(context, "Demo Login Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("demo_login_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.AutoMode, contentDescription = "Demo")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Demo Login (Quick Access)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onNavigateToRegister,
                    modifier = Modifier.testTag("go_to_register_button")
                ) {
                    Text("New teacher? Register here")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    sessionManager: TeacherSessionManager,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var teacherName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AppRegistration,
                    contentDescription = "Register Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Text(
                    text = "Teacher Registration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Create an account to manage classes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Teacher Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User") },
                    modifier = Modifier.fillMaxWidth().testTag("register_teacher_name"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("register_password"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm Lock") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("register_confirm_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (teacherName.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        } else if (password != confirmPassword) {
                            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                        } else {
                            val success = sessionManager.registerTeacher(teacherName, password)
                            if (success) {
                                Toast.makeText(context, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                                onRegisterSuccess()
                            } else {
                                Toast.makeText(context, "Teacher name already exists", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("register_submit_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Register", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.testTag("go_to_login_button")
                ) {
                    Text("Already registered? Login here")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    viewModel: AttendanceViewModel,
    teacherName: String,
    onLogout: () -> Unit,
    onTakeAttendance: (LocalDate, String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    
    val classes by viewModel.classesList.collectAsStateWithLifecycle()
    val divisions by viewModel.divisionsList.collectAsStateWithLifecycle()
    
    var selectedClass by remember(classes) { mutableStateOf(classes.firstOrNull() ?: "5th") }
    var selectedDivision by remember(divisions) { mutableStateOf(divisions.firstOrNull() ?: "A") }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "User avatar",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = teacherName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Date Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. Select Attendance Date",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCustomDatePicker = true
                            }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Calendar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = selectedDate.format(dateFormatter),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Class Standard Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "2. Select Class / Standard",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(classes) { item ->
                            val isSelected = selectedClass == item
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { selectedClass = item }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                                    .testTag("class_pill_$item"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Division Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "3. Select Division",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        divisions.forEach { item ->
                            val isSelected = selectedDivision == item
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { selectedDivision = item }
                                    .padding(vertical = 14.dp)
                                    .testTag("division_pill_$item"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Div $item",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Take Attendance Primary Action Button
            Button(
                onClick = {
                    onTakeAttendance(selectedDate, selectedClass, selectedDivision)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("take_attendance_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = "Checklist")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Take Attendance",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showCustomDatePicker) {
        CustomDatePickerDialog(
            initialDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismissRequest = { showCustomDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollStudentScreen(
    viewModel: AttendanceViewModel,
    filteredStudents: List<Student>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var rollNo by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Boy") }
    var registerNo by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OPEN") }
    var subCategory by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }

    val categories = listOf("SC", "ST", "VJ/NT", "SBC", "OBC", "OPEN", "Minority", "BC ONCE", "PAYING")

    // Auto-set next roll number on load
    LaunchedEffect(filteredStudents) {
        val nextRoll = (filteredStudents.mapNotNull { it.rollNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
        rollNo = nextRoll.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enroll New Student", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val classNameState by viewModel.className.collectAsStateWithLifecycle()
            val divisionState by viewModel.division.collectAsStateWithLifecycle()

            Text(
                text = "Enter student details to add them to Class $classNameState - Div $divisionState",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Roll Number
                    OutlinedTextField(
                        value = rollNo,
                        onValueChange = { rollNo = it },
                        label = { Text("Roll Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enroll_input_roll_no"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 2. Register Number
                    OutlinedTextField(
                        value = registerNo,
                        onValueChange = { registerNo = it },
                        label = { Text("Register Number") },
                        placeholder = { Text("e.g. REG12345") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enroll_input_register_no"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 3. Student Full Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student Full Name") },
                        placeholder = { Text("e.g. John Doe") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enroll_input_student_name"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 4. Date of Birth
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Date of Birth") },
                        placeholder = { Text("DD-MM-YYYY") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    2015, 0, 1
                                )
                                datePickerDialog.show()
                            }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    2015, 0, 1
                                )
                                datePickerDialog.show()
                            }
                            .testTag("enroll_input_student_dob"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 5. Gender
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Gender",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf("Boy", "Girl").forEach { gender ->
                                val isSelected = selectedGender == gender
                                Card(
                                    onClick = { selectedGender = gender },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = gender,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Category
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                SuggestionChip(
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    // 7. Caste
                    OutlinedTextField(
                        value = subCategory,
                        onValueChange = { subCategory = it },
                        label = { Text("Caste") },
                        placeholder = { Text("e.g. Maratha, Mali (Optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enroll_input_sub_category"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // 8. Parent Phone Number
                    OutlinedTextField(
                        value = parentPhone,
                        onValueChange = { parentPhone = it },
                        label = { Text("Parent Phone Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enroll_input_parent_phone"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (name.isBlank() || rollNo.isBlank()) {
                            Toast.makeText(context, "Name and Roll Number are required!", Toast.LENGTH_SHORT).show()
                        } else if (filteredStudents.any { it.rollNo == rollNo }) {
                            Toast.makeText(context, "Roll Number $rollNo already exists in this class!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addStudent(
                                name = name.trim(),
                                rollNo = rollNo.trim(),
                                gender = selectedGender,
                                classStandard = viewModel.className.value,
                                division = viewModel.division.value,
                                registerNo = registerNo.trim(),
                                dob = dob,
                                category = category,
                                subCategory = subCategory.trim(),
                                parentPhone = parentPhone.trim()
                            )
                            Toast.makeText(context, "Student Enrolled Successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Exit", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (name.isBlank() || rollNo.isBlank()) {
                            Toast.makeText(context, "Name and Roll Number are required!", Toast.LENGTH_SHORT).show()
                        } else if (filteredStudents.any { it.rollNo == rollNo }) {
                            Toast.makeText(context, "Roll Number $rollNo already exists in this class!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addStudent(
                                name = name.trim(),
                                rollNo = rollNo.trim(),
                                gender = selectedGender,
                                classStandard = viewModel.className.value,
                                division = viewModel.division.value,
                                registerNo = registerNo.trim(),
                                dob = dob,
                                category = category,
                                subCategory = subCategory.trim(),
                                parentPhone = parentPhone.trim()
                            )
                            Toast.makeText(context, "Saved! Ready for next.", Toast.LENGTH_SHORT).show()
                            
                            // Auto-increment roll number
                            val currentRollInt = rollNo.toIntOrNull() ?: 1
                            rollNo = (currentRollInt + 1).toString()
                            name = ""
                            registerNo = ""
                            dob = ""
                            subCategory = ""
                            category = "General"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Add Next", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        name = ""
                        registerNo = ""
                        dob = ""
                        subCategory = ""
                        category = "General"
                        val nextRoll = (filteredStudents.mapNotNull { it.rollNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
                        rollNo = nextRoll.toString()
                        Toast.makeText(context, "Form Cleared!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Form")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmitStudentScreen(
    viewModel: AttendanceViewModel,
    filteredStudents: List<Student>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var rollNo by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Boy") }
    var registerNo by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OPEN") }
    var subCategory by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }

    var admissionDate by remember {
        mutableStateOf(String.format("%02d-%02d-%04d", 1, selectedMonth, selectedYear))
    }
    var admissionType by remember { mutableStateOf("New") }
    var admissionRemark by remember { mutableStateOf("") }

    val categories = listOf("SC", "ST", "VJ/NT", "SBC", "OBC", "OPEN", "Minority", "BC ONCE", "PAYING")

    // Auto-set next roll number on load
    LaunchedEffect(filteredStudents) {
        val nextRoll = (filteredStudents.mapNotNull { it.rollNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
        rollNo = nextRoll.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admit Student (Monthly)", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val classNameState by viewModel.className.collectAsStateWithLifecycle()
            val divisionState by viewModel.division.collectAsStateWithLifecycle()

            Text(
                text = "Admit a new student during the active month to automatically include them in the Table 3 (Pupils Admitted) report.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Student General Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Roll Number
                    OutlinedTextField(
                        value = rollNo,
                        onValueChange = { rollNo = it },
                        label = { Text("Roll Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admit_input_roll_no"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Register Number
                    OutlinedTextField(
                        value = registerNo,
                        onValueChange = { registerNo = it },
                        label = { Text("Register Number") },
                        placeholder = { Text("e.g. REG12345") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admit_input_register_no"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Student Full Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student Full Name") },
                        placeholder = { Text("e.g. John Doe") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admit_input_student_name"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Date of Birth
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Date of Birth") },
                        placeholder = { Text("DD-MM-YYYY") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    2015, 0, 1
                                )
                                datePickerDialog.show()
                            }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        dob = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    2015, 0, 1
                                )
                                datePickerDialog.show()
                            }
                            .testTag("admit_input_student_dob"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Gender
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Gender",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf("Boy", "Girl").forEach { gender ->
                                val isSelected = selectedGender == gender
                                Card(
                                    onClick = { selectedGender = gender },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = gender,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Category
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                SuggestionChip(
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    // Caste (Subcategory)
                    OutlinedTextField(
                        value = subCategory,
                        onValueChange = { subCategory = it },
                        label = { Text("Caste") },
                        placeholder = { Text("e.g. Maratha, Mali (Optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admit_input_sub_category"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Parent Phone Number
                    OutlinedTextField(
                        value = parentPhone,
                        onValueChange = { parentPhone = it },
                        label = { Text("Parent Phone Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admit_input_parent_phone"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Text(
                text = "Admission Details (Monthly Report Sync)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Admission Date
                    OutlinedTextField(
                        value = admissionDate,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Admission Date *") },
                        placeholder = { Text("DD-MM-YYYY") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        admissionDate = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    selectedYear, selectedMonth - 1, 1
                                )
                                datePickerDialog.show()
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Admission Date")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, yearVal, monthOfYear, dayOfMonth ->
                                        admissionDate = String.format("%02d-%02d-%04d", dayOfMonth, monthOfYear + 1, yearVal)
                                    },
                                    selectedYear, selectedMonth - 1, 1
                                )
                                datePickerDialog.show()
                            }
                            .testTag("admit_input_admission_date"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Admission Type
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Admission Type",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf("New", "By Transfer").forEach { type ->
                                val isSelected = admissionType == type
                                Card(
                                    onClick = { admissionType = type },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (type == "New") "New Admission" else "By Transfer",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Admission Remark (Optional, syncs with Table 3)
                    OutlinedTextField(
                        value = admissionRemark,
                        onValueChange = { admissionRemark = it },
                        label = { Text("Admission Remark (Optional)") },
                        placeholder = { Text("e.g. Admitted by transfer, New Admission") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admit_input_admission_remark"),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (name.isBlank() || rollNo.isBlank()) {
                            Toast.makeText(context, "Name and Roll Number are required!", Toast.LENGTH_SHORT).show()
                        } else if (filteredStudents.any { it.rollNo == rollNo }) {
                            Toast.makeText(context, "Roll Number $rollNo already exists in this class!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addStudent(
                                name = name.trim(),
                                rollNo = rollNo.trim(),
                                gender = selectedGender,
                                classStandard = viewModel.className.value,
                                division = viewModel.division.value,
                                registerNo = registerNo.trim(),
                                dob = dob,
                                category = category,
                                subCategory = subCategory.trim(),
                                parentPhone = parentPhone.trim(),
                                admissionDate = admissionDate,
                                admissionType = admissionType,
                                admissionRemark = admissionRemark.trim()
                            )
                            Toast.makeText(context, "Student Admitted Successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Exit", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (name.isBlank() || rollNo.isBlank()) {
                            Toast.makeText(context, "Name and Roll Number are required!", Toast.LENGTH_SHORT).show()
                        } else if (filteredStudents.any { it.rollNo == rollNo }) {
                            Toast.makeText(context, "Roll Number $rollNo already exists in this class!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addStudent(
                                name = name.trim(),
                                rollNo = rollNo.trim(),
                                gender = selectedGender,
                                classStandard = viewModel.className.value,
                                division = viewModel.division.value,
                                registerNo = registerNo.trim(),
                                dob = dob,
                                category = category,
                                subCategory = subCategory.trim(),
                                parentPhone = parentPhone.trim(),
                                admissionDate = admissionDate,
                                admissionType = admissionType,
                                admissionRemark = admissionRemark.trim()
                            )
                            Toast.makeText(context, "Saved! Ready for next.", Toast.LENGTH_SHORT).show()

                            // Auto-increment roll number
                            val currentRollInt = rollNo.toIntOrNull() ?: 1
                            rollNo = (currentRollInt + 1).toString()
                            name = ""
                            registerNo = ""
                            dob = ""
                            subCategory = ""
                            category = "OPEN"
                            admissionRemark = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.AppRegistration, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Add Next", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        name = ""
                        registerNo = ""
                        dob = ""
                        subCategory = ""
                        category = "OPEN"
                        admissionRemark = ""
                        val nextRoll = (filteredStudents.mapNotNull { it.rollNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
                        rollNo = nextRoll.toString()
                        Toast.makeText(context, "Form Cleared!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Form")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var currentMonthYear by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var isYearSelectorOpen by remember { mutableStateOf(false) }

    val monthsList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            TextButton(
                onClick = {
                    onDateSelected(selectedDate)
                    onDismissRequest()
                }
            ) {
                Text("Select", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with gorgeous selected date display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = selectedDate.year.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Navigation Controls for Month / Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isYearSelectorOpen = !isYearSelectorOpen }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${monthsList[currentMonthYear.monthValue - 1]} ${currentMonthYear.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (isYearSelectorOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Toggle View",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!isYearSelectorOpen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    currentMonthYear = currentMonthYear.minusMonths(1)
                                }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Prev Month")
                            }
                            IconButton(
                                onClick = {
                                    currentMonthYear = currentMonthYear.plusMonths(1)
                                }
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
                            }
                        }
                    }
                }

                if (isYearSelectorOpen) {
                    // List of Year Options
                    val yearsRange = (2015..2035).toList()
                    val lazyListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = (yearsRange.indexOf(currentMonthYear.year) - 3).coerceAtLeast(0)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(yearsRange) { y ->
                                val isSelected = currentMonthYear.year == y
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            currentMonthYear = YearMonth.of(y, currentMonthYear.monthValue)
                                            isYearSelectorOpen = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = y.toString(),
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Month Calendar grid
                    val daysInMonth = currentMonthYear.lengthOfMonth()
                    val firstDayOfMonth = currentMonthYear.atDay(1)
                    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
                    val offset = if (firstDayOfWeek == 7) 0 else firstDayOfWeek

                    val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Days headers
                        Row(modifier = Modifier.fillMaxWidth()) {
                            weekdays.forEach { dayLabel ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // Weeks
                        val totalCells = offset + daysInMonth
                        val rowsCount = (totalCells + 6) / 7

                        for (r in 0 until rowsCount) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val dayNum = cellIndex - offset + 1

                                    if (cellIndex < offset || dayNum > daysInMonth) {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val date = currentMonthYear.atDay(dayNum)
                                        val isSelected = selectedDate == date
                                        val isToday = LocalDate.now() == date
                                        val isSunday = c == 0

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                                                    color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    selectedDate = date
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isSunday -> Color(0xFFC62828)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
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
    )
}

