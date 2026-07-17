package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rollNo: String,
    val gender: String, // "Boy" or "Girl"
    val classStandard: String = "5th",
    val division: String = "A",
    val registerNo: String = "",
    val dob: String = "",
    val category: String = "",
    val subCategory: String = "",
    val isRemoved: Boolean = false,
    val parentPhone: String = "",
    val admissionDate: String = "",
    val admissionType: String = "New", // "New" or "By Transfer"
    val removalDate: String = "",
    val removalType: String = "Left", // "Left" or "By Transfer"
    val removalRemark: String = "",
    val admissionRemark: String = ""
)

