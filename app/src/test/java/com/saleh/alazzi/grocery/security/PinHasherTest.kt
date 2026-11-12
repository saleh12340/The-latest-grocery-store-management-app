package com.saleh.alazzi.grocery.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun verifiesCorrectPinAndRejectsWrongPin() {
        val credential = PinHasher.create("2580".toCharArray())
        assertTrue(PinHasher.verify("2580".toCharArray(), credential))
        assertFalse(PinHasher.verify("2581".toCharArray(), credential))
    }

    @Test fun usesDifferentSaltForEachCredential() {
        val first = PinHasher.create("1234".toCharArray())
        val second = PinHasher.create("1234".toCharArray())
        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
    }

    @Test fun rejectsWeakOrMalformedPins() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PinHasher.create("123".toCharArray())
        }
        assertFalse(PinHasher.verify("12a4".toCharArray(), PinHasher.create("1234".toCharArray())))
    }
}
