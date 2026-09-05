package com.globalcall.app.data

import java.security.MessageDigest

object PhoneDirectory {
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.startsWith("+")) { "Use international format, for example +8801... or +9665..." }
        val digits = trimmed.drop(1).filter(Char::isDigit)
        require(digits.length in 8..15) { "Enter a valid phone number with country code" }
        return "+$digits"
    }

    fun key(phoneE164: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(phoneE164.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun last4(phoneE164: String): String = phoneE164.filter(Char::isDigit).takeLast(4)
}
