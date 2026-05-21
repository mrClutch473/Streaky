package com.example.streaky

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.example.streaky.auth.UserSession
import com.example.streaky.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import com.example.streaky.R
import com.example.streaky.databinding.ActivityStatsBinding

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    private val calendarCells = mutableListOf<View>()

    // ── Cached colour ints (parsed once after setContentView) ─────────────────
    private val colorGreen  by lazy { Color.parseColor("#4CAF50") }
    private val colorOrange by lazy { Color.parseColor("#FF6B35") }
    private val colorGold   by lazy { Color.parseColor("#FFD700") }
    private val colorDark   by lazy { Color.parseColor("#2A2A2A") }
    private val colorGrey   by lazy { Color.parseColor("#9E9E9E") }

    private val colorRed by lazy { Color.parseColor("#CF6679") }
    private val colorWhite  by lazy { Color.WHITE }

    companion object {

        const val TAG                  = "StatsActivity"

        const val EXTRA_HABIT_ID       = "extra_habit_id"
        const val EXTRA_HABIT_NAME     = "extra_habit_name"
        const val EXTRA_HABIT_EMOJI    = "extra_habit_emoji"

        const val EXTRA_STREAK          = "extra_streak"
        const val EXTRA_BEST_STREAK     = "extra_best_streak"
        const val EXTRA_TOTAL_COMPLETED = "extra_total_completed"
        const val EXTRA_COMPLETED_DAYS  = "extra_completed_days"

        fun launch(
            activity: Activity,
            habitName: String,
            habitIcon: String,
            streak: Int,
            bestStreak: Int,
            totalCompleted: Int,
            completedDays: BooleanArray,
            sharedIconView: View
        ) {
            val intent = Intent(activity, StatsActivity::class.java).apply {
                putExtra(EXTRA_HABIT_NAME,      habitName)
                putExtra(EXTRA_HABIT_EMOJI,     habitIcon)
                putExtra(EXTRA_STREAK,          streak)
                putExtra(EXTRA_BEST_STREAK,     bestStreak)
                putExtra(EXTRA_TOTAL_COMPLETED, totalCompleted)
                putExtra(EXTRA_COMPLETED_DAYS,  completedDays)
            }

            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                activity,
                Pair(sharedIconView, activity.getString(R.string.transition_habit_icon))
            )
            activity.startActivity(intent, options.toBundle())
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        setupSharedElementTransition()
        super.onCreate(savedInstanceState)
        postponeEnterTransition()

        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val habitId   = intent.getLongExtra(EXTRA_HABIT_ID,       -1L)
        val habitName = intent.getStringExtra(EXTRA_HABIT_NAME)  ?: "Привычка"
        val habitIcon = intent.getStringExtra(EXTRA_HABIT_EMOJI) ?: "💧"

        setupToolbar(habitName)
        bindHeroBlock(habitIcon)
        setupDeleteButton(habitId)

        // Запускаем переход сразу после layout — данные загрузятся параллельно
        binding.root.doOnPreDraw {
            startPostponedEnterTransition()
            binding.root.post { playEntranceAnimations() }
        }

        loadStats(habitId)
    }

    private fun loadStats(habitId: Long) {
        lifecycleScope.launch {
            try {
                val stats = RetrofitClient.apiService.getHabitStats(
                    id = habitId,
                    userId = UserSession.userId
                )

                bindStatTiles(stats.bestStreak, stats.totalCompletions)
                buildCalendarGrid(
                    completedDates = stats.last30Days.toSet(),
                    createdAt      = stats.createdAt
                )
                playCountUpAnimation(targetValue = stats.streak)
            } catch (e: Exception) {
                buildCalendarGrid(
                    completedDates = emptySet(),
                    createdAt      = ""
                )
                android.util.Log.e("StatsActivity", "loadStats failed: ${e.message}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Shared Element Transition
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Sets a combined ChangeBounds + ChangeTransform transition as the
     * shared element enter transition. The emoji [tvHabitIcon] is tagged
     * with transitionName="@string/transition_habit_icon" in the layout.
     */
    private fun setupSharedElementTransition() {
        val transition = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            duration = 380
            interpolator = DecelerateInterpolator(1.8f)
            ordering = TransitionSet.ORDERING_TOGETHER
        }
        window.sharedElementEnterTransition  = transition
        window.sharedElementReturnTransition = transition
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UI setup helpers
    // ═════════════════════════════════════════════════════════════════════════

    private fun setupToolbar(habitName: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = habitName
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun bindHeroBlock(icon: String) {
        binding.tvHabitIcon.text    = icon
        binding.tvStreakNumber.text = "0"   // CountUp starts from 0
    }

    private fun bindStatTiles(bestStreak: Int, totalCompleted: Int) {
        // "Сейчас" is set by CountUp; best and total are set directly
        binding.tvStatBest.text  = bestStreak.toString()
        binding.tvStatTotal.text = totalCompleted.toString()
    }

    private fun showDeleteConfirmation(habitId: Long) {
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Streaky_MaterialAlertDialog)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteHabit(habitId)
            }
            .show()
    }

    private fun setupDeleteButton(habitId: Long) {
        binding.btnDeleteHabit.setOnClickListener {
            showDeleteConfirmation(habitId)
        }
    }

    private fun deleteHabit(habitId: Long) {
        lifecycleScope.launch {
            try {
                RetrofitClient.apiService.deleteHabit(
                    id = habitId,
                    userId = UserSession.userId
                )
                setResult(RESULT_OK)
                finishAfterTransition()
            } catch (e: Exception) {
                android.util.Log.e("StatsActivity", "deleteHabit failed: ${e.message}")
            }
        }
    }

    private fun buildCalendarGrid(completedDates: Set<String>, createdAt: String) {
        binding.calendarGrid.doOnLayout { grid ->
            val gridLayout = grid as GridLayout
            val cols   = 6
            val gapPx  = 8.dpToPx()
            val cellPx = (grid.width - (cols - 1) * gapPx) / cols

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val todayStr = sdf.format(Calendar.getInstance().time)

            // Стартуем с даты создания привычки
            val iterCal = Calendar.getInstance().apply {
                time = sdf.parse(createdAt) ?: return@doOnLayout
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }

            (0 until 30).forEach { i ->
                val dateStr   = sdf.format(iterCal.time)
                val dayNumber = iterCal.get(Calendar.DAY_OF_MONTH)
                val isToday   = dateStr == todayStr
                val isFuture  = dateStr > todayStr   // строковое сравнение ISO работает корректно
                val isDone    = completedDates.contains(dateStr)

                iterCal.add(Calendar.DAY_OF_YEAR, 1)

                val col = i % cols
                val row = i / cols

                val cell = createCalendarCell(
                    isDone    = isDone,
                    isToday   = isToday,
                    isFuture  = isFuture,
                    sizePx    = cellPx,
                    dayNumber = dayNumber
                )
                cell.alpha  = 0f
                cell.scaleX = 0f
                cell.scaleY = 0f

                gridLayout.addView(cell, GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(col)
                ).apply {
                    width        = cellPx
                    height       = cellPx
                    topMargin    = if (row > 0) gapPx else 0
                    leftMargin   = if (col > 0) gapPx else 0
                    rightMargin  = 0
                    bottomMargin = 0
                })
                calendarCells.add(cell)
            }

            binding.tvCompletionSummary.text =
                getString(R.string.completion_summary, completedDates.size)

            binding.calendarCard.postDelayed({ playCalendarCellsStagger() }, 500)
        }
    }

    private fun createCalendarCell(
        isDone: Boolean,
        isToday: Boolean,
        isFuture: Boolean,
        sizePx: Int,
        dayNumber: Int
    ): TextView {
        val density = resources.displayMetrics.density

        return TextView(this).apply {
            text               = dayNumber.toString()
            textSize           = 9f
            gravity            = Gravity.CENTER
            includeFontPadding = false
            layoutParams       = ViewGroup.LayoutParams(sizePx, sizePx)

            when {
                isDone -> {
                    // Выполнено — зелёный (проверяем первым, независимо от даты)
                    background = ovalDrawable(fill = colorGreen)
                    setTextColor(colorWhite)
                }
                isFuture -> {
                    // Ещё не наступил — почти невидимый тёмный
                    background = ovalDrawable(fill = Color.parseColor("#1F1F1F"))
                    setTextColor(Color.parseColor("#3A3A3A"))
                }
                isToday -> {
                    // Сегодня, ещё не отмечено — обводка
                    background = ovalDrawable(
                        fill        = colorDark,
                        strokeColor = colorGrey,
                        strokePx    = (1.5f * density).toInt()
                    )
                    setTextColor(colorGrey)
                }
                else -> {
                    // Пропущенный день — красный
                    background = ovalDrawable(fill = colorRed)
                    setTextColor(colorWhite)
                }
            }
        }
    }

    private fun ovalDrawable(
        fill: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokePx: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

    private fun playEntranceAnimations() {
        playHeroEntrance()
        playTilesEntrance()
        playCalendarCardEntrance()
    }

    // ── Hero: fade + scale in ─────────────────────────────────────────────────
    private fun playHeroEntrance() {
        binding.heroBlock.apply {
            scaleX = 0.88f
            scaleY = 0.88f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    private fun playTilesEntrance() {
        val tiles = listOf(binding.tileNow, binding.tileBest, binding.tileTotal)
        val slideDistancePx = 28.dpToPx().toFloat()

        tiles.forEachIndexed { i, tile ->
            tile.translationY = slideDistancePx
            tile.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((i * 80).toLong())
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
        }
    }

    private fun playCalendarCardEntrance() {
        binding.calendarCard.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun playCalendarCellsStagger() {
        calendarCells.forEachIndexed { i, cell ->
            val delay = (i * 20).toLong()

            cell.postDelayed({
                cell.animate().alpha(1f).setDuration(80).start()

                SpringAnimation(cell, DynamicAnimation.SCALE_X, 1f).apply {
                    spring.stiffness   = 300f
                    spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    setStartValue(0f)
                    start()
                }
                SpringAnimation(cell, DynamicAnimation.SCALE_Y, 1f).apply {
                    spring.stiffness   = 300f
                    spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    setStartValue(0f)
                    // Pulse completed cells once they fully appear
                    addEndListener { _, _, _, _ ->
                        if (cell is TextView) playDoneCellPulse(cell)
                    }
                    start()
                }
            }, delay)
        }
    }

    private fun playDoneCellPulse(cell: View) {
        val scaleUpX   = ObjectAnimator.ofFloat(cell, View.SCALE_X, 1f, 1.15f)
        val scaleUpY   = ObjectAnimator.ofFloat(cell, View.SCALE_Y, 1f, 1.15f)
        val scaleDownX = ObjectAnimator.ofFloat(cell, View.SCALE_X, 1.15f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(cell, View.SCALE_Y, 1.15f, 1f)

        AnimatorSet().apply {
            play(scaleUpX).with(scaleUpY)
            play(scaleDownX).with(scaleDownY).after(scaleUpX)
            duration     = 120
            startDelay   = 80    // small pause after spring settles
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
    }

    private fun playCountUpAnimation(targetValue: Int) {
        if (targetValue == 0) {
            binding.tvStreakNumber.text = "0"
            binding.tvStatNow.text      = "0"
            return
        }

        ValueAnimator.ofInt(0, targetValue).apply {
            duration    = 600
            startDelay  = 150   // slight delay after hero appears
            interpolator = OvershootInterpolator(0.8f)

            addUpdateListener { animator ->
                val current = animator.animatedValue as Int
                binding.tvStreakNumber.text = current.toString()
                binding.tvStatNow.text      = current.toString()
            }

            start()
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density + 0.5f).toInt()
    }