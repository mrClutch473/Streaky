package com.example.streaky.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.streaky.R
import com.example.streaky.databinding.FragmentRegisterBinding
import com.example.streaky.network.RegisterRequest
import com.example.streaky.network.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputListeners()
        setupRegisterButton()
    }

    // ─── Input listeners ──────────────────────────────────────────────────────

    private fun setupInputListeners() {
        // Обновляем индикатор силы пароля при вводе
        binding.etRegisterPassword.doAfterTextChanged { text ->
            updatePasswordStrength(text?.toString() ?: "")
            validateFormAndToggleButton()
            hideError()
        }

        // Скрываем ошибку при изменении любого поля
        binding.etRegisterEmail.doAfterTextChanged   { hideError(); validateFormAndToggleButton() }
        binding.etRegisterConfirmPassword.doAfterTextChanged {
            hideError()
            validateFormAndToggleButton()
        }
    }

    // ─── Password strength ────────────────────────────────────────────────────

    /**
     * Уровни:
     *   0 — пусто          → все серые, без подписи
     *   1 — слабый         → 1 сегмент красный
     *   2 — средний        → 2 сегмента оранжевых
     *   3 — хороший        → 3 сегмента жёлто-зелёных
     *   4 — надёжный       → 4 сегмента зелёных
     */
    private fun updatePasswordStrength(password: String) {
        val level = calcStrength(password)

        val segments = listOf(
            binding.strengthSeg1,
            binding.strengthSeg2,
            binding.strengthSeg3,
            binding.strengthSeg4
        )

        val (color, label) = when (level) {
            0    -> Pair(R.color.md_theme_dark_surfaceVariant, "")
            1    -> Pair(R.color.streaky_error, "Слабый")
            2    -> Pair(R.color.streaky_warning, "Средний")
            3    -> Pair(R.color.streaky_warning_light, "Хороший")
            else -> Pair(R.color.md_theme_dark_primary, "Надёжный")
        }

        segments.forEachIndexed { index, view ->
            val activeColor = if (index < level) color
            else R.color.md_theme_dark_surfaceVariant
            view.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), activeColor)
        }

        binding.tvPasswordStrengthLabel.text = label
        binding.tvPasswordStrengthLabel.setTextColor(
            if (level == 0) 0 else ContextCompat.getColor(requireContext(), color)
        )
    }

    /**
     * Считает уровень сложности пароля (0–4):
     *   +1 за длину ≥ 6
     *   +1 за длину ≥ 8
     *   +1 за наличие цифр
     *   +1 за наличие спецсимволов
     */
    private fun calcStrength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= 6)                           score++
        if (password.length >= 8)                           score++
        if (password.any { it.isDigit() })                  score++
        if (password.any { !it.isLetterOrDigit() })         score++
        return score.coerceAtLeast(1) // минимум 1 если что-то введено
    }

    // ─── Button enable/disable ────────────────────────────────────────────────

    private fun validateFormAndToggleButton() {
        val email    = binding.etRegisterEmail.text?.toString()?.trim() ?: ""
        val password = binding.etRegisterPassword.text?.toString() ?: ""
        val confirm  = binding.etRegisterConfirmPassword.text?.toString() ?: ""

        val isReady = email.isNotBlank()
                && password.length >= 6
                && confirm.isNotBlank()

        binding.btnRegister.isEnabled = isReady
    }

    // ─── Register button ──────────────────────────────────────────────────────

    private fun setupRegisterButton() {
        binding.btnRegister.setOnClickListener {
            val email    = binding.etRegisterEmail.text?.toString()?.trim() ?: ""
            val password = binding.etRegisterPassword.text?.toString() ?: ""
            val confirm  = binding.etRegisterConfirmPassword.text?.toString() ?: ""

            if (!validate(email, password, confirm)) return@setOnClickListener

            performRegister(email, password)
        }
    }

    private fun validate(email: String, password: String, confirm: String): Boolean {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Некорректный формат email")
            return false
        }
        if (password.length < 6) {
            showError("Пароль должен быть не менее 6 символов")
            return false
        }
        if (password != confirm) {
            showError("Пароли не совпадают")
            return false
        }
        return true
    }

    private fun performRegister(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val user = RetrofitClient.apiService.register(
                    RegisterRequest(email = email, password = password)
                )
                UserSession.save(id = user.id, email = user.email)
                (requireActivity() as AuthActivity).goToMain()

            } catch (e: HttpException) {
                val message = when (e.code()) {
                    400  -> "Пользователь с таким email уже существует"
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

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun setLoading(isLoading: Boolean) {
        binding.btnRegister.isEnabled = !isLoading
        binding.btnRegister.text = if (isLoading) "Создаём аккаунт…" else "Создать аккаунт"
        binding.etRegisterEmail.isEnabled           = !isLoading
        binding.etRegisterPassword.isEnabled        = !isLoading
        binding.etRegisterConfirmPassword.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        binding.tvRegisterError.text = message
        binding.registerErrorCard.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.registerErrorCard.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}