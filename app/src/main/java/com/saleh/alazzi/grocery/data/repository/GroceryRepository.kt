package com.saleh.alazzi.grocery.data.repository

import androidx.room.withTransaction
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.*
import com.saleh.alazzi.grocery.domain.money.MoneyMath
import java.util.UUID

data class InvoiceLineInput(
    val itemId: String,
    val quantityMilli: Long,
    val unitPrice: Long
)

data class CreateInvoiceInput(
    val operationId: String,
    val customerOrSupplierId: String?,
    val lines: List<InvoiceLineInput>,
    val discount: Long = 0,
    val paid: Long,
    val paymentMethod: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class SaveInvoiceResult(val invoiceId: String, val invoiceNumber: String, val wasDuplicate: Boolean)

/**
 * Posts invoice, ledger, stock and cash effects atomically.
 * Callers must persist the same operationId while retrying the same user action.
 */
class GroceryRepository(private val database: GroceryDatabase) {

    suspend fun createSale(input: CreateInvoiceInput): SaveInvoiceResult =
        database.withTransaction {
            require(input.operationId.isNotBlank()) { "معرف العملية مطلوب" }
            require(input.lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
            require(input.lines.map { it.itemId }.distinct().size == input.lines.size) { "ادمج الكميات المكررة للصنف نفسه قبل الحفظ" }
            require(input.paid >= 0) { "المبلغ المدفوع غير صحيح" }

            val invoiceDao = database.salesInvoices()
            invoiceDao.findByOperationId(input.operationId)?.let {
                return@withTransaction SaveInvoiceResult(it.id, it.invoiceNumber, true)
            }

            val prepared = input.lines.map { line ->
                val item = database.items().findById(line.itemId)
                    ?: error("الصنف غير موجود: ${line.itemId}")
                require(item.isActive) { "الصنف غير نشط: ${item.name}" }
                require(line.quantityMilli > 0) { "الكمية يجب أن تكون أكبر من صفر" }
                val lineTotal = MoneyMath.lineTotal(line.quantityMilli, line.unitPrice)
                Triple(line, item, lineTotal)
            }
            val subtotal = prepared.fold(0L) { acc, row -> Math.addExact(acc, row.third) }
            val total = MoneyMath.total(listOf(subtotal), input.discount)
            val remaining = MoneyMath.remaining(total, input.paid)
            if (input.customerOrSupplierId == null) {
                require(remaining == 0L) { "البيع الآجل يتطلب اختيار عميل" }
            }

            val customer = input.customerOrSupplierId?.let {
                database.customers().findById(it) ?: error("العميل غير موجود")
            }
            val invoiceId = UUID.randomUUID().toString()
            val invoiceNumber = (invoiceDao.maxInvoiceNumber() + 1L).toString().padStart(8, '0')
            val lineEntities = prepared.map { (line, item, lineTotal) ->
                SalesInvoiceItemEntity(
                    id = UUID.randomUUID().toString(),
                    invoiceId = invoiceId,
                    itemId = item.id,
                    itemNameSnapshot = item.name,
                    quantityMilli = line.quantityMilli,
                    unitPrice = line.unitPrice,
                    lineTotal = lineTotal
                )
            }

            invoiceDao.insert(
                SalesInvoiceEntity(
                    id = invoiceId,
                    operationId = input.operationId,
                    invoiceNumber = invoiceNumber,
                    customerId = customer?.id,
                    createdAt = input.createdAt,
                    subtotal = subtotal,
                    discount = input.discount,
                    total = total,
                    paid = input.paid,
                    remaining = remaining,
                    paymentMethod = input.paymentMethod,
                    notes = input.notes
                )
            )
            invoiceDao.insertLines(lineEntities)

            prepared.forEachIndexed { index, (line, item, _) ->
                val before = item.quantityMilli
                require(before >= line.quantityMilli) { "المخزون غير كافٍ للصنف: ${item.name}" }
                val after = Math.subtractExact(before, line.quantityMilli)
                check(database.items().setQuantity(item.id, after, input.createdAt) == 1)
                database.stock().insertMovement(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "${input.operationId}:stock:$index",
                        itemId = item.id,
                        movementType = "SALE",
                        quantityInMilli = 0,
                        quantityOutMilli = line.quantityMilli,
                        balanceBeforeMilli = before,
                        balanceAfterMilli = after,
                        sourceType = "SALES_INVOICE",
                        sourceId = invoiceId,
                        createdAt = input.createdAt
                    )
                )
            }

            if (customer != null) {
                var balance = MoneyMath.addBalance(customer.balance, total)
                database.ledger().insertCustomer(
                    CustomerTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "${input.operationId}:sale-ledger",
                        customerId = customer.id,
                        type = "SALE",
                        amount = total,
                        balanceAfter = balance,
                        sourceType = "SALES_INVOICE",
                        sourceId = invoiceId,
                        note = "فاتورة بيع رقم $invoiceNumber",
                        createdAt = input.createdAt
                    )
                )
                if (input.paid > 0) {
                    balance = MoneyMath.reduceBalance(balance, input.paid)
                    database.ledger().insertCustomer(
                        CustomerTransactionEntity(
                            id = UUID.randomUUID().toString(),
                            operationId = "${input.operationId}:payment-ledger",
                            customerId = customer.id,
                            type = "PAYMENT",
                            amount = input.paid,
                            balanceAfter = balance,
                            sourceType = "SALES_INVOICE_PAYMENT",
                            sourceId = invoiceId,
                            note = "دفعة مع فاتورة $invoiceNumber",
                            createdAt = input.createdAt
                        )
                    )
                }
                check(database.customers().setBalance(customer.id, balance, input.createdAt) == 1)
            }

            if (input.paid > 0) {
                database.cash().insert(
                    CashTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        idempotencyKey = "${input.operationId}:cash",
                        direction = "IN",
                        amount = input.paid,
                        sourceType = "SALES_INVOICE",
                        sourceId = invoiceId,
                        paymentMethod = input.paymentMethod,
                        createdAt = input.createdAt
                    )
                )
            }
            database.references().insertActivity(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    actorId = null,
                    action = "CREATE_SALE",
                    entityType = "SALES_INVOICE",
                    entityId = invoiceId,
                    details = "فاتورة $invoiceNumber بقيمة $total",
                    createdAt = input.createdAt
                )
            )
            SaveInvoiceResult(invoiceId, invoiceNumber, false)
        }

    suspend fun createPurchase(input: CreateInvoiceInput): SaveInvoiceResult =
        database.withTransaction {
            require(input.operationId.isNotBlank()) { "معرف العملية مطلوب" }
            require(input.lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
            require(input.lines.map { it.itemId }.distinct().size == input.lines.size) { "ادمج الكميات المكررة للصنف نفسه قبل الحفظ" }
            require(input.paid >= 0) { "المبلغ المدفوع غير صحيح" }

            val invoiceDao = database.purchaseInvoices()
            invoiceDao.findByOperationId(input.operationId)?.let {
                return@withTransaction SaveInvoiceResult(it.id, it.invoiceNumber, true)
            }

            val prepared = input.lines.map { line ->
                val item = database.items().findById(line.itemId)
                    ?: error("الصنف غير موجود: ${line.itemId}")
                require(item.isActive) { "الصنف غير نشط: ${item.name}" }
                require(line.quantityMilli > 0) { "الكمية يجب أن تكون أكبر من صفر" }
                Triple(line, item, MoneyMath.lineTotal(line.quantityMilli, line.unitPrice))
            }
            val subtotal = prepared.fold(0L) { acc, row -> Math.addExact(acc, row.third) }
            val total = MoneyMath.total(listOf(subtotal), input.discount)
            val remaining = MoneyMath.remaining(total, input.paid)
            if (input.customerOrSupplierId == null) {
                require(remaining == 0L) { "الشراء الآجل يتطلب اختيار مورد" }
            }
            val supplier = input.customerOrSupplierId?.let {
                database.suppliers().findById(it) ?: error("المورد غير موجود")
            }
            val invoiceId = UUID.randomUUID().toString()
            val invoiceNumber = (invoiceDao.maxInvoiceNumber() + 1L).toString().padStart(8, '0')
            val lineEntities = prepared.map { (line, item, lineTotal) ->
                PurchaseInvoiceItemEntity(
                    id = UUID.randomUUID().toString(),
                    invoiceId = invoiceId,
                    itemId = item.id,
                    itemNameSnapshot = item.name,
                    quantityMilli = line.quantityMilli,
                    unitCost = line.unitPrice,
                    lineTotal = lineTotal
                )
            }

            invoiceDao.insert(
                PurchaseInvoiceEntity(
                    id = invoiceId,
                    operationId = input.operationId,
                    invoiceNumber = invoiceNumber,
                    supplierId = supplier?.id,
                    createdAt = input.createdAt,
                    subtotal = subtotal,
                    discount = input.discount,
                    total = total,
                    paid = input.paid,
                    remaining = remaining,
                    paymentMethod = input.paymentMethod,
                    notes = input.notes
                )
            )
            invoiceDao.insertLines(lineEntities)

            prepared.forEachIndexed { index, (line, item, _) ->
                val before = item.quantityMilli
                val after = Math.addExact(before, line.quantityMilli)
                check(database.items().setQuantity(item.id, after, input.createdAt) == 1)
                database.stock().insertMovement(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "${input.operationId}:stock:$index",
                        itemId = item.id,
                        movementType = "PURCHASE",
                        quantityInMilli = line.quantityMilli,
                        quantityOutMilli = 0,
                        balanceBeforeMilli = before,
                        balanceAfterMilli = after,
                        sourceType = "PURCHASE_INVOICE",
                        sourceId = invoiceId,
                        createdAt = input.createdAt
                    )
                )
            }

            if (supplier != null) {
                var balance = MoneyMath.addBalance(supplier.balance, total)
                database.ledger().insertSupplier(
                    SupplierTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "${input.operationId}:purchase-ledger",
                        supplierId = supplier.id,
                        type = "PURCHASE",
                        amount = total,
                        balanceAfter = balance,
                        sourceType = "PURCHASE_INVOICE",
                        sourceId = invoiceId,
                        note = "فاتورة شراء رقم $invoiceNumber",
                        createdAt = input.createdAt
                    )
                )
                if (input.paid > 0) {
                    balance = MoneyMath.reduceBalance(balance, input.paid)
                    database.ledger().insertSupplier(
                        SupplierTransactionEntity(
                            id = UUID.randomUUID().toString(),
                            operationId = "${input.operationId}:payment-ledger",
                            supplierId = supplier.id,
                            type = "PAYMENT",
                            amount = input.paid,
                            balanceAfter = balance,
                            sourceType = "PURCHASE_INVOICE_PAYMENT",
                            sourceId = invoiceId,
                            note = "دفعة مع فاتورة $invoiceNumber",
                            createdAt = input.createdAt
                        )
                    )
                }
                check(database.suppliers().setBalance(supplier.id, balance, input.createdAt) == 1)
            }

            if (input.paid > 0) {
                database.cash().insert(
                    CashTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        idempotencyKey = "${input.operationId}:cash",
                        direction = "OUT",
                        amount = input.paid,
                        sourceType = "PURCHASE_INVOICE",
                        sourceId = invoiceId,
                        paymentMethod = input.paymentMethod,
                        createdAt = input.createdAt
                    )
                )
            }
            database.references().insertActivity(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    actorId = null,
                    action = "CREATE_PURCHASE",
                    entityType = "PURCHASE_INVOICE",
                    entityId = invoiceId,
                    details = "فاتورة شراء $invoiceNumber بقيمة $total",
                    createdAt = input.createdAt
                )
            )
            SaveInvoiceResult(invoiceId, invoiceNumber, false)
        }
}
