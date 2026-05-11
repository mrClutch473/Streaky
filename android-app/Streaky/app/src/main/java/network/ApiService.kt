package com.example.streaky.network

import com.example.streaky.model.Habit
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Path

data class HabitDto(
    @SerializedName("id")              val id: Long,
    @SerializedName("name")            val name: String,
    @SerializedName("icon")            val icon: String,
    @SerializedName("streak")          val streak: Int,
    @SerializedName("completed_today") val completedToday: Boolean,
    @SerializedName("created_at")      val createdAt: String
)

data class CreateHabitRequest(
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String
)

data class CompleteToggleResponse(
    @SerializedName("id")              val id: Long,
    @SerializedName("completed_today") val completedToday: Boolean,
    @SerializedName("streak")          val streak: Int
)

data class HabitStatsDto(
    @SerializedName("id")                val id: Long,
    @SerializedName("name")             val name: String,
    @SerializedName("icon")             val icon: String,
    @SerializedName("streak")           val streak: Int,
    @SerializedName("best_streak")      val bestStreak: Int,
    @SerializedName("total_completions") val totalCompletions: Int,
    @SerializedName("last_30_days")     val last30Days: List<String>,
    @SerializedName("created_at")      val createdAt: String
)

data class TodayResponse(
    @SerializedName("date_string") val dateString: String
)

fun HabitDto.toHabit() = Habit(
    id              = id,
    name            = name,
    emoji           = icon,
    streakDays      = streak,
    isCompletedToday = completedToday
)


interface ApiService {

    @GET("habits")
    suspend fun getHabits(): List<HabitDto>

    @POST("habits")
    suspend fun createHabit(@Body request: CreateHabitRequest): HabitDto

    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: Long): Response<Unit>

    @POST("habits/{id}/complete")
    suspend fun toggleComplete(@Path("id") id: Long): CompleteToggleResponse

    @GET("habits/{id}/stats")
    suspend fun getHabitStats(@Path("id") id: Long): HabitStatsDto

    @GET("today")
    suspend fun getToday(): TodayResponse
}