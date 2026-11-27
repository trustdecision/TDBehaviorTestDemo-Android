package com.TrustDecision.TDBehaviorTestDemo

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : BaseAuthActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val resettingUi = intent.getBooleanExtra(ResetActivity.EXTRA_RESET_UI, false)

        // 重置启动必须忽略 Android 保存的 View hierarchy，防止 EditText、CheckBox
        // 在新 Activity 创建时又从 savedInstanceState 恢复旧值。
        super.onCreate(if (resettingUi) null else savedInstanceState)
        setContentView(R.layout.activity_login)
        preventInitialInputFocus()

        val account = findViewById<EditText>(R.id.account_input)
        val password = findViewById<EditText>(R.id.password_input)
        val privacy = findViewById<CheckBox>(R.id.privacy_checkbox)
        val login = findViewById<Button>(R.id.login_button)
        val register = findViewById<Button>(R.id.register_button)
        val ticketRush = findViewById<Button>(R.id.ticket_rush_button)

        if (resettingUi) {
            account.text?.clear()
            password.text?.clear()
            resetPrivacyAgreement(privacy)
            intent.removeExtra(ResetActivity.EXTRA_RESET_UI)
            preventInitialInputFocus()
        }

        configurePrivacyAgreement(privacy)
        showSdkStatus(findViewById<TextView>(R.id.sdk_status_text))
        register.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        ticketRush.setOnClickListener {
            startActivity(Intent(this, TicketRushActivity::class.java))
        }

        fun attemptLogin() {
            val validation = FormValidator.login(
                account.text.toString(),
                password.text.toString(),
                privacy.isChecked
            )
            if (validation != null) {
                Toast.makeText(this, validation, Toast.LENGTH_SHORT).show()
                return
            }
            submitRiskCheck(login, "Login successful · Risk result", register, ticketRush)
        }

        login.setOnClickListener { attemptLogin() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

}
