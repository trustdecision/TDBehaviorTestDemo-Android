package com.example.tdbehaviortestdemo_android

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun loginRequiresEveryFieldAndPrivacyConsent() {
        assertEquals("Enter your account.", FormValidator.login("", "secret", true))
        assertEquals("Enter your password.", FormValidator.login("lee", "", true))
        assertEquals(
            "Read and agree to the Privacy Policy first.",
            FormValidator.login("lee", "secret", false)
        )
        assertNull(FormValidator.login("lee", "secret", true))
    }

    @Test
    fun registrationRequiresMatchingPasswords() {
        assertEquals(
            "Passwords do not match.",
            FormValidator.registration("lee", "secret", "different", true)
        )
        assertNull(FormValidator.registration("lee", "secret", "secret", true))
    }
}
