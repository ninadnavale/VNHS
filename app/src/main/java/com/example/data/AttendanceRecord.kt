package com.example.data

import androidx.room.Entity

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["date", "studentId"]
)
data class AttendanceRecord(
    val date: String, // Format: "YYYY-MM-DD"
    val studentId: Int,
    val status: String // "P" or "A"
)
