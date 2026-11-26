package com.TrustDecision.TDBehaviorTestDemo

internal object FormValidator {
    fun login(account: String, password: String, privacyAccepted: Boolean): String? {
        if (account.isBlank()) return "Enter your account."
        if (password.isBlank()) return "Enter your password."
        if (!privacyAccepted) return "Read and agree to the Privacy Policy first."
        return null
    }

    fun registration(
        account: String,
        password: String,
        confirmation: String,
        privacyAccepted: Boolean
    ): String? {
        if (account.isBlank()) return "Enter your account."
        if (password.isBlank()) return "Enter your password."
        if (confirmation.isBlank()) return "Confirm your password."
        if (password != confirmation) return "Passwords do not match."
        if (!privacyAccepted) return "Read and agree to the Privacy Policy first."
        return null
    }
}
