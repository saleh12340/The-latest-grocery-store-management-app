package com.saleh.alazzi.grocery.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyMathTest {
    @Test fun calculatesLineTotalFromThousandthQuantity() {
        assertEquals(375L, MoneyMath.lineTotal(1_500, 250))
    }

    @Test fun roundsHalfUpWithoutFloatingPoint() {
        assertEquals(2L, MoneyMath.lineTotal(500, 3))
    }

    @Test fun derivesUnitPriceFromEnteredLineTotalExactly() {
        assertEquals(2_000L, MoneyMath.unitPriceFromLineTotal(4_000L, 2_000L))
        assertEquals(250L, MoneyMath.unitPriceFromLineTotal(375L, 1_500L))
    }

    @Test fun rejectsLineTotalsThatCannotProduceWholeRialUnitPrice() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.unitPriceFromLineTotal(1L, 3L)
        }
    }

    @Test fun calculatesDiscountedTotal() {
        assertEquals(950L, MoneyMath.total(listOf(400L, 700L), 150L))
    }

    @Test fun rejectsDiscountGreaterThanSubtotal() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.total(listOf(100L), 101L)
        }
    }

    @Test fun calculatesRemainingAndRejectsOverpayment() {
        assertEquals(250L, MoneyMath.remaining(1_000L, 750L))
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.remaining(100L, 101L)
        }
    }

    @Test fun weightedAverageCostUsesExactIntegerArithmetic() {
        assertEquals(80L, MoneyMath.weightedAverageCost(1_000, 60, 1_000, 100))
    }

    @Test fun balanceArithmeticIsChecked() {
        assertEquals(1_250L, MoneyMath.addBalance(1_000L, 250L))
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.reduceBalance(100L, 101L)
        }
    }
}
