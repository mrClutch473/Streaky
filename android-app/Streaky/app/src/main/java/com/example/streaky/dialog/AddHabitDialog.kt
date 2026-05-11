package com.example.streaky.dialog

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.streaky.databinding.DialogAddHabitBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

import com.example.streaky.R
import com.google.android.material.R as MaterialR

class AddHabitDialog : BottomSheetDialogFragment() {

    // ── Public callback (SAM-style lambda, no interface boilerplate) ──────────
    var onHabitCreated: ((name: String, icon: String) -> Unit)? = null

    // ── ViewBinding ───────────────────────────────────────────────────────────
    private var _binding: DialogAddHabitBinding? = null
    private val binding get() = _binding!!

    // ── State ─────────────────────────────────────────────────────────────────
    private val emojis = listOf(
        "💧", "📚", "🏃", "💪", "🧘", "🎯",
        "🍎", "✍️", "🎸", "💤", "🚴", "🧹"
    )
    private var selectedIndex = 0
    private val iconCards = mutableListOf<MaterialCardView>()

    // ── Cached colours (initialised lazily after context available) ───────────
    private val colorCardDefault by lazy { Color.parseColor("#2A2A2A") }
    private val colorCardSelected by lazy { Color.parseColor("#1B5E20") }
    private val colorAccent       by lazy { Color.parseColor("#4CAF50") }
    private val colorBtnDisabled  by lazy { Color.parseColor("#2A2A2A") }
    private val colorTextPrimary  by lazy { Color.parseColor("#FFFFFF") }
    private val colorTextSecond   by lazy { Color.parseColor("#9E9E9E") }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        const val TAG = "AddHabitDialog"
        fun newInstance() = AddHabitDialog()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureBottomSheet()
        buildEmojiIcons()
        setupTextInput()
        setupAddButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bottom Sheet configuration
    // ─────────────────────────────────────────────────────────────────────────

