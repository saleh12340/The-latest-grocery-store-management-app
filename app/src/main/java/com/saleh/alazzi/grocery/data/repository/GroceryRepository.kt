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


data class ReturnLineInput(val originalInvoiceItemId: String, val quantityMilli: Long)
data class SaveReturnResult(val returnId: String, val total: Long, val wasDuplicate: Boolean)

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
                    unitCostSnapshot = item.purchasePrice,
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
                val averageCost = MoneyMath.weightedAverageCost(before, item.purchasePrice, line.quantityMilli, line.unitPrice)
                check(database.items().setQuantityAndPurchasePrice(item.id, after, averageCost, input.createdAt) == 1)
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
    suspend fun settleCustomerBalance(
        operationId: String,
        customerId: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank()) { "معرف التسوية مطلوب" }
        if (database.ledger().findCustomerOperation("$operationId:ledger") != null) return@withTransaction
        val customer = database.customers().findById(customerId) ?: error("العميل غير موجود")
        val settledAmount = customer.balance
        require(settledAmount > 0) { "رصيد العميل صفر ولا يحتاج إلى تسوية" }
        database.ledger().insertCustomer(
            CustomerTransactionEntity(
                id = UUID.randomUUID().toString(), operationId = "$operationId:ledger",
                customerId = customerId, type = "SETTLEMENT", amount = settledAmount,
                balanceAfter = 0L, sourceType = "MANUAL_SETTLEMENT", sourceId = operationId,
                note = note, createdAt = createdAt
            )
        )
        check(database.customers().setBalance(customerId, 0L, createdAt) == 1)
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "CUSTOMER_SETTLEMENT", "CUSTOMER", customerId, "تصفير رصيد العميل: $settledAmount", createdAt)
        )
    }

    suspend fun settleSupplierBalance(
        operationId: String,
        supplierId: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank()) { "معرف التسوية مطلوب" }
        if (database.ledger().findSupplierOperation("$operationId:ledger") != null) return@withTransaction
        val supplier = database.suppliers().findById(supplierId) ?: error("المورد غير موجود")
        val settledAmount = supplier.balance
        require(settledAmount > 0) { "رصيد المورد صفر ولا يحتاج إلى تسوية" }
        database.ledger().insertSupplier(
            SupplierTransactionEntity(
                id = UUID.randomUUID().toString(), operationId = "$operationId:ledger",
                supplierId = supplierId, type = "SETTLEMENT", amount = settledAmount,
                balanceAfter = 0L, sourceType = "MANUAL_SETTLEMENT", sourceId = operationId,
                note = note, createdAt = createdAt
            )
        )
        check(database.suppliers().setBalance(supplierId, 0L, createdAt) == 1)
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "SUPPLIER_SETTLEMENT", "SUPPLIER", supplierId, "تصفير رصيد المورد: $settledAmount", createdAt)
        )
    }

    suspend fun addCustomerDebit(
        operationId: String,
        customerId: String,
        amount: Long,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && amount > 0) { "بيانات الدين غير صحيحة" }
        if (database.ledger().findCustomerOperation("$operationId:ledger") != null) return@withTransaction
        val customer = database.customers().findById(customerId) ?: error("العميل غير موجود")
        val newBalance = MoneyMath.addBalance(customer.balance, amount)
        database.ledger().insertCustomer(
            CustomerTransactionEntity(
                id = UUID.randomUUID().toString(), operationId = "$operationId:ledger",
                customerId = customerId, type = "DEBIT", amount = amount,
                balanceAfter = newBalance, sourceType = "MANUAL_DEBIT", sourceId = operationId,
                note = note, createdAt = createdAt
            )
        )
        check(database.customers().setBalance(customerId, newBalance, createdAt) == 1)
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "CUSTOMER_DEBIT", "CUSTOMER", customerId, "إضافة دين بقيمة $amount", createdAt)
        )
    }

    suspend fun addSupplierDebit(
        operationId: String,
        supplierId: String,
        amount: Long,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && amount > 0) { "بيانات المستحق غير صحيحة" }
        if (database.ledger().findSupplierOperation("$operationId:ledger") != null) return@withTransaction
        val supplier = database.suppliers().findById(supplierId) ?: error("المورد غير موجود")
        val newBalance = MoneyMath.addBalance(supplier.balance, amount)
        database.ledger().insertSupplier(
            SupplierTransactionEntity(
                id = UUID.randomUUID().toString(), operationId = "$operationId:ledger",
                supplierId = supplierId, type = "DEBIT", amount = amount,
                balanceAfter = newBalance, sourceType = "MANUAL_DEBIT", sourceId = operationId,
                note = note, createdAt = createdAt
            )
        )
        check(database.suppliers().setBalance(supplierId, newBalance, createdAt) == 1)
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "SUPPLIER_DEBIT", "SUPPLIER", supplierId, "إضافة مستحق بقيمة $amount", createdAt)
        )
    }

    suspend fun receiveCustomerPayment(
        operationId: String,
        customerId: String,
        amount: Long,
        paymentMethod: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && amount > 0) { "بيانات السداد غير صحيحة" }
        if (database.ledger().findCustomerOperation("$operationId:ledger") != null) return@withTransaction
        val customer = database.customers().findById(customerId) ?: error("العميل غير موجود")
        val newBalance = MoneyMath.reduceBalance(customer.balance, amount)
        database.ledger().insertCustomer(
            CustomerTransactionEntity(
                id = UUID.randomUUID().toString(),
                operationId = "$operationId:ledger",
                customerId = customerId,
                type = "PAYMENT",
                amount = amount,
                balanceAfter = newBalance,
                sourceType = "CUSTOMER_PAYMENT",
                sourceId = operationId,
                note = note,
                createdAt = createdAt
            )
        )
        check(database.customers().setBalance(customerId, newBalance, createdAt) == 1)
        database.cash().insert(
            CashTransactionEntity(
                id = UUID.randomUUID().toString(),
                idempotencyKey = "$operationId:cash",
                direction = "IN",
                amount = amount,
                sourceType = "CUSTOMER_PAYMENT",
                sourceId = customerId,
                paymentMethod = paymentMethod,
                note = note,
                createdAt = createdAt
            )
        )
    }

    suspend fun paySupplier(
        operationId: String,
        supplierId: String,
        amount: Long,
        paymentMethod: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && amount > 0) { "بيانات الدفع غير صحيحة" }
        if (database.ledger().findSupplierOperation("$operationId:ledger") != null) return@withTransaction
        val supplier = database.suppliers().findById(supplierId) ?: error("المورد غير موجود")
        val newBalance = MoneyMath.reduceBalance(supplier.balance, amount)
        database.ledger().insertSupplier(
            SupplierTransactionEntity(
                id = UUID.randomUUID().toString(),
                operationId = "$operationId:ledger",
                supplierId = supplierId,
                type = "PAYMENT",
                amount = amount,
                balanceAfter = newBalance,
                sourceType = "SUPPLIER_PAYMENT",
                sourceId = operationId,
                note = note,
                createdAt = createdAt
            )
        )
        check(database.suppliers().setBalance(supplierId, newBalance, createdAt) == 1)
        database.cash().insert(
            CashTransactionEntity(
                id = UUID.randomUUID().toString(),
                idempotencyKey = "$operationId:cash",
                direction = "OUT",
                amount = amount,
                sourceType = "SUPPLIER_PAYMENT",
                sourceId = supplierId,
                paymentMethod = paymentMethod,
                note = note,
                createdAt = createdAt
            )
        )
    }


    suspend fun recordExpense(
        operationId: String,
        category: String,
        amount: Long,
        paymentMethod: String,
        description: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && category.isNotBlank() && amount > 0) { "بيانات المصروف غير صحيحة" }
        if (database.expenses().findByOperationId(operationId) != null) return@withTransaction
        val id = UUID.randomUUID().toString()
        database.expenses().insert(
            ExpenseEntity(
                id = id,
                operationId = operationId,
                category = category.trim(),
                amount = amount,
                paymentMethod = paymentMethod,
                description = description,
                createdAt = createdAt
            )
        )
        database.cash().insert(
            CashTransactionEntity(
                id = UUID.randomUUID().toString(),
                idempotencyKey = "$operationId:cash",
                direction = "OUT",
                amount = amount,
                sourceType = "EXPENSE",
                sourceId = id,
                paymentMethod = paymentMethod,
                note = description,
                createdAt = createdAt
            )
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = null,
                action = "CREATE_EXPENSE",
                entityType = "EXPENSE",
                entityId = id,
                details = "$category: $amount",
                createdAt = createdAt
            )
        )
    }

    suspend fun recordTransfer(
        operationId: String,
        recipientName: String,
        phone: String,
        direction: String,
        amount: Long,
        fees: Long,
        paymentMethod: String,
        status: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && recipientName.isNotBlank() && amount > 0 && fees >= 0) {
            "بيانات الحوالة غير صحيحة"
        }
        require(direction == "OUT" || direction == "IN") { "اتجاه الحوالة غير صحيح" }
        if (database.transfers().findByOperationId(operationId) != null) return@withTransaction
        val net = if (direction == "OUT") Math.addExact(amount, fees) else {
            require(fees <= amount) { "الرسوم أكبر من مبلغ الحوالة" }
            Math.subtractExact(amount, fees)
        }
        val id = UUID.randomUUID().toString()
        database.transfers().insert(
            TransferEntity(
                id = id,
                operationId = operationId,
                recipientName = recipientName.trim(),
                phone = phone.filter(Char::isDigit),
                direction = direction,
                amount = amount,
                fees = fees,
                netAmount = net,
                status = status,
                note = note,
                createdAt = createdAt
            )
        )
        database.cash().insert(
            CashTransactionEntity(
                id = UUID.randomUUID().toString(),
                idempotencyKey = "$operationId:cash",
                direction = direction,
                amount = net,
                sourceType = "TRANSFER",
                sourceId = id,
                paymentMethod = paymentMethod,
                note = note,
                createdAt = createdAt
            )
        )
    }


    suspend fun returnSale(
        operationId: String,
        invoiceId: String,
        lines: List<ReturnLineInput>,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): SaveReturnResult = database.withTransaction {
        require(operationId.isNotBlank() && lines.isNotEmpty()) { "بيانات المرتجع غير مكتملة" }
        database.returns().findByOperationId(operationId)?.let {
            return@withTransaction SaveReturnResult(it.id, it.total, true)
        }
        val invoice = database.salesInvoices().findById(invoiceId) ?: error("فاتورة البيع غير موجودة")
        require(invoice.status == "POSTED") { "لا يمكن إرجاع فاتورة ملغاة" }
        val originalLines = database.salesInvoices().lines(invoiceId)
        val returnedLines = lines.map { requested ->
            require(requested.quantityMilli > 0) { "كمية المرتجع غير صحيحة" }
            val original = originalLines.firstOrNull { it.id == requested.originalInvoiceItemId }
                ?: error("الصنف غير موجود في الفاتورة الأصلية")
            val alreadyQty = database.returns().returnedQuantity(invoiceId, original.id)
            val remainingQty = Math.subtractExact(original.quantityMilli, alreadyQty)
            require(requested.quantityMilli <= remainingQty) { "الكمية المرتجعة تتجاوز الكمية المتبقية من الفاتورة" }
            val rawAmount = MoneyMath.lineTotal(requested.quantityMilli, original.unitPrice)
            val netLineAmount = MoneyMath.proportionalShare(original.lineTotal, invoice.total, invoice.subtotal)
            val alreadyAmount = database.returns().returnedAmount(invoiceId, original.id)
            val amount = if (requested.quantityMilli == remainingQty) {
                Math.subtractExact(netLineAmount, alreadyAmount)
            } else {
                MoneyMath.proportionalShare(rawAmount, invoice.total, invoice.subtotal)
                    .coerceAtMost(Math.subtractExact(netLineAmount, alreadyAmount))
            }
            Triple(requested, original, amount)
        }
        val total = returnedLines.fold(0L) { sum, row -> Math.addExact(sum, row.third) }
        val returnId = UUID.randomUUID().toString()
        database.returns().insert(
            ReturnEntity(returnId, operationId, "SALE", invoiceId, total, createdAt, note)
        )
        database.returns().insertItems(returnedLines.map { (requested, original, amount) ->
            ReturnItemEntity(
                id = UUID.randomUUID().toString(),
                returnId = returnId,
                originalInvoiceItemId = original.id,
                itemId = original.itemId,
                quantityMilli = requested.quantityMilli,
                amount = amount
            )
        })
        returnedLines.forEachIndexed { index, (requested, original, _) ->
            val item = database.items().findById(original.itemId) ?: error("الصنف المرتجع غير موجود")
            val before = item.quantityMilli
            val after = Math.addExact(before, requested.quantityMilli)
            check(database.items().setQuantity(item.id, after, createdAt) == 1)
            database.stock().insertMovement(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    operationId = "$operationId:stock:$index",
                    itemId = item.id,
                    movementType = "SALE_RETURN",
                    quantityInMilli = requested.quantityMilli,
                    quantityOutMilli = 0,
                    balanceBeforeMilli = before,
                    balanceAfterMilli = after,
                    sourceType = "SALE_RETURN",
                    sourceId = returnId,
                    note = note,
                    createdAt = createdAt
                )
            )
        }
        val customerId = invoice.customerId
        val creditReduction = if (customerId != null) {
            val customer = database.customers().findById(customerId) ?: error("العميل المرتبط غير موجود")
            val reduction = minOf(customer.balance, total)
            val newBalance = MoneyMath.reduceBalance(customer.balance, reduction)
            database.ledger().insertCustomer(
                CustomerTransactionEntity(
                    UUID.randomUUID().toString(), "$operationId:customer",
                    customerId, "RETURN", total, newBalance, "SALE_RETURN", returnId, note, createdAt
                )
            )
            check(database.customers().setBalance(customerId, newBalance, createdAt) == 1)
            reduction
        } else 0L
        val cashRefund = Math.subtractExact(total, creditReduction)
        if (cashRefund > 0) {
            database.cash().insert(
                CashTransactionEntity(
                    UUID.randomUUID().toString(), "$operationId:cash", "OUT", cashRefund,
                    "SALE_RETURN", returnId, "CASH", note, createdAt
                )
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "SALE_RETURN", "RETURN", returnId, "مرتجع بقيمة $total", createdAt)
        )
        SaveReturnResult(returnId, total, false)
    }

    suspend fun returnPurchase(
        operationId: String,
        invoiceId: String,
        lines: List<ReturnLineInput>,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): SaveReturnResult = database.withTransaction {
        require(operationId.isNotBlank() && lines.isNotEmpty()) { "بيانات المرتجع غير مكتملة" }
        database.returns().findByOperationId(operationId)?.let {
            return@withTransaction SaveReturnResult(it.id, it.total, true)
        }
        val invoice = database.purchaseInvoices().findById(invoiceId) ?: error("فاتورة الشراء غير موجودة")
        require(invoice.status == "POSTED") { "لا يمكن إرجاع فاتورة ملغاة" }
        val originalLines = database.purchaseInvoices().lines(invoiceId)
        val returnedLines = lines.map { requested ->
            require(requested.quantityMilli > 0) { "كمية المرتجع غير صحيحة" }
            val original = originalLines.firstOrNull { it.id == requested.originalInvoiceItemId }
                ?: error("الصنف غير موجود في الفاتورة الأصلية")
            val alreadyQty = database.returns().returnedQuantity(invoiceId, original.id)
            val remainingQty = Math.subtractExact(original.quantityMilli, alreadyQty)
            require(requested.quantityMilli <= remainingQty) { "الكمية المرتجعة تتجاوز الكمية المتبقية من الفاتورة" }
            val rawAmount = MoneyMath.lineTotal(requested.quantityMilli, original.unitCost)
            val netLineAmount = MoneyMath.proportionalShare(original.lineTotal, invoice.total, invoice.subtotal)
            val alreadyAmount = database.returns().returnedAmount(invoiceId, original.id)
            val amount = if (requested.quantityMilli == remainingQty) {
                Math.subtractExact(netLineAmount, alreadyAmount)
            } else {
                MoneyMath.proportionalShare(rawAmount, invoice.total, invoice.subtotal)
                    .coerceAtMost(Math.subtractExact(netLineAmount, alreadyAmount))
            }
            Triple(requested, original, amount)
        }
        val total = returnedLines.fold(0L) { sum, row -> Math.addExact(sum, row.third) }
        val returnId = UUID.randomUUID().toString()
        database.returns().insert(
            ReturnEntity(returnId, operationId, "PURCHASE", invoiceId, total, createdAt, note)
        )
        database.returns().insertItems(returnedLines.map { (requested, original, amount) ->
            ReturnItemEntity(
                id = UUID.randomUUID().toString(),
                returnId = returnId,
                originalInvoiceItemId = original.id,
                itemId = original.itemId,
                quantityMilli = requested.quantityMilli,
                amount = amount
            )
        })
        returnedLines.forEachIndexed { index, (requested, original, _) ->
            val item = database.items().findById(original.itemId) ?: error("الصنف المرتجع غير موجود")
            val before = item.quantityMilli
            require(before >= requested.quantityMilli) { "المخزون الحالي لا يكفي لإرجاع الصنف للمورد" }
            val after = Math.subtractExact(before, requested.quantityMilli)
            check(database.items().setQuantity(item.id, after, createdAt) == 1)
            database.stock().insertMovement(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    operationId = "$operationId:stock:$index",
                    itemId = item.id,
                    movementType = "PURCHASE_RETURN",
                    quantityInMilli = 0,
                    quantityOutMilli = requested.quantityMilli,
                    balanceBeforeMilli = before,
                    balanceAfterMilli = after,
                    sourceType = "PURCHASE_RETURN",
                    sourceId = returnId,
                    note = note,
                    createdAt = createdAt
                )
            )
        }
        val supplierId = invoice.supplierId
        val debtReduction = if (supplierId != null) {
            val supplier = database.suppliers().findById(supplierId) ?: error("المورد المرتبط غير موجود")
            val reduction = minOf(supplier.balance, total)
            val newBalance = MoneyMath.reduceBalance(supplier.balance, reduction)
            database.ledger().insertSupplier(
                SupplierTransactionEntity(
                    UUID.randomUUID().toString(), "$operationId:supplier",
                    supplierId, "RETURN", total, newBalance, "PURCHASE_RETURN", returnId, note, createdAt
                )
            )
            check(database.suppliers().setBalance(supplierId, newBalance, createdAt) == 1)
            reduction
        } else 0L
        val cashReceived = Math.subtractExact(total, debtReduction)
        if (cashReceived > 0) {
            database.cash().insert(
                CashTransactionEntity(
                    UUID.randomUUID().toString(), "$operationId:cash", "IN", cashReceived,
                    "PURCHASE_RETURN", returnId, "CASH", note, createdAt
                )
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "PURCHASE_RETURN", "RETURN", returnId, "مرتجع مشتريات بقيمة $total", createdAt)
        )
        SaveReturnResult(returnId, total, false)
    }


    suspend fun adjustStock(
        operationId: String,
        itemId: String,
        newQuantityMilli: Long,
        note: String,
        createdAt: Long = System.currentTimeMillis()
    ) = database.withTransaction {
        require(operationId.isNotBlank() && newQuantityMilli >= 0 && note.isNotBlank()) {
            "أدخل الكمية الجديدة وسبب التسوية"
        }
        database.stock().findByOperationId(operationId)?.let { return@withTransaction it }
        val item = database.items().findById(itemId) ?: error("الصنف غير موجود")
        val before = item.quantityMilli
        require(before != newQuantityMilli) { "الكمية مطابقة للرصيد الحالي" }
        val increase = newQuantityMilli > before
        val delta = if (increase) Math.subtractExact(newQuantityMilli, before) else Math.subtractExact(before, newQuantityMilli)
        check(database.items().setQuantity(itemId, newQuantityMilli, createdAt) == 1)
        val movement = StockMovementEntity(
            id = UUID.randomUUID().toString(),
            operationId = operationId,
            itemId = itemId,
            movementType = if (increase) "ADJUSTMENT_IN" else "ADJUSTMENT_OUT",
            quantityInMilli = if (increase) delta else 0,
            quantityOutMilli = if (increase) 0 else delta,
            balanceBeforeMilli = before,
            balanceAfterMilli = newQuantityMilli,
            sourceType = "MANUAL_ADJUSTMENT",
            sourceId = operationId,
            note = note,
            createdAt = createdAt
        )
        database.stock().insertMovement(movement)
        database.references().insertActivity(
            ActivityLogEntity(UUID.randomUUID().toString(), null, "STOCK_ADJUSTMENT", "ITEM", itemId, note, createdAt)
        )
        movement
    }

}
