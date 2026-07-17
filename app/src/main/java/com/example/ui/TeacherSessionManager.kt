package com.example.ui

import android.content.Context
import android.content.SharedPreferences

class TeacherSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TeacherPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOGGED_IN_TEACHER = "logged_in_teacher"
        private const val PREFIX_TEACHER_PASS = "teacher_pass_"
    }

    fun registerTeacher(name: String, pass: String): Boolean {
        val trimmedName = name.trim()
        val trimmedPass = pass.trim()
        if (trimmedName.isEmpty() || trimmedPass.isEmpty()) return false
        
        // Check if teacher already registered
        if (prefs.contains(PREFIX_TEACHER_PASS + trimmedName.lowercase())) {
            return false // Already exists
        }
        
        prefs.edit()
            .putString(PREFIX_TEACHER_PASS + trimmedName.lowercase(), trimmedPass)
            .putString(PREFIX_TEACHER_PASS + trimmedName.lowercase() + "_display", trimmedName)
            .apply()
        return true
    }

    fun loginTeacher(name: String, pass: String): Boolean {
        val trimmedName = name.trim()
        val trimmedPass = pass.trim()
        val savedPass = prefs.getString(PREFIX_TEACHER_PASS + trimmedName.lowercase(), null)
        if (savedPass != null && savedPass == trimmedPass) {
            val displayName = prefs.getString(PREFIX_TEACHER_PASS + trimmedName.lowercase() + "_display", trimmedName) ?: trimmedName
            prefs.edit().putString(KEY_LOGGED_IN_TEACHER, displayName).apply()
            return true
        }
        return false
    }

    fun getLoggedInTeacher(): String? {
        return prefs.getString(KEY_LOGGED_IN_TEACHER, null)
    }

    fun logoutTeacher() {
        prefs.edit().remove(KEY_LOGGED_IN_TEACHER).apply()
    }
}
