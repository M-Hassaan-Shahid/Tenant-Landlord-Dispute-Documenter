package com.example.tenant_landlorddisputedocumenter.util

import java.security.SecureRandom

/**
 * Generates 6-character invite codes used to link tenants to properties.
 *
 * Alphabet drops similar-looking glyphs (0/O, 1/I/L) to reduce mistakes when typed by hand.
 */
object InviteCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 6
    private val rng = SecureRandom()

    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) }
    }

    /** Canonicalize user input: trim, uppercase, drop spaces. */
    fun normalize(raw: String): String = raw.trim().uppercase().filter { it.isLetterOrDigit() }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}