    private fun configureBottomSheet() {
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val sheetView = bottomSheetDialog.findViewById<View>(
            MaterialR.id.design_bottom_sheet
        ) ?: return

        // Remove default white background injected by the dialog theme
        sheetView.setBackgroundColor(Color.TRANSPARENT)

        BottomSheetBehavior.from(sheetView).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
            isDraggable = true

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(v: View, newState: Int) {}
                override fun onSlide(v: View, slideOffset: Float) {}
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Build emoji icon cards programmatically
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildEmojiIcons() {
        val container = binding.iconsContainer
        container.removeAllViews()
        iconCards.clear()

        val cardSizePx = requireContext().dpToPx(52)
        val gapPx = requireContext().dpToPx(10)

        emojis.forEachIndexed { index, emoji ->
            val card = buildIconCard(emoji, index)

            // Gap between cards (no gap before first)
            val params = LinearLayout.LayoutParams(cardSizePx, cardSizePx).apply {
                if (index > 0) marginStart = gapPx
            }
            container.addView(card, params)
            iconCards.add(card)

            // Initial state: all invisible and at scale 0 — staggered reveal later
            card.alpha = 0f
            card.scaleX = 0f
            card.scaleY = 0f
        }

        // Pre-select first icon visually (no animation on build)
        applySelectedStyle(iconCards[0], animated = false)

        // Staggered appear animation fires after layout pass
        container.post { playStaggeredIconsEntrance() }
    }

    /** Inflate a single 52×52dp MaterialCardView for a given emoji + index. */
    private fun buildIconCard(emoji: String, index: Int): MaterialCardView {
        val ctx = requireContext()
        val cornerRadiusPx = ctx.dpToPx(14).toFloat()

        return MaterialCardView(ctx).apply {
            radius = cornerRadiusPx
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(colorCardDefault)
            isClickable = true
            isFocusable = true

            // Emoji label
            val tv = TextView(ctx).apply {
                text = emoji
                textSize = 26f
                gravity = Gravity.CENTER
            }
            addView(
                tv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            // Click → select this icon
            setOnClickListener { onIconSelected(index) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Text input & validation
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupTextInput() {
        binding.etHabitName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.trim()?.isNotEmpty() == true
                updateButtonState(hasText)
            }
        })

        // Dismiss keyboard + trigger add on IME "Done"
        binding.etHabitName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.btnAddHabit.isEnabled) {
                performAddHabit()
                true
            } else false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Add button
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupAddButton() {
        binding.btnAddHabit.setOnClickListener { performAddHabit() }
    }

    private fun performAddHabit() {
        val name = binding.etHabitName.text?.trim()?.toString() ?: return
        if (name.isBlank()) return

        val icon = emojis[selectedIndex]
        playSuccessPulse {
            onHabitCreated?.invoke(name, icon)
            dismissAllowingStateLoss()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Icon selection logic
    // ─────────────────────────────────────────────────────────────────────────

    private fun onIconSelected(newIndex: Int) {
        if (newIndex == selectedIndex) return

        val prevCard = iconCards[selectedIndex]
        val nextCard = iconCards[newIndex]

        // Deselect previous
        playDeselect(prevCard)

        // Select new
        playSelect(nextCard)

        selectedIndex = newIndex
    }

    /** Apply selected visual style instantly (no animation). */
    private fun applySelectedStyle(card: MaterialCardView, animated: Boolean = true) {
        if (animated) return // handled by playSelect
        card.setCardBackgroundColor(colorCardSelected)
        card.strokeColor = colorAccent
        card.strokeWidth = requireContext().dpToPx(2)
        card.scaleX = 1.1f
        card.scaleY = 1.1f
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Button state (enabled ↔ disabled) with background colour fade
    // ─────────────────────────────────────────────────────────────────────────

    private var isButtonEnabled = false

    private fun updateButtonState(enable: Boolean) {
        if (enable == isButtonEnabled) return
        isButtonEnabled = enable

        binding.btnAddHabit.isEnabled = enable

        // Animated background colour transition #2A2A2A ↔ #4CAF50 (200ms)
        val fromColor = if (enable) colorBtnDisabled else colorAccent
        val toColor   = if (enable) colorAccent       else colorBtnDisabled
        val fromText  = if (enable) colorTextSecond   else colorTextPrimary
        val toText    = if (enable) colorTextPrimary  else colorTextSecond

        ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = 200
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.btnAddHabit.backgroundTintList = ColorStateList.valueOf(color)
            }
            start()
        }

        ValueAnimator.ofObject(ArgbEvaluator(), fromText, toText).apply {
            duration = 200
            addUpdateListener { animator ->
                binding.btnAddHabit.setTextColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ══════════════════════  A N I M A T I O N S  ══════════════════════════
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Staggered scale-in for each emoji card when the sheet opens.
     * Delay per card = index × 30ms.
     */
    private fun playStaggeredIconsEntrance() {
        iconCards.forEachIndexed { i, card ->
            card.animate()
                .scaleX(if (i == selectedIndex) 1.1f else 1.0f)
                .scaleY(if (i == selectedIndex) 1.1f else 1.0f)
                .alpha(1f)
                .setDuration(250)
                .setStartDelay((i * 30).toLong())
                .setInterpolator(OvershootInterpolator(1.6f))
                .withStartAction {
                    // Apply selected card colour at start of its reveal animation
                    if (i == selectedIndex) {
                        card.setCardBackgroundColor(colorCardSelected)
                        card.strokeColor = colorAccent
                        card.strokeWidth = requireContext().dpToPx(2)
                    }
                }
                .start()
        }
    }

    /**
     * Deselect animation for the previously selected card:
     * scale 1.1 → 1.0, background fade to default, stroke fade out — 150ms.
     */
    private fun playDeselect(card: MaterialCardView) {
        // Scale back to 1.0
        card.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Background colour: selected → default
        ValueAnimator.ofObject(ArgbEvaluator(), colorCardSelected, colorCardDefault).apply {
            duration = 150
            addUpdateListener { card.setCardBackgroundColor(it.animatedValue as Int) }
            doOnEnd {
                card.strokeWidth = 0
                card.strokeColor = Color.TRANSPARENT
            }
            start()
        }
    }

    /**
     * Select animation for the newly chosen card:
     * scale 0.85 → 1.15 → 1.1 (spring feel), background fade, stroke fade in — 220ms.
     */
    private fun playSelect(card: MaterialCardView) {
        // Pre-bounce to 0.85 instantly, then spring to 1.1
        card.scaleX = 0.85f
        card.scaleY = 0.85f

        // Background colour transition
        ValueAnimator.ofObject(ArgbEvaluator(), colorCardDefault, colorCardSelected).apply {
            duration = 180
            addUpdateListener { card.setCardBackgroundColor(it.animatedValue as Int) }
            doOnStart {
                card.strokeColor = colorAccent
                card.strokeWidth = requireContext().dpToPx(2)
            }
            start()
        }

        // Spring scale animation for tactile feel
        SpringAnimation(card, DynamicAnimation.SCALE_X, 1.1f).apply {
            spring.stiffness  = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            start()
        }
        SpringAnimation(card, DynamicAnimation.SCALE_Y, 1.1f).apply {
            spring.stiffness  = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            start()
        }
    }

    /**
     * Button press feedback: scale 1.0 → 0.96 → 1.0, 100ms.
     * Called automatically via touch through setOnClickListener — here
     * we add an additional tactile spring on the view itself.
     */
    private fun playButtonPress(card: View) {
        card.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(80)
            .withEndAction {
                card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    /**
     * Success pulse: scale 1.0 → 1.05 → 1.0 on the Add button,
     * then invokes [onComplete] to dismiss the sheet.
     */
    private fun playSuccessPulse(onComplete: () -> Unit) {
        val btn = binding.btnAddHabit
        playButtonPress(btn) // immediate press feel

        AnimatorSet().apply {
            val scaleUpX = ObjectAnimator.ofFloat(btn, View.SCALE_X, 1.0f, 1.05f)
            val scaleUpY = ObjectAnimator.ofFloat(btn, View.SCALE_Y, 1.0f, 1.05f)
            val scaleDownX = ObjectAnimator.ofFloat(btn, View.SCALE_X, 1.05f, 1.0f)
            val scaleDownY = ObjectAnimator.ofFloat(btn, View.SCALE_Y, 1.05f, 1.0f)

            playTogether(scaleUpX, scaleUpY)
            play(scaleDownX).with(scaleDownY).after(scaleUpX)

            duration = 180
            interpolator = AccelerateDecelerateInterpolator()

            doOnEnd { onComplete() }
            startDelay = 80  // brief pause after press feel
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers / Extensions
    // ─────────────────────────────────────────────────────────────────────────

    /** Convert dp to px using this Context's display metrics. */
    private fun Context.dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    // ─────────────────────────────────────────────────────────────────────────
    //  Animator convenience extensions (avoids importing kotlin-coroutines
    //  or lifecycle-ktx solely for these small callbacks)
    // ─────────────────────────────────────────────────────────────────────────

    private inline fun Animator.doOnEnd(
        crossinline action: () -> Unit
    ) = addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = action()
    })

    private inline fun Animator.doOnStart(
        crossinline action: () -> Unit
    ) = addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) = action()
    })

    private inline fun ValueAnimator.doOnEnd(
        crossinline action: () -> Unit
    ) = addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = action()
    })

    private inline fun ValueAnimator.doOnStart(
        crossinline action: () -> Unit
    ) = addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) = action()
    })
}