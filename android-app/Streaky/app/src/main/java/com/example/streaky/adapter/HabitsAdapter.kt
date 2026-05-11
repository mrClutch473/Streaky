package com.example.streaky.adapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.example.streaky.R
import com.example.streaky.databinding.ItemHabitBinding
import com.example.streaky.model.Habit
import android.animation.Keyframe
import android.animation.PropertyValuesHolder
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import kotlin.math.sin
import kotlin.math.cos
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce



sealed class HabitPayload {
    /** User tapped the complete button — carry the new state */
    data class CompletionToggled(val isCompleted: Boolean) : HabitPayload()

    /** Streak counter changed (e.g. after midnight rollover or undo) */
    data class StreakUpdated(val newStreak: Int) : HabitPayload()
}

// ════════════════════════════════════════════════════════════════════════════
// ADAPTER
// ════════════════════════════════════════════════════════════════════════════

/**
 * HabitsAdapter
 *
 * ListAdapter backed by DiffUtil for efficient, animated list updates.
 * All visual state transitions (completed / uncompleted) are driven by
 * Kotlin extension animations — zero XML selectors involved.
 *
 * @param onHabitClicked     called when the user taps the card (opens StatsActivity)
 * @param onHabitChecked     called when the user taps the complete button
 * @param onHabitLongPressed called on long-press (opens edit sheet)
 */
