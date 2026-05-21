package com.example.streaky.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.streaky.databinding.FragmentLoginBinding
import com.example.streaky.network.LoginRequest
import com.example.streaky.network.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputListeners()
        setupLoginButton()
        setupForgotPassword()
    }

    // ─── Input listeners ──────────────────────────────────────────────────────

    private fun setupInputListeners() {
        // Скрываем ошибку при вводе
        binding.etLoginEmail.doAfterTextChanged { hideError() }
        binding.etLoginPassword.doAfterTextChanged { hideError() }
    }

    // ─── Login button ─────────────────────────────────────────────────────────

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener {
            val email    = binding.etLoginEmail.text?.toString()?.trim() ?: ""
            val password = binding.etLoginPassword.text?.toString() ?: ""

            if (!validate(email, password)) return@setOnClickListener

            performLogin(email, password)
        }
    }

    private fun validate(email: String, password: String): Boolean {
        if (email.isBlank()) {
            showError("Введите электронную почту")
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Некорректный формат email")
            return false
        }
        if (password.isBlank()) {
            showError("Введите пароль")
            return false
        }
        return true
    }

    private fun performLogin(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val user = RetrofitClient.apiService.login(
                    LoginRequest(email = email, password = password)
                )
                // Сохраняем сессию и переходим в главный экран
                UserSession.save(id = user.id, email = user.email)
                (requireActivity() as AuthActivity).goToMain()

            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401  -> "Неверный email или пароль"
                    404  -> "Пользователь не найден"
                    else -> "Ошибка сервера: ${e.code()}"
                }
                showError(message)
            } catch (e: Exception) {
                showError("Нет соединения с сервером")
            } finally {
                setLoading(false)
            }
        }
    }

    // ─── Forgot password ──────────────────────────────────────────────────────

    private fun setupForgotPassword() {
        binding.btnForgotPassword.setOnClickListener {
            // TODO: реализовать восстановление пароля
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Входим…" else "Войти"
        binding.etLoginEmail.isEnabled    = !isLoading
        binding.etLoginPassword.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.loginErrorCard.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.loginErrorCard.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}