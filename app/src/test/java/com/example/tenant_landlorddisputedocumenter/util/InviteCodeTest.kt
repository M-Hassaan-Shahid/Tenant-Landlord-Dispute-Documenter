package com.example.tenant_landlorddisputedocumenter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeTest {

    @Test
    fun normalize_strips_non_alphanumeric_and_uppercases() {
        assertEquals("ABC123", InviteCode.normalize("ab-c 123"))
    }

    @Test
    fun valid_requires_six_characters_from_alphabet() {
        assertTrue(InviteCode.isValid("ABC234"))
        assertFalse(InviteCode.isValid("ABC12"))
        assertFalse(InviteCode.isValid("ABC123")) // digit 1 is excluded from alphabet
    }
}
