package com.example.streaky

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.streaky.adapter.HabitsAdapter
import com.example.streaky.databinding.ActivityMainBinding
import com.example.streaky.dialog.AddHabitDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import com.example.streaky.network.RetrofitClient
import com.example.streaky.network.toHabit
import kotlinx.coroutines.launch
import com.example.streaky.network.CreateHabitRequest
import androidx.activity.result.contract.ActivityResultContracts

import com.example.streaky.model.Habit

class MainActivity : AppCompatActivity() {

    // ── ViewBinding ──────────────────────────────────────────────────────────
    private lateinit var binding: ActivityMainBinding

    // ── Adapter (replace Habit with your domain model) ───────────────────────
    private lateinit var habitsAdapter: HabitsAdapter

    // ── FAB scroll state ─────────────────────────────────────────────────────
    private var isFabVisible = true
    private var fabHideAnimator: AnimatorSet? = null
    private var fabShowAnimator: AnimatorSet? = null

    private val statsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Привычка была удалена — перезагружаем список
            loadHabits()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge (status bar transparent, content draws behind it)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupDateText()
        setupRecyclerView()
        setupFab()
        scheduleFabBounceIn()

        // Initial data load — replace with ViewModel observation in production
        loadHabits()
    }

    // ════════════════════════════════════════════════════════════════════════
    // WINDOW & INSETS
    // ════════════════════════════════════════════════════════════════════════

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Let AppBarLayout handle top inset via fitsSystemWindows=true;
            // push FAB above the navigation bar
            binding.addHabitFab.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = 24.dp + systemBars.bottom
            }
            insets
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TOOLBAR
    // ════════════════════════════════════════════════════════════════════════

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Начальное состояние — toolbarContent скрыт
        binding.toolbarContent.alpha = 0f
        binding.toolbarContent.visibility = View.INVISIBLE

        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val total = appBarLayout.totalScrollRange
            if (total == 0) return@addOnOffsetChangedListener

            val progress = Math.abs(verticalOffset).toFloat() / total

            // expandedHeader гаснет в первые 70% скролла
            binding.expandedHeader.alpha = (1f - progress / 0.7f).coerceIn(0f, 1f)

            // toolbarContent появляется в последние 30% скролла (70%→100%)
            val collapsedAlpha = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
            binding.toolbarContent.alpha = collapsedAlpha
            binding.toolbarContent.visibility =
                if (collapsedAlpha > 0f) View.VISIBLE else View.INVISIBLE
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATE TEXT
    // ════════════════════════════════════════════════════════════════════════

    private fun setupDateText() {
        val fallback = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
            .format(Date())
            .replaceFirstChar { it.uppercase() }
        binding.currentDateText.text  = fallback
        binding.expandedDateText.text = fallback

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getToday()
                binding.currentDateText.text  = response.dateString
                binding.expandedDateText.text = response.dateString
            } catch (e: Exception) {
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECYCLER VIEW
    // ════════════════════════════════════════════════════════════════════════

    private fun setupRecyclerView() {
        habitsAdapter = HabitsAdapter(
            onHabitClicked  = { habit -> onHabitClicked(habit) },
            onHabitChecked  = { habit, position -> onHabitChecked(habit) },
            onHabitLongPressed = { habit -> onHabitLongPressed(habit) }
        )

        binding.habitsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = habitsAdapter
            // Staggered animation via ItemAnimator extension — see below
            addOnScrollListener(buildScrollListener())
            // Disable default change animations for smoother updates
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
                ?.supportsChangeAnimations = false
        }
    }

    /**
     * Staggered entrance: when the adapter submits a new list, each visible
     * item animates in with a [STAGGER_DELAY_MS * position] start offset.
     *
     * Called after the adapter's submitList() DiffUtil callback completes.
     */
    fun runStaggeredEntrance() {
        val recycler = binding.habitsRecyclerView
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        for (i in firstVisible..lastVisible) {
            val itemView = layoutManager.findViewByPosition(i) ?: continue
            val delay = (i - firstVisible) * STAGGER_DELAY_MS

            itemView.alpha = 0f
            itemView.translationY = 40f.dp(this)

            // Build per-item AnimatorSet
            val fadeIn = ObjectAnimator.ofFloat(itemView, View.ALPHA, 0f, 1f).apply {
                duration = ITEM_ANIM_DURATION_MS
                interpolator = DECELERATE
            }
            val slideUp = ObjectAnimator.ofFloat(itemView, View.TRANSLATION_Y, 40f.dp(this), 0f).apply {
                duration = ITEM_ANIM_DURATION_MS
                interpolator = DECELERATE
            }
            AnimatorSet().apply {
                playTogether(fadeIn, slideUp)
                startDelay = delay
                start()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // FAB SETUP
    // ════════════════════════════════════════════════════════════════════════

    private fun setupFab() {
        // Pre-load the show/hide AnimatorSets
        fabHideAnimator = buildFabHideAnimator()
        fabShowAnimator = buildFabShowAnimator()

        binding.addHabitFab.setOnClickListener {
            openAddHabitSheet()
        }
    }

    /**
     * Bounce-in on first launch:
     * Scale 0→1 with OvershootInterpolator + fade, after 300ms delay.
     */
    private fun scheduleFabBounceIn() {
        // FAB starts invisible (scaleX=0, scaleY=0, alpha=0 set in XML)
        binding.addHabitFab.postDelayed({
            binding.addHabitFab
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(OvershootInterpolator(2.0f))
                .withStartAction {
                    binding.addHabitFab.visibility = View.VISIBLE
                }
                .start()
        }, FAB_LAUNCH_DELAY_MS)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FAB HIDE / SHOW ANIMATORS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Build the hide AnimatorSet (slide down + fade out).
     * Uses fast_out_linear_in for an abrupt, snappy exit.
     */
    private fun buildFabHideAnimator(): AnimatorSet {
        val fab = binding.addHabitFab
        val slideDown = ObjectAnimator.ofFloat(fab, View.TRANSLATION_Y, 0f, fab.height + 24f.dp(this)).apply {
            duration = 250
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        val fadeOut = ObjectAnimator.ofFloat(fab, View.ALPHA, 1f, 0f).apply {
            duration = 200
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        return AnimatorSet().apply {
            playTogether(slideDown, fadeOut)
        }
    }

    /**
     * Build the show AnimatorSet (slide up + fade in).
     * Uses linear_out_slow_in for a smooth, decelerating entrance.
     */
    private fun buildFabShowAnimator(): AnimatorSet {
        val fab = binding.addHabitFab
        val slideUp = ObjectAnimator.ofFloat(fab, View.TRANSLATION_Y, fab.translationY, 0f).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        val fadeIn = ObjectAnimator.ofFloat(fab, View.ALPHA, fab.alpha, 1f).apply {
            duration = 250
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        return AnimatorSet().apply {
            playTogether(slideUp, fadeIn)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCROLL LISTENER — FAB visibility
    // ════════════════════════════════════════════════════════════════════════

    /**
     * RecyclerView scroll listener that:
     *  • hides the FAB when scrolling DOWN  (dy > SCROLL_THRESHOLD)
     *  • shows the FAB when scrolling UP    (dy < -SCROLL_THRESHOLD)
     *
     * A threshold prevents jittery toggling on tiny scroll deltas.
     */
    private fun buildScrollListener(): RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            private var accumulatedDy = 0

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                accumulatedDy += dy

                if (accumulatedDy > SCROLL_HIDE_THRESHOLD && isFabVisible) {
                    hideFab()
                    accumulatedDy = 0
                } else if (accumulatedDy < -SCROLL_SHOW_THRESHOLD && !isFabVisible) {
                    showFab()
                    accumulatedDy = 0
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // Always show FAB when list comes to rest at the top
                if (newState == RecyclerView.SCROLL_STATE_IDLE
                    && !recyclerView.canScrollVertically(-1)
                    && !isFabVisible
                ) {
                    showFab()
                }
            }
        }

    private fun hideFab() {
        if (!isFabVisible) return
        isFabVisible = false
        fabShowAnimator?.cancel()

        // Rebuild hide animator with fresh translationY in case it changed
        fabHideAnimator = buildFabHideAnimator()
        fabHideAnimator?.start()
    }

    private fun showFab() {
        if (isFabVisible) return
        isFabVisible = true
        fabHideAnimator?.cancel()

        // Rebuild show animator to start from current position
        fabShowAnimator = buildFabShowAnimator()
        fabShowAnimator?.start()
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMPTY STATE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Show the empty state with a gentle fade + scale animation.
     * Call this when the habits list becomes empty.
     */
    private fun showEmptyState() {
        val emptyView = binding.emptyStateLayout
        if (emptyView.visibility == View.VISIBLE) return

        emptyView.alpha = 0f
        emptyView.scaleX = 0.95f
        emptyView.scaleY = 0.95f
        emptyView.visibility = View.VISIBLE

        emptyView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Hide the RecyclerView (cross-fade optional)
        binding.habitsRecyclerView
            .animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { binding.habitsRecyclerView.visibility = View.GONE }
            .start()
    }

    /**
     * Hide the empty state and reveal the RecyclerView.
     * Call this when the first habit is added.
     */
    private fun hideEmptyState() {
        val emptyView = binding.emptyStateLayout
        if (emptyView.visibility == View.GONE) return

        emptyView.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                emptyView.visibility = View.GONE
                emptyView.alpha = 0f
                emptyView.scaleX = 1f
                emptyView.scaleY = 1f
            }
            .start()

        binding.habitsRecyclerView.alpha = 0f
        binding.habitsRecyclerView.visibility = View.VISIBLE
        binding.habitsRecyclerView
            .animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    /**
     * Central function to update EmptyState visibility based on list size.
     * Plug this into your ViewModel observer:
     *
     *   viewModel.habits.observe(this) { habits ->
     *       updateEmptyState(habits.isEmpty())
     *       habitsAdapter.submitList(habits) {
     *           if (habits.isNotEmpty()) runStaggeredEntrance()
     *       }
     *   }
     */
    fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) showEmptyState() else hideEmptyState()
    }

    private fun loadHabits() {
        lifecycleScope.launch {
            try {
                val dtos = RetrofitClient.apiService.getHabits()

                // 👇 ВРЕМЕННЫЙ ЛОГ — смотри в Logcat по тегу "STREAKY_DEBUG"
                dtos.forEach { dto ->
                    android.util.Log.d("STREAKY_DEBUG",
                        "Habit: ${dto.name} | streak=${dto.streak} | completed_today=${dto.completedToday}"
                    )
                }

                val habits = dtos.map { it.toHabit() }
                updateEmptyState(habits.isEmpty())
                habitsAdapter.submitList(habits) {
                    if (habits.isNotEmpty()) runStaggeredEntrance()
                }
            } catch (e: Exception) {
                updateEmptyState(true)
                android.util.Log.e("MainActivity", "loadHabits failed: ${e.message}")
            }
        }
    }

    private fun onHabitClicked(habit: Habit) {
        val intent = Intent(this, StatsActivity::class.java).apply {
            putExtra(StatsActivity.EXTRA_HABIT_ID, habit.id)
            putExtra(StatsActivity.EXTRA_HABIT_NAME, habit.name)
            putExtra(StatsActivity.EXTRA_HABIT_EMOJI, habit.emoji)
        }
        statsLauncher.launch(intent)
    }

    private fun onHabitChecked(habit: Habit) {
        val isCompleting = !habit.isCompletedToday

        val optimisticList = habitsAdapter.currentList.map { item ->
            if (item.id == habit.id) item.copy(isCompletedToday = isCompleting)
            else item
        }
        habitsAdapter.submitList(optimisticList)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.toggleComplete(habit.id)
                val updatedList = habitsAdapter.currentList.map { item ->
                    if (item.id == habit.id) item.copy(
                        isCompletedToday = response.completedToday,
                        streakDays       = response.streak
                    ) else item
                }
                habitsAdapter.submitList(updatedList)
            } catch (e: Exception) {
                val rollback = habitsAdapter.currentList.map {
                    if (it.id == habit.id) habit else it
                }
                habitsAdapter.submitList(rollback)
                android.util.Log.e("MainActivity", "toggleComplete failed: ${e.message}")
            }
        }
    }

    private fun onHabitLongPressed(habit: Habit) {
        // TODO: open edit bottom sheet
    }

    private fun openAddHabitSheet() {
        AddHabitDialog().also { dialog ->
            dialog.onHabitCreated = { name, icon ->
                createHabit(name, icon)
            }
        }.show(supportFragmentManager, AddHabitDialog.TAG)
    }

    private fun createHabit(name: String, icon: String) {
        lifecycleScope.launch {
            try {
                RetrofitClient.apiService.createHabit(
                    CreateHabitRequest(name = name, icon = icon)
                )
                // После создания — перезагружаем список
                loadHabits()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "createHabit failed: ${e.message}")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ════════════════════════════════════════════════════════════════════════

    companion object {
        private const val FAB_LAUNCH_DELAY_MS = 300L
        private const val STAGGER_DELAY_MS = 50L
        private const val ITEM_ANIM_DURATION_MS = 350L
        private const val SCROLL_HIDE_THRESHOLD = 20  // px
        private const val SCROLL_SHOW_THRESHOLD = 10  // px

        private val DECELERATE = android.view.animation.DecelerateInterpolator()
    }
}

// ════════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ════════════════════════════════════════════════════════════════════════════

/** Converts an Int dp value to pixels using the display density. */
val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density + 0.5f).toInt()

/** Converts a Float dp value to pixels. Requires Context for density. */
fun Float.dp(context: android.content.Context): Float =
    this * context.resources.displayMetrics.density

/**
 * Safe cast for updateLayoutParams — avoids unchecked cast warning.
 */
inline fun <reified T : android.view.ViewGroup.LayoutParams> View.updateLayoutParams(
    block: T.() -> Unit
) {
    val params = layoutParams as? T ?: return
    block(params)
    layoutParams = params
}