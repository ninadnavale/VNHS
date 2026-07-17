package com.example.data

import kotlinx.coroutines.flow.Flow

class AttendanceRepository(private val attendanceDao: AttendanceDao) {

    // Students
    val allStudents: Flow<List<Student>> = attendanceDao.getAllStudents()

    suspend fun insertStudent(student: Student): Long {
        return attendanceDao.insertStudent(student)
    }

    suspend fun updateStudent(student: Student) {
        attendanceDao.updateStudent(student)
    }

    suspend fun deleteStudent(student: Student) {
        // Also clean up their attendance records to avoid orphaned records
        attendanceDao.deleteAttendanceForStudent(student.id)
        attendanceDao.deleteStudent(student)
    }

    suspend fun deleteStudentById(id: Int) {
        attendanceDao.deleteAttendanceForStudent(id)
        attendanceDao.deleteStudentById(id)
    }

    // Holidays
    val allHolidays: Flow<List<Holiday>> = attendanceDao.getAllHolidays()

    fun getHolidaysForMonth(monthPattern: String): Flow<List<Holiday>> {
        return attendanceDao.getHolidaysForMonth(monthPattern)
    }

    suspend fun insertHoliday(holiday: Holiday) {
        attendanceDao.insertHoliday(holiday)
    }

    suspend fun deleteHolidayByDate(date: String) {
        attendanceDao.deleteHolidayByDate(date)
    }

    // Attendance Records
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForDate(date)
    }

    fun getAttendanceForMonth(monthPattern: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForMonth(monthPattern)
    }

    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>) {
        attendanceDao.insertAttendanceRecords(records)
    }

    suspend fun deleteAttendanceForDate(date: String) {
        attendanceDao.deleteAttendanceForDate(date)
    }
}
