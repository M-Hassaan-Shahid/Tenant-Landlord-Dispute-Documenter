package com.example.tenant_landlorddisputedocumenter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidationTest {

    @Test
    fun validateEmail_rejectsBlankAndInvalid() {
        assertNotNull(InputValidation.validateEmail(""))
        assertNotNull(InputValidation.validateEmail("not-an-email"))
        assertNull(InputValidation.validateEmail("user@example.com"))
    }

    @Test
    fun validatePassword_requiresSixCharsAndMatch() {
        assertNotNull(InputValidation.validatePassword("123", "123"))
        assertNotNull(InputValidation.validatePassword("123456", "654321"))
        assertNull(InputValidation.validatePassword("123456", "123456"))
    }

    @Test
    fun validatePhone_requiresValidFormat() {
        assertNotNull(InputValidation.validatePhone(""))
        assertNotNull(InputValidation.validatePhone("abc"))
        assertNull(InputValidation.validatePhone("0300-1234567"))
    }

    @Test
    fun validateCnic_requiresPakistaniFormat() {
        assertNotNull(InputValidation.validateCnic(""))
        assertNotNull(InputValidation.validateCnic("123456789"))
        assertNull(InputValidation.validateCnic("12345-1234567-1"))
    }

    @Test
    fun validateFinancialAmount_rejectsInvalidAndZero() {
        assertNull(InputValidation.validateFinancialAmount("0", "Rent"))
        assertNull(InputValidation.validateFinancialAmount("abc", "Rent"))
        assertEquals(1500.0, InputValidation.validateFinancialAmount("1500", "Rent")!!, 0.0)
    }

    @Test
    fun trimToMax_limitsLength() {
        assertEquals(500, InputValidation.trimToMax("x".repeat(600)).length)
    }
}
