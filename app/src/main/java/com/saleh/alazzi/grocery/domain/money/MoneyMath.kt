package com.saleh.alazzi.grocery.domain.money

import java.math.BigInteger

/**
 * Monetary values are stored as whole Yemeni rials (Long), never Float/Double.
 * Quantities are stored in thousandths (e.g. 1.250 units = 1250).
 */
object MoneyMath {
    private val THOUSAND = BigInteger.valueOf(1_000L)

    fun lineTotal(quantityMilli: Long, unitPrice: Long): Long {
        require(quantityMilli > 0) { "Quantity must be positive" }
        require(unitPrice >= 0) { "Unit price cannot be negative" }
        val product = BigInteger.valueOf(quantityMilli).multiply(BigInteger.valueOf(unitPrice))
        val rounded = product.add(THOUSAND.divide(BigInteger.TWO))
            .divide(THOUSAND)
        return rounded.longValueExact()
    }

    fun total(lines: Iterable<Long>, discount: Long): Long {
        require(discount >= 0) { "Discount cannot be negative" }
        val subtotal = lines.fold(BigInteger.ZERO) { acc, value ->
            require(value >= 0) { "Line total cannot be negative" }
            acc.add(BigInteger.valueOf(value))
        }
        val finalValue = subtotal.subtract(BigInteger.valueOf(discount))
        require(finalValue.signum() >= 0) { "Discount exceeds subtotal" }
        return finalValue.longValueExact()
    }

    fun remaining(total: Long, paid: Long): Long {
        require(total >= 0 && paid >= 0) { "Amounts cannot be negative" }
        require(paid <= total) { "Paid amount exceeds invoice total" }
        return Math.subtractExact(total, paid)
    }

    fun addBalance(current: Long, increase: Long): Long {
        require(current >= 0 && increase >= 0) { "Balances and increases cannot be negative" }
        return Math.addExact(current, increase)
    }

    fun reduceBalance(current: Long, reduction: Long): Long {
        require(current >= 0 && reduction >= 0) { "Balances and reductions cannot be negative" }
        require(reduction <= current) { "Reduction exceeds balance" }
        return Math.subtractExact(current, reduction)
    }
    fun weightedAverageCost(oldQuantityMilli: Long, oldUnitCost: Long, newQuantityMilli: Long, newUnitCost: Long): Long {
        require(oldQuantityMilli >= 0 && newQuantityMilli > 0)
        require(oldUnitCost >= 0 && newUnitCost >= 0)
        val oldValue = BigInteger.valueOf(oldQuantityMilli).multiply(BigInteger.valueOf(oldUnitCost))
        val newValue = BigInteger.valueOf(newQuantityMilli).multiply(BigInteger.valueOf(newUnitCost))
        val quantity = BigInteger.valueOf(Math.addExact(oldQuantityMilli, newQuantityMilli))
        return oldValue.add(newValue).add(quantity.divide(BigInteger.TWO))
            .divide(quantity).longValueExact()
    }

}