class HabitsAdapter(
    private val onHabitClicked: (Habit) -> Unit,
    private val onHabitChecked: (habit: Habit, position: Int) -> Unit,
    private val onHabitLongPressed: (Habit) -> Unit
) : ListAdapter<Habit, HabitsAdapter.HabitViewHolder>(DIFF_CALLBACK) {

    // ── Inflate ──────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HabitViewHolder(binding)
    }

    // ── Full bind ────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ── Partial bind (payload) ───────────────────────────────────────────────

    override fun onBindViewHolder(
        holder: HabitViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            // No payload → full rebind
            onBindViewHolder(holder, position)
            return
        }

        payloads.forEach { payload ->
            when (payload) {
                is HabitPayload.CompletionToggled -> {
                    if (payload.isCompleted) {
                        holder.binding.habitCard.animateToCompleted(holder.itemView.context)
                        holder.animateCompleteButton(completed = true)
                        holder.playFireCelebration()
                        holder.stopPulse()
                    } else {
                        holder.binding.habitCard.animateToUncompleted(holder.itemView.context)
                        holder.animateCompleteButton(completed = false)
                    }
                }
                is HabitPayload.StreakUpdated -> {
                    val newItem = getItem(holder.bindingAdapterPosition)
                    holder.animateStreakUpdate(
                        oldStreak = holder.currentStreak,
                        newStreak = payload.newStreak,
                        isCompletedToday = newItem.isCompletedToday
                    )
                    holder.currentStreak = payload.newStreak
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // VIEW HOLDER
    // ════════════════════════════════════════════════════════════════════════

    inner class HabitViewHolder(
        val binding: ItemHabitBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Cached streak value — used by StreakUpdated payload to know the "from" value */
        var currentStreak: Int = 0
            internal set

        private var pulseAnimator: ValueAnimator? = null

        fun stopPulse() {
            pulseAnimator?.cancel()
            pulseAnimator = null
            binding.habitStreak.alpha = 1f
        }

        // ── bind ─────────────────────────────────────────────────────────────

        fun bind(habit: Habit) {
            currentStreak = habit.streakDays

            // ── Static text ──────────────────────────────────────────────────
            binding.habitIcon.text = habit.emoji
            binding.habitName.text = habit.name
            bindStreakText(habit.streakDays, habit.isCompletedToday)

            // ── Completion visual state (no animation on initial bind) ────────
            applyCompletionState(habit.isCompletedToday, animated = false)

            // ── Listeners ────────────────────────────────────────────────────
            binding.habitCard.setOnClickListener {
                onHabitClicked(habit)
            }

            binding.completeButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    onHabitChecked(habit, pos)
                }
            }

            binding.habitCard.setOnLongClickListener {
                onHabitLongPressed(habit)
                true
            }
        }

        fun animateRevertToStart() {
            val ctx = itemView.context
            val streakView = binding.habitStreak

            // Фаза 1: текст "🔥 1 день" улетает вниз и гаснет (обратно тому как появился)
            streakView.animate()
                .alpha(0f)
                .translationY(20f.dpF(ctx))
                .setDuration(180)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    streakView.text = ctx.getString(R.string.streak_zero_text)
                    streakView.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            ctx, R.color.md_theme_dark_onSurfaceVariant
                        )
                    )
                    streakView.translationY = -20f.dpF(ctx)

                    // Фаза 2: "Начни сегодня" появляется сверху вниз
                    streakView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(250)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
        }

        fun animateFirstDayStreak() {
            val ctx = itemView.context
            val streakView = binding.habitStreak

            // Фаза 1: текст "Начни сегодня" улетает вверх и гаснет
            streakView.animate()
                .alpha(0f)
                .translationY(-20f.dpF(ctx))
                .setDuration(180)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    // Меняем текст пока он невидим
                    streakView.text = ctx.getString(R.string.habit_streak_format, 1)
                    streakView.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            ctx, R.color.md_theme_dark_secondary
                        )
                    )
                    // Сбрасываем позицию снизу
                    streakView.translationY = 20f.dpF(ctx)

                    // Фаза 2: новый текст появляется снизу вверх
                    streakView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                }
                .start()
        }

        // ── Streak text helper ────────────────────────────────────────────────

        fun bindStreakText(streakDays: Int, isCompletedToday: Boolean) {
            val ctx = itemView.context
            val streakView = binding.habitStreak

            // Останавливаем предыдущую пульсацию в любом случае
            pulseAnimator?.cancel()
            pulseAnimator = null
            streakView.alpha = 1f

            when {
                // Привычка выполнена сегодня
                isCompletedToday -> {
                    streakView.text = ctx.getString(R.string.habit_streak_format, streakDays)
                    streakView.setTextColor(
                        androidx.core.content.ContextCompat.getColor(ctx, R.color.md_theme_dark_secondary)
                    )
                }
                // Стрик есть, но сегодня ещё не отмечено → мотивирующий текст янтарного цвета
                // Намеренно отличается от оранжевого "выполнено" (#FF6B35) и серого "начни сегодня"
                streakDays > 0 -> {
                    streakView.text = ctx.getString(R.string.streak_keep_going_format, streakDays)
                    streakView.setTextColor(Color.parseColor("#FFB300")) // янтарный — между золотом и оранжевым
                    pulseAnimator = ValueAnimator.ofFloat(1f, 0.5f, 1f).apply {
                        duration = 1600
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.RESTART
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        addUpdateListener { streakView.alpha = it.animatedValue as Float }
                        start()
                    }
                }
                // Стрика нет, привычка ещё не начата
                else -> {
                    streakView.text = ctx.getString(R.string.streak_zero_text)
                    streakView.setTextColor(
                        androidx.core.content.ContextCompat.getColor(ctx, R.color.md_theme_dark_onSurfaceVariant)
                    )
                }
            }
        }

        // ── Instant state apply (no animation) ───────────────────────────────

        private fun applyCompletionState(isCompleted: Boolean, animated: Boolean) {
            val ctx = itemView.context
            if (animated) {
                if (isCompleted) {
                    binding.habitCard.animateToCompleted(ctx)
                    animateCompleteButton(completed = true)
                } else {
                    binding.habitCard.animateToUncompleted(ctx)
                    animateCompleteButton(completed = false)
                }
            } else {
                // Snap to state — used on initial bind to avoid spurious animations
                // when RecyclerView recycles views
                if (isCompleted) {
                    binding.habitCard.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.habit_completed_background)
                    )
                    binding.habitCard.strokeWidth = ctx.dpToPx(1)
                    binding.completedBorderAccent.visibility = View.VISIBLE
                    binding.completedBorderAccent.scaleX = 1f
                    setButtonCompleted(ctx)
                } else {
                    binding.habitCard.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.md_theme_dark_surface)
                    )
                    binding.habitCard.strokeWidth = 0
                    binding.completedBorderAccent.visibility = View.INVISIBLE
                    binding.completedBorderAccent.scaleX = 0f
                    setButtonUnchecked(ctx)
                }
            }
        }

        // ── Button visual helpers ─────────────────────────────────────────────

        private fun setButtonCompleted(ctx: Context) {
            with(binding.completeButton) {
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.md_theme_dark_primary)
                )
                strokeWidth = 0
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_check)
                iconTint = ColorStateList.valueOf(Color.WHITE)
                elevation = 6f
            }
        }

        private fun setButtonUnchecked(ctx: Context) {
            with(binding.completeButton) {
                backgroundTintList = ColorStateList.valueOf(
                    Color.TRANSPARENT
                )
                strokeWidth = ctx.dpToPx(2)
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_circle_outline)
                iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.md_theme_dark_onSurfaceVariant)
                )
                elevation = 0f
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // ✦ ANIMATION: complete button spring
        // Sequence: scale 1.0 → 0.8 → 1.2 → 1.0  (spring-like, 280ms total)
        // ════════════════════════════════════════════════════════════════════

        /**
         * Animates the complete button on tap.
         *
         * When completing:  shrink → overshoot expand → settle, then swap icon.
         * When un-checking: simpler quick scale + icon swap.
         */
        fun animateCompleteButton(completed: Boolean) {
            val btn = binding.completeButton
            val ctx = itemView.context

            if (completed) {
                // Phase 1 — compress (0→80ms)
                val compress = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btn, View.SCALE_X, 1f, 0.75f),
                        ObjectAnimator.ofFloat(btn, View.SCALE_Y, 1f, 0.75f)
                    )
                    duration = 80
                    interpolator = DecelerateInterpolator()
                }

                // Phase 2 — overshoot expand (80→220ms): swap icon at start of this phase
                val expand = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btn, View.SCALE_X, 0.75f, 1.25f),
                        ObjectAnimator.ofFloat(btn, View.SCALE_Y, 0.75f, 1.25f)
                    )
                    duration = 140
                    interpolator = OvershootInterpolator(3f)
                }

                // Phase 3 — settle back to 1.0 (220→280ms)
                val settle = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btn, View.SCALE_X, 1.25f, 1f),
                        ObjectAnimator.ofFloat(btn, View.SCALE_Y, 1.25f, 1f)
                    )
                    duration = 120
                    interpolator = DecelerateInterpolator()
                }

                // Chain: compress → (icon swap + expand) → settle
                AnimatorSet().apply {
                    playSequentially(compress, expand, settle)
                    start()
                }

                // Swap icon at the moment of maximum compression
                btn.postDelayed({ setButtonCompleted(ctx) }, 80)

                // Animate the check icon rotating in (alpha 0→1, rotation -30→0)
                animateCheckIconEntrance()

            } else {
                // Un-check: quick scale pulse then snap icon
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(btn, View.SCALE_X, 1f, 0.85f, 1f),
                        ObjectAnimator.ofFloat(btn, View.SCALE_Y, 1f, 0.85f, 1f)
                    )
                    duration = 180
                    interpolator = DecelerateInterpolator()
                    start()
                }
                btn.postDelayed({ setButtonUnchecked(ctx) }, 90)
            }
        }

        /** Rotates the check icon in: alpha 0→1, rotation -30°→0°, 180ms */
        private fun animateCheckIconEntrance() {
            val btn = binding.completeButton
            // Access the icon drawable view indirectly via the button's icon layer
            val iconRotate = ObjectAnimator.ofFloat(btn, View.ROTATION, -30f, 0f).apply {
                duration = 180
                startDelay = 80          // start after compression phase
                interpolator = OvershootInterpolator(2f)
            }
            val iconFade = ObjectAnimator.ofFloat(btn, View.ALPHA, 0.4f, 1f).apply {
                duration = 150
                startDelay = 80
                interpolator = DecelerateInterpolator()
            }
            AnimatorSet().apply {
                playTogether(iconRotate, iconFade)
                start()
            }
        }

        fun animateStreakUpdate(oldStreak: Int, newStreak: Int, isCompletedToday: Boolean) {

            if (oldStreak == 0 && newStreak == 1 && isCompletedToday) {
                animateFirstDayStreak()
                return
            }

            if (newStreak == 0 && !isCompletedToday) {
                animateRevertToStart()
                return
            }

            if (newStreak <= oldStreak) {
                bindStreakText(newStreak, isCompletedToday)
                return
            }

            val ctx    = itemView.context
            val anchor = binding.habitStreak

            if (newStreak <= oldStreak) {
                bindStreakText(newStreak, isCompletedToday)
                return
            }

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(anchor, View.SCALE_X, 1f, 0.75f, 1f),
                    ObjectAnimator.ofFloat(anchor, View.SCALE_Y, 1f, 0.65f, 1f)
                )
                duration    = 120
                interpolator = AccelerateDecelerateInterpolator()
                doOnEnd {
                    bindStreakText(newStreak, isCompletedToday)
                    SpringAnimation(anchor, DynamicAnimation.SCALE_X, 1f).apply {
                        spring.stiffness    = SpringForce.STIFFNESS_HIGH
                        spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                        setStartValue(1.3f); start()
                    }
                    SpringAnimation(anchor, DynamicAnimation.SCALE_Y, 1f).apply {
                        spring.stiffness    = SpringForce.STIFFNESS_HIGH
                        spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                        setStartValue(1.3f); start()
                    }
                }
                start()
            }

            val orange  = ContextCompat.getColor(ctx, R.color.md_theme_dark_secondary)
            val normal  = ContextCompat.getColor(ctx, R.color.md_theme_dark_secondary)
            ValueAnimator.ofObject(ArgbEvaluator(),
                Color.parseColor("#FFFFD740"),  // яркое золото в начале
                orange                           // затухает в штатный оранжевый
            ).apply {
                duration = 500
                startDelay = 80
                addUpdateListener { anchor.setTextColor(it.animatedValue as Int) }
                start()
            }

            // ── 3. Плавающий бейдж «+1» ─────────────────────────────────────────────
            val parent = binding.root as ViewGroup

            val badge = TextView(ctx).apply {
                text     = "+1"
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FFFFD740"))
                alpha    = 0f
                rotation = -12f
            }

            val badgeSize = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val loc = IntArray(2); anchor.getLocationInWindow(loc)
                val parentLoc = IntArray(2); parent.getLocationInWindow(parentLoc)
                leftMargin = loc[0] - parentLoc[0] + anchor.width + 4.dpToPx(ctx)
                topMargin  = loc[1] - parentLoc[1]
            }
            parent.addView(badge, badgeSize)

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(badge, View.ALPHA,    0f, 1f).apply { duration = 120 },
                    ObjectAnimator.ofFloat(badge, View.SCALE_X,  0.4f, 1.2f).apply { duration = 160; interpolator = OvershootInterpolator(3f) },
                    ObjectAnimator.ofFloat(badge, View.SCALE_Y,  0.4f, 1.2f).apply { duration = 160; interpolator = OvershootInterpolator(3f) },
                    ObjectAnimator.ofFloat(badge, View.ROTATION, -12f, 4f).apply { duration = 200 }
                )
                doOnEnd {
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(badge, View.TRANSLATION_Y, 0f, -52f.dpF(ctx)).apply {
                                duration = 600; interpolator = DecelerateInterpolator(1.4f)
                            },
                            ObjectAnimator.ofFloat(badge, View.ALPHA, 1f, 0f).apply {
                                duration = 500; startDelay = 150
                            },
                            ObjectAnimator.ofFloat(badge, View.SCALE_X, 1.2f, 0.9f).apply { duration = 600 },
                            ObjectAnimator.ofFloat(badge, View.SCALE_Y, 1.2f, 0.9f).apply { duration = 600 }
                        )
                        doOnEnd { parent.removeView(badge) }
                        start()
                    }
                }
                start()
            }

            ObjectAnimator.ofFloat(
                binding.habitIcon, View.ROTATION,
                0f, -8f, 8f, -5f, 5f, -2f, 2f, 0f
            ).apply {
                duration    = 500
                startDelay  = 60
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        fun playFireCelebration() {
            val ctx = itemView.context
            val anchor = binding.completeButton
            val decorView = (ctx as? android.app.Activity)
                ?.window?.decorView as? ViewGroup ?: return

            // Центр кнопки в координатах экрана
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val cx = loc[0] + anchor.width  / 2f
            val cy = loc[1] + anchor.height / 2f

            // 1. Две кольцевые волны
            spawnBurstRing(ctx, decorView, cx, cy,
                startDelay = 0L,
                color      = Color.parseColor("#FFFF6B35"),  // оранжевая
                maxScale   = 2.9f,
                duration   = 430L
            )
            spawnBurstRing(ctx, decorView, cx, cy,
                startDelay = 90L,
                color      = Color.parseColor("#FF4CAF50"),  // зелёная
                maxScale   = 2.3f,
                duration   = 380L
            )

            listOf(
                Triple("🔥", -58f, 26f),
                Triple("🔥", -28f, 30f),
                Triple("🔥",   4f, 32f),
                Triple("🔥",  32f, 27f),
                Triple("🔥",  62f, 23f),
                Triple("✨",  -14f, 19f),
                Triple("✨",   46f, 17f)
            ).forEachIndexed { i, (emoji, angle, size) ->
                spawnParticle(ctx, decorView, cx, cy,
                    emoji      = emoji,
                    angleDeg   = angle,
                    textSizeSp = size,
                    startDelay = (i * 38).toLong()
                )
            }

            // 3. Spring-bounce на кнопке
            anchor.scaleX = 1.4f
            anchor.scaleY = 1.4f
            SpringAnimation(anchor, DynamicAnimation.SCALE_X, 1f).apply {
                spring.stiffness    = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                start()
            }
            SpringAnimation(anchor, DynamicAnimation.SCALE_Y, 1f).apply {
                spring.stiffness    = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                start()
            }
        }

        private fun spawnBurstRing(
            ctx: Context, root: ViewGroup,
            cx: Float, cy: Float,
            startDelay: Long, color: Int,
            maxScale: Float, duration: Long
        ) {
            val sizePx = ctx.dpToPx(54)
            val ring = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(ctx.dpToPx(2), color)
                }
                alpha = 0.85f
            }
            root.addView(ring, FrameLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = (cx - sizePx / 2f).toInt()
                topMargin  = (cy - sizePx / 2f).toInt()
            })

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(ring, View.SCALE_X, 0.35f, maxScale),
                    ObjectAnimator.ofFloat(ring, View.SCALE_Y, 0.35f, maxScale),
                    ObjectAnimator.ofFloat(ring, View.ALPHA,   0.85f, 0f)
                )
                this.startDelay = startDelay
                this.duration   = duration
                interpolator = DecelerateInterpolator(1.6f)
                doOnEnd { root.removeView(ring) }
                start()
            }
        }

        private fun spawnParticle(
            ctx: Context, root: ViewGroup,
            cx: Float, cy: Float,
            emoji: String, angleDeg: Float,
            textSizeSp: Float, startDelay: Long
        ) {
            // Расстояние чуть разное у каждой частицы для объёма
            val baseDistance = (88f + (textSizeSp % 7) * 7f).dpF(ctx)
            val rad = Math.toRadians(angleDeg.toDouble())
            val tx = sin(rad).toFloat() * baseDistance
            val ty = -cos(rad).toFloat() * baseDistance   // вверх = отрицательный Y

            val sizePx = ctx.dpToPx(46)
            val tv = TextView(ctx).apply {
                text     = emoji
                textSize = textSizeSp
                gravity  = Gravity.CENTER
                alpha    = 0f
            }
            root.addView(tv, FrameLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = (cx - sizePx / 2f).toInt()
                topMargin  = (cy - sizePx / 2f).toInt()
            })

            // Keyframe для alpha: быстро появляется → медленно гаснет
            val alphaAnim = ObjectAnimator.ofPropertyValuesHolder(
                tv,
                PropertyValuesHolder.ofKeyframe(
                    View.ALPHA,
                    Keyframe.ofFloat(0f,    0f),
                    Keyframe.ofFloat(0.18f, 1f),
                    Keyframe.ofFloat(1f,    0f)
                )
            )

            AnimatorSet().apply {
                playTogether(
                    alphaAnim,
                    ObjectAnimator.ofFloat(tv, View.TRANSLATION_X, 0f, tx).apply {
                        interpolator = DecelerateInterpolator(1.3f)
                    },
                    ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 0f, ty).apply {
                        interpolator = DecelerateInterpolator(1.3f)
                    },
                    ObjectAnimator.ofFloat(tv, View.SCALE_X, 0.15f, 1.15f, 0.85f),
                    ObjectAnimator.ofFloat(tv, View.SCALE_Y, 0.15f, 1.15f, 0.85f)
                )
                this.startDelay = startDelay
                duration = 780
                doOnEnd { root.removeView(tv) }
                start()
            }
        }

    }

    // ════════════════════════════════════════════════════════════════════════
    // DIFF CALLBACK
    // ════════════════════════════════════════════════════════════════════════

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Habit>() {
            override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean =
                oldItem == newItem

            /**
             * Return a fine-grained payload so onBindViewHolder(payloads)
             * can run only the changed animation instead of a full rebind.
             */
            override fun getChangePayload(oldItem: Habit, newItem: Habit): Any? {
                return when {
                    oldItem.isCompletedToday != newItem.isCompletedToday ->
                        HabitPayload.CompletionToggled(newItem.isCompletedToday)

                    oldItem.streakDays != newItem.streakDays ->
                        HabitPayload.StreakUpdated(newItem.streakDays)

                    else -> null // full rebind
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ✦ EXTENSION: MaterialCardView card background animations
// ════════════════════════════════════════════════════════════════════════════

/**
 * Smoothly transitions the card from its current background color to the
 * "completed" state: background #1B5E20, stroke width 1dp + green border accent.
 *
 * Duration: 320ms · ArgbEvaluator for true color interpolation.
 */
fun MaterialCardView.animateToCompleted(context: Context) {
    val fromColor = (background as? ColorDrawable)?.color
        ?: ContextCompat.getColor(context, R.color.md_theme_dark_surface)
    val toColor = ContextCompat.getColor(context, R.color.habit_completed_background)

    ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
        duration = 320
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            setCardBackgroundColor(animator.animatedValue as Int)
        }
        doOnEnd {
            // Reveal left border accent with a horizontal scaleX expand
            val border = findViewById<View>(R.id.completedBorderAccent)
            border?.apply {
                visibility = View.VISIBLE
                animate()
                    .scaleX(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .start()
            }
            // Fade in the stroke
            strokeWidth = context.dpToPx(1)
        }
        start()
    }
}

/**
 * Reverses the completion transition back to the default card surface.
 * Background #1B5E20 → #1E1E1E, stroke fades, border accent collapses.
 */
fun MaterialCardView.animateToUncompleted(context: Context) {
    val fromColor = ContextCompat.getColor(context, R.color.habit_completed_background)
    val toColor = ContextCompat.getColor(context, R.color.md_theme_dark_surface)

    // Collapse left border accent first
    val border = findViewById<View>(R.id.completedBorderAccent)
    border?.animate()
        ?.scaleX(0f)
        ?.setDuration(150)
        ?.withEndAction { border.visibility = View.INVISIBLE }
        ?.start()

    strokeWidth = 0

    ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
        duration = 250
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            setCardBackgroundColor(animator.animatedValue as Int)
        }
        start()
    }
}

// ════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ════════════════════════════════════════════════════════════════════════════

/** dp → px  (Int version) */
fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density + 0.5f).toInt()

fun Int.dpToPx(ctx: Context): Int =
    (this * ctx.resources.displayMetrics.density + 0.5f).toInt()

/** dp → px  (Float version, for translations/pivots) */
fun Float.dpF(context: Context): Float =
    this * context.resources.displayMetrics.density

/**
 * Convenience wrapper to attach a doOnEnd listener to a ValueAnimator
 * without importing AnimatorListenerAdapter each time.
 */
fun ValueAnimator.doOnEnd(action: () -> Unit): ValueAnimator {
    addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = action()
    })
    return this
}

/**
 * Same doOnEnd helper for AnimatorSet.
 */
fun AnimatorSet.doOnEnd(action: () -> Unit): AnimatorSet {
    addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = action()
    })
    return this
}