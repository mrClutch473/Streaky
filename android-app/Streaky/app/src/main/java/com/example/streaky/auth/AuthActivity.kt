package com.example.streaky.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.streaky.MainActivity
import com.example.streaky.databinding.ActivityAuthBinding
import com.google.android.material.tabs.TabLayoutMediator

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Если пользователь уже авторизован — сразу в MainActivity
        if (UserSession.isLoggedIn) {
            goToMain()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = AuthPagerAdapter(this)
        binding.authViewPager.adapter = adapter
        binding.authViewPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.authTabLayout, binding.authViewPager) { tab, position ->
            tab.text = if (position == 0) "Войти" else "Регистрация"
        }.attach()
    }

    /** Переход в MainActivity с очисткой back stack. */
    fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}