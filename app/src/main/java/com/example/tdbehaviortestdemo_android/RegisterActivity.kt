package com.example.tdbehaviortestdemo_android

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class RegisterActivity : BaseAuthActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val account = findViewById<EditText>(R.id.account_input)
        val password = findViewById<EditText>(R.id.password_input)
        val confirmation = findViewById<EditText>(R.id.confirm_password_input)
        val privacy = findViewById<CheckBox>(R.id.privacy_checkbox)
        val register = findViewById<Button>(R.id.submit_register_button)
        val back = findViewById<Button>(R.id.back_button)

        configurePrivacyAgreement(privacy)
        showSdkStatus(findViewById<TextView>(R.id.sdk_status_text))
        back.setOnClickListener { finish() }

        fun attemptRegistration() {
            val validation = FormValidator.registration(
                account.text.toString(),
                password.text.toString(),
                confirmation.text.toString(),
                privacy.isChecked
            )
            if (validation != null) {
                Toast.makeText(this, validation, Toast.LENGTH_SHORT).show()
                return
            }
            submitRiskCheck(register, "Registration successful · Risk result", back)
        }

        register.setOnClickListener { attemptRegistration() }
        confirmation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegistration()
                true
            } else {
                false
            }
        }
    }
}
