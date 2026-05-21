package com.example.streaky.network

import com.example.streaky.model.Habit
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

// ─── Auth DTOs ────────────────────────────────────────────────────────────────

data class RegisterRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String
)

data class UserDto(
    @SerializedName("id")         val id: Long,
    @SerializedName("email")      val email: String,
    @SerializedName("created_at") val createdAt: String
)

// ─── Habit DTOs ───────────────────────────────────────────────────────────────

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
    @SerializedName("name")              val name: String,
    @SerializedName("icon")              val icon: String,
    @SerializedName("streak")            val streak: Int,
    @SerializedName("best_streak")       val bestStreak: Int,
    @SerializedName("total_completions") val totalCompletions: Int,
    @SerializedName("last_30_days")      val last30Days: List<String>,
    @SerializedName("created_at")        val createdAt: String
)

data class TodayResponse(
    @SerializedName("date_string") val dateString: String
)

// ─── Mapper ───────────────────────────────────────────────────────────────────

fun HabitDto.toHabit() = Habit(
    id               = id,
    name             = name,
    emoji            = icon,
    streakDays       = streak,
    isCompletedToday = completedToday
)

// ─── API Interface ────────────────────────────────────────────────────────────

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): UserDto

    @POST("login")
    suspend fun login(@Body request: LoginRequest): UserDto

    // ── Habits (все требуют user_id как query-параметр) ───────────────────────

    @GET("habits")
    suspend fun getHabits(
        @Query("user_id") userId: Long
    ): List<HabitDto>

    @POST("habits")
    suspend fun createHabit(
        @Body request: CreateHabitRequest,
        @Query("user_id") userId: Long
    ): HabitDto

    @DELETE("habits/{id}")
    suspend fun deleteHabit(
        @Path("id") id: Long,
        @Query("user_id") userId: Long
    ): Response<Unit>

    @POST("habits/{id}/complete")
    suspend fun toggleComplete(
        @Path("id") id: Long,
        @Query("user_id") userId: Long
    ): CompleteToggleResponse

    @GET("habits/{id}/stats")
    suspend fun getHabitStats(
        @Path("id") id: Long,
        @Query("user_id") userId: Long
    ): HabitStatsDto

    // ── Misc ──────────────────────────────────────────────────────────────────

    @GET("today")
    suspend fun getToday(): TodayResponse
}