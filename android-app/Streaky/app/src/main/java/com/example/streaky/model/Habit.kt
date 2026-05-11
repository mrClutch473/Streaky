package com.example.streaky.model

data class Habit(
    val id: Long,
    val name: String,
    val emoji: String,
    val streakDays: Int,
    val isCompletedToday: Boolean
)
