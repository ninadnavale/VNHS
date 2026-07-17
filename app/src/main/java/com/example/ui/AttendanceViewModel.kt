package com.example.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AttendanceRecord
import com.example.data.Holiday
import com.example.data.Student
import com.example.data.AttendanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(private val repository: AttendanceRepository) : ViewModel() {

    // Current selection for tracking month/year
    val selectedYear = MutableStateFlow(2026)
    val selectedMonth = MutableStateFlow(7) // Default to July

    // School Info (optional custom fields for PDF generation)
    val schoolName = MutableStateFlow("बाहुसार शिक्षण प्रसारक मंडळ संचालित, विद्यानिकेतन हायस्कूल, सोलापूर")
    val schoolAddress = MutableStateFlow("२१/२२, साखर पेठ, सोलापूर-४१३ ००५")
    val udiseNo = MutableStateFlow("27301202509")
    val principalName = MutableStateFlow("Principal")
    val className = MutableStateFlow("10th")
    val division = MutableStateFlow("A")
    
    val classesList = MutableStateFlow(listOf("5th", "6th", "7th", "8th", "9th", "10th"))
    val divisionsList = MutableStateFlow(listOf("A", "B", "C"))

    fun addClass(newClass: String) {
        val current = classesList.value.toMutableList()
        if (!current.contains(newClass) && newClass.isNotBlank()) {
            current.add(newClass)
            classesList.value = current
        }
    }

    fun removeClass(classToRemove: String) {
        val current = classesList.value.toMutableList()
        current.remove(classToRemove)
        classesList.value = current
    }

    fun addDivision(newDiv: String) {
        val current = divisionsList.value.toMutableList()
        if (!current.contains(newDiv) && newDiv.isNotBlank()) {
            current.add(newDiv)
            divisionsList.value = current
        }
    }

    fun removeDivision(divToRemove: String) {
        val current = divisionsList.value.toMutableList()
        current.remove(divToRemove)
        divisionsList.value = current
    }

    // Reactive list of all students
    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Generate Month Pattern flow (e.g. "2026-07-%")
    private val monthPatternFlow = combine(selectedYear, selectedMonth) { year, month ->
        String.format("%04d-%02d-%%", year, month)
    }

    // Reactively observe holidays for selected month
    val holidaysInSelectedMonth: StateFlow<List<Holiday>> = monthPatternFlow
        .flatMapLatest { pattern ->
            repository.getHolidaysForMonth(pattern)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactively observe attendance records for selected month
    val attendanceInSelectedMonth: StateFlow<List<AttendanceRecord>> = monthPatternFlow
        .flatMapLatest { pattern ->
            repository.getAttendanceForMonth(pattern)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Helper: list of local dates in the current selected month
    val datesInSelectedMonth: StateFlow<List<LocalDate>> = combine(selectedYear, selectedMonth) { year, month ->
        val ym = YearMonth.of(year, month)
        (1..ym.lengthOfMonth()).map { day ->
            LocalDate.of(year, month, day)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- STUDENT OPERATIONS ---
    fun addStudent(
        name: String,
        rollNo: String,
        gender: String,
        classStandard: String,
        division: String,
        registerNo: String = "",
        dob: String = "",
        category: String = "",
        subCategory: String = "",
        parentPhone: String = "",
        admissionDate: String = "",
        admissionType: String = "New",
        admissionRemark: String = ""
    ) {
        viewModelScope.launch {
            repository.insertStudent(
                Student(
                    name = name,
                    rollNo = rollNo,
                    gender = gender,
                    classStandard = classStandard,
                    division = division,
                    registerNo = registerNo,
                    dob = dob,
                    category = category,
                    subCategory = subCategory,
                    isRemoved = false,
                    parentPhone = parentPhone,
                    admissionDate = admissionDate,
                    admissionType = admissionType,
                    admissionRemark = admissionRemark
                )
            )
        }
    }

    fun removeStudent(student: Student, date: String, type: String, remark: String) {
        viewModelScope.launch {
            repository.updateStudent(
                student.copy(
                    isRemoved = true,
                    removalDate = date,
                    removalType = type,
                    removalRemark = remark
                )
            )
        }
    }

    fun restoreStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student.copy(isRemoved = false))
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    // --- HOLIDAY OPERATIONS ---
    fun toggleHoliday(date: LocalDate, reason: String = "Holiday") {
        viewModelScope.launch {
            val dateStr = date.toString() // YYYY-MM-DD
            val currentHolidays = holidaysInSelectedMonth.value
            val existing = currentHolidays.find { it.date == dateStr }
            if (existing != null) {
                repository.deleteHolidayByDate(dateStr)
            } else {
                repository.insertHoliday(Holiday(date = dateStr, reason = reason))
            }
        }
    }

    fun markAllSundaysAsHolidays() {
        viewModelScope.launch {
            val year = selectedYear.value
            val month = selectedMonth.value
            val ym = YearMonth.of(year, month)
            for (day in 1..ym.lengthOfMonth()) {
                val date = LocalDate.of(year, month, day)
                if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                    repository.insertHoliday(Holiday(date = date.toString(), reason = "Sunday"))
                }
            }
        }
    }

    fun clearHolidaysInMonth() {
        viewModelScope.launch {
            val currentHolidays = holidaysInSelectedMonth.value
            currentHolidays.forEach {
                repository.deleteHolidayByDate(it.date)
            }
        }
    }

    // --- ATTENDANCE OPERATIONS ---
    fun saveAttendance(date: LocalDate, records: List<AttendanceRecord>) {
        viewModelScope.launch {
            // First clear existing records for this day
            repository.deleteAttendanceForDate(date.toString())
            // Save new records
            repository.insertAttendanceRecords(records)
        }
    }

    // Pre-populate standard demo data for easy evaluation
    fun prePopulateDemoData(classStandard: String, division: String, count: Int = 10) {
        viewModelScope.launch {
            // Delete all existing students for this class standard and division to reset/overwrite cleanly
            val currentForClass = students.value.filter {
                it.classStandard.equals(classStandard, ignoreCase = true) &&
                        it.division.equals(division, ignoreCase = true)
            }
            for (student in currentForClass) {
                repository.deleteStudentById(student.id)
            }

            val boyFirstNames = listOf(
                "Aaron", "Benjamin", "Daniel", "Ethan", "Henry", "James", "Alexander", "Michael", "William", "Oliver",
                "Lucas", "Mason", "Logan", "Elijah", "Ethan", "Aiden", "Jackson", "Sebastian", "Matthew", "Jack",
                "Jayden", "Noah", "Arav", "Vihaan", "Kabir", "Arjun", "Sai", "Aarav", "Krishna", "Aditya",
                "Rahul", "Rohan", "Amit", "Yash", "Dev", "Neil", "Aniket", "Pranav", "Siddharth", "Ishaan"
            )
            val girlFirstNames = listOf(
                "Abigail", "Charlotte", "Emily", "Grace", "Isabella", "Emma", "Olivia", "Sophia", "Ava", "Mia",
                "Amelia", "Harper", "Evelyn", "Abigail", "Ella", "Scarlett", "Aria", "Chloe", "Lily", "Aanya",
                "Aadhya", "Ananya", "Diya", "Ira", "Myra", "Saanvi", "Shruti", "Pooja", "Neha", "Kavya",
                "Riya", "Sneha", "Tanvi", "Prisha", "Isha", "Divya", "Gauri", "Shreya", "Aditi", "Meera"
            )
            val lastNames = listOf(
                "Smith", "Johnson", "Davis", "Miller", "Wilson", "Moore", "Taylor", "Anderson", "Thomas", "Jackson",
                "White", "Harris", "Martin", "Thompson", "Garcia", "Martinez", "Robinson", "Clark", "Rodriguez", "Lewis",
                "Lee", "Walker", "Hall", "Allen", "Young", "Hernandez", "King", "Wright", "Lopez", "Hill",
                "Sharma", "Patel", "Verma", "Gupta", "Joshi", "Kulkarni", "Deshmukh", "Patil", "More", "Shinde"
            )
            val categories = listOf("OPEN", "OBC", "SC", "ST")
            val subCategories = mapOf(
                "OPEN" to listOf("Maratha", "Brahmin", "Rajput", "Jat", "Kapu"),
                "OBC" to listOf("Mali", "Teli", "Kunbi", "Yadav", "Kurmi"),
                "SC" to listOf("Mahar", "Chambhar", "Mang", "Jatav"),
                "ST" to listOf("Bhil", "Gond", "Koli", "Katkari")
            )

            val year = selectedYear.value
            val month = selectedMonth.value
            val ym = YearMonth.of(year, month)
            val daysInMonth = ym.lengthOfMonth()
            val random = java.util.Random()
            
            val attendanceRecordsToInsert = ArrayList<AttendanceRecord>()

            for (i in 1..count) {
                val isBoy = i % 2 == 1
                val gender = if (isBoy) "Boy" else "Girl"
                val firstNamePool = if (isBoy) boyFirstNames else girlFirstNames
                val firstName = firstNamePool[((i - 1) / 2) % firstNamePool.size]
                val lastName = lastNames[(i - 1) % lastNames.size]
                val name = "$firstName $lastName"
                
                val rollNo = i.toString()
                val regNo = "REG-${9420 + i}"
                
                // Generate varied date of birth around 2008-2012
                val birthYear = 2008 + (i % 5)
                val birthMonth = 1 + (i % 12)
                val birthDay = 1 + (i % 28)
                val dobStr = String.format("%04d-%02d-%02d", birthYear, birthMonth, birthDay)
                
                val category = categories[i % categories.size]
                val subCatPool = subCategories[category] ?: listOf("General")
                val subCategory = subCatPool[i % subCatPool.size]
                
                // Generate a random 10-digit Indian mobile number
                val randomPhone = "9" + String.format("%09d", random.nextInt(1000000000))
                
                val student = Student(
                    name = name,
                    rollNo = rollNo,
                    gender = gender,
                    classStandard = classStandard,
                    division = division,
                    registerNo = regNo,
                    dob = dobStr,
                    category = category,
                    subCategory = subCategory,
                    parentPhone = randomPhone,
                    admissionDate = "$year-06-15",
                    admissionType = "New"
                )
                
                // Insert student and obtain their newly generated ID
                val studentId = repository.insertStudent(student).toInt()

                // Generate random weekday attendance for the entire month
                for (day in 1..daysInMonth) {
                    val date = LocalDate.of(year, month, day)
                    if (date.dayOfWeek != DayOfWeek.SUNDAY) {
                        val status = if (random.nextFloat() < 0.90f) "P" else "A"
                        attendanceRecordsToInsert.add(
                            AttendanceRecord(
                                date = date.toString(),
                                studentId = studentId,
                                status = status
                            )
                        )
                    }
                }
            }

            // Insert all generated attendance records
            if (attendanceRecordsToInsert.isNotEmpty()) {
                repository.insertAttendanceRecords(attendanceRecordsToInsert)
            }

            // Auto-mark Sundays as holidays
            markAllSundaysAsHolidays()
        }
    }

    // --- REPORT GENERATION ---
    fun generateAndSaveReport(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            val filteredStudents = students.value.filter {
                it.classStandard.equals(className.value, ignoreCase = true) &&
                        it.division.equals(division.value, ignoreCase = true)
            }
            val file = PdfGenerator.generateAttendancePdf(
                context = context,
                year = selectedYear.value,
                month = selectedMonth.value,
                students = filteredStudents,
                holidays = holidaysInSelectedMonth.value,
                attendanceRecords = attendanceInSelectedMonth.value,
                schoolName = schoolName.value,
                schoolAddress = schoolAddress.value,
                udiseNo = udiseNo.value,
                className = className.value,
                division = division.value
            )
            onComplete(file)
        }
    }
}

// Custom Factory for ViewModel
class AttendanceViewModelFactory(private val repository: AttendanceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AttendanceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
