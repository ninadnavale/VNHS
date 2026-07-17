package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // Students
    @Query("SELECT * FROM students ORDER BY rollNo ASC, name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: Int)

    // Holidays
    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun getAllHolidays(): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays WHERE date LIKE :monthPattern ORDER BY date ASC")
    fun getHolidaysForMonth(monthPattern: String): Flow<List<Holiday>> // e.g. "2026-07-%"

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoliday(holiday: Holiday)

    @Delete
    suspend fun deleteHoliday(holiday: Holiday)

    @Query("DELETE FROM holidays WHERE date = :date")
    suspend fun deleteHolidayByDate(date: String)

    // Attendance Records
    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date LIKE :monthPattern")
    fun getAttendanceForMonth(monthPattern: String): Flow<List<AttendanceRecord>> // e.g. "2026-07-%"

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records WHERE date = :date")
    suspend fun deleteAttendanceForDate(date: String)

    @Query("DELETE FROM attendance_records WHERE studentId = :studentId")
    suspend fun deleteAttendanceForStudent(studentId: Int)
}
