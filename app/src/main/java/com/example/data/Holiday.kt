package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey val date: String, // Format: "YYYY-MM-DD"
    val reason: String
)
