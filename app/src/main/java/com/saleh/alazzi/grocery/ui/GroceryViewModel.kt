package com.saleh.alazzi.grocery.ui

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.local.entity.SupplierEntity
import com.saleh.alazzi.grocery.data.local.entity.NoteEntity
import com.saleh.alazzi.grocery.data.local.entity.UserEntity
import com.saleh.alazzi.grocery.security.PinHasher
import com.saleh.alazzi.grocery.security.PinCredential
import com.saleh.alazzi.grocery.data.repository.CreateInvoiceInput
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.data.repository.ReturnLineInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Calendar

enum class ReportPeriod(val label: String) {
    TODAY("اليوم"),
    WEEK("هذا الأسبوع"),
    MONTH("هذا الشهر")
}

data class ReportSummary(
    val sales: Long = 0,
    val purchases: Long = 0,
    val grossProfit: Long = 0,
    val salesInvoices: Long = 0,
    val salesReturns: Long = 0,
    val purchaseReturns: Long = 0,
    val expenses: Long = 0
)

class GroceryViewModel(
    private val database: GroceryDatabase,
    private val repository: GroceryRepository
) : ViewModel() {
    val customers = database.customers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val suppliers = database.suppliers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items = database.items().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lowStockItems = database.items().observeLowStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses = database.expenses().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transfers = database.transfers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = database.references().observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val users = database.references().observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val storeSettings = database.references().observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _session = MutableStateFlow<UserEntity?>(null)
    val session = _session.asStateFlow()



    val cashBalance = database.cash().observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    private val todayRange = todayRange()
    val salesToday = database.salesInvoices().observeSalesTotal(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val purchasesToday = database.purchaseInvoices().observePurchaseTotal(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val invoicesToday = database.salesInvoices().observeSalesCount(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val salesInvoices = database.salesInvoices().observePage(200, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val purchaseInvoices = database.purchaseInvoices().observePage(200, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grossProfitToday = database.salesInvoices().observeGrossProfit(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)


    private val _reportPeriod = MutableStateFlow(ReportPeriod.TODAY)
    val reportPeriod = _reportPeriod.asStateFlow()

    val reportSummary = _reportPeriod.flatMapLatest { period ->
        val (from, to) = reportRange(period)
        combine(
            arrayOf(
                database.salesInvoices().observeSalesTotal(from, to),
                database.purchaseInvoices().observePurchaseTotal(from, to),
                database.salesInvoices().observeGrossProfit(from, to),
                database.salesInvoices().observeSalesCount(from, to),
                database.returns().observeSalesReturnsTotal(from, to),
                database.returns().observePurchaseReturnsTotal(from, to),
                database.expenses().observeTotal(from, to)
            )
        ) { values ->
            ReportSummary(
                sales = values[0],
                purchases = values[1],
                grossProfit = values[2],
                salesInvoices = values[3],
                salesReturns = values[4],
                purchaseReturns = values[5],
                expenses = values[6]
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportSummary())

    fun setReportPeriod(period: ReportPeriod) {
        _reportPeriod.value = period
    }


    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun saveStoreSettings(storeName: String, phone: String, address: String, receiptFooter: String) {
        viewModelScope.launch {
            runCatching {
                val cleanName = storeName.trim()
                require(cleanName.isNotBlank()) { "اكتب اسم المتجر" }
                val now = System.currentTimeMillis()
                database.withTransaction {
                    val values = mapOf(
                        "store_name" to cleanName,
                        "store_phone" to phone.trim(),
                        "store_address" to address.trim(),
                        "receipt_footer" to receiptFooter.trim()
                    )
                    values.forEach { (key, value) ->
                        database.references().putSetting(
                            com.saleh.alazzi.grocery.data.local.entity.AppSettingEntity(key, value, now)
                        )
                    }
                }
            }.onSuccess { eventChannel.send("تم حفظ إعدادات المتجر") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر حفظ إعدادات المتجر")) }
        }
    }

    fun addCustomer(name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم العميل" }
                val cleanPhone = phone.trim().ifBlank { null }
                require(database.customers().duplicateCount(cleanName, cleanPhone) == 0) { "العميل مسجل مسبقاً" }
                val now = System.currentTimeMillis()
                database.customers().insert(
                    CustomerEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        phone = cleanPhone,
                        address = address.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }.onSuccess { eventChannel.send("تمت إضافة العميل") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إضافة العميل")) }
        }
    }

    fun addSupplier(name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم المورد" }
                val cleanPhone = phone.trim().ifBlank { null }
                require(database.suppliers().duplicateCount(cleanName, cleanPhone) == 0) { "المورد مسجل مسبقاً" }
                val now = System.currentTimeMillis()
                database.suppliers().insert(
                    SupplierEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        phone = cleanPhone,
                        address = address.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }.onSuccess { eventChannel.send("تمت إضافة المورد") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إضافة المورد")) }
        }
    }

    fun addItem(name: String, barcode: String, quantity: String, purchasePrice: String, salePrice: String) {
        viewModelScope.launch {
            runCatching {
                database.withTransaction {
                    val cleanName = name.trim()
                    require(cleanName.isNotEmpty()) { "اكتب اسم الصنف" }
                    val cleanBarcode = barcode.trim().ifBlank { null }
                    require(database.items().duplicateCount(cleanName, cleanBarcode) == 0) { "الصنف أو الباركود مسجل مسبقاً" }
                    val now = System.currentTimeMillis()
                    val quantityMilli = (quantity.toBigDecimalOrNull() ?: error("الكمية غير صحيحة"))
                        .multiply(java.math.BigDecimal("1000")).longValueExact()
                    val buy = purchasePrice.toLongOrNull() ?: error("سعر الشراء غير صحيح")
                    val sell = salePrice.toLongOrNull() ?: error("سعر البيع غير صحيح")
                    require(quantityMilli >= 0 && buy >= 0 && sell >= 0) { "لا تقبل القيم السالبة" }
                    val itemId = UUID.randomUUID().toString()
                    database.items().insert(
                        ItemEntity(
                            id = itemId,
                            name = cleanName,
                            barcode = cleanBarcode,
                            quantityMilli = quantityMilli,
                            purchasePrice = buy,
                            salePrice = sell,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    if (quantityMilli > 0) {
                        database.stock().insertMovement(
                            com.saleh.alazzi.grocery.data.local.entity.StockMovementEntity(
                                id = UUID.randomUUID().toString(),
                                operationId = "opening:$itemId",
                                itemId = itemId,
                                movementType = "OPENING",
                                quantityInMilli = quantityMilli,
                                quantityOutMilli = 0,
                                balanceBeforeMilli = 0,
                                balanceAfterMilli = quantityMilli,
                                sourceType = "OPENING_BALANCE",
                                sourceId = itemId,
                                note = "رصيد افتتاحي عند إضافة الصنف",
                                createdAt = now
                            )
                        )
                    }
                }
            }.onSuccess { eventChannel.send("تمت إضافة الصنف وتسجيل رصيده الافتتاحي") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إضافة الصنف")) }
        }
    }

    fun createSale(input: CreateInvoiceInput, onFinished: (Boolean) -> Unit = {}, onSaved: (com.saleh.alazzi.grocery.data.repository.SaveInvoiceResult) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.createSale(input) }
                .onSuccess {
                    eventChannel.send(
                        if (it.wasDuplicate) "هذه الفاتورة محفوظة مسبقاً: ${it.invoiceNumber}"
                        else "تم حفظ فاتورة البيع رقم ${it.invoiceNumber}"
                    )
                    onSaved(it)
                    onFinished(true)
                }
                .onFailure {
                    eventChannel.send(messageFor(it, "تعذر حفظ فاتورة البيع"))
                    onFinished(false)
                }
        }
    }

    fun createPurchase(input: CreateInvoiceInput, onFinished: (Boolean) -> Unit = {}, onSaved: (com.saleh.alazzi.grocery.data.repository.SaveInvoiceResult) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.createPurchase(input) }
                .onSuccess {
                    eventChannel.send(
                        if (it.wasDuplicate) "فاتورة الشراء محفوظة مسبقاً: ${it.invoiceNumber}"
                        else "تم حفظ فاتورة الشراء رقم ${it.invoiceNumber}"
                    )
                    onSaved(it)
                    onFinished(true)
                }
                .onFailure {
                    eventChannel.send(messageFor(it, "تعذر حفظ فاتورة الشراء"))
                    onFinished(false)
                }
        }
    }

    fun receiveCustomerPayment(customerId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                repository.receiveCustomerPayment(
                    operationId = operationId,
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = "CASH",
                    note = note
                )
            }.onSuccess { eventChannel.send("تم تسجيل قبض العميل وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل القبض")) }
        }
    }

    fun paySupplier(supplierId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                repository.paySupplier(
                    operationId = operationId,
                    supplierId = supplierId,
                    amount = amount,
                    paymentMethod = "CASH",
                    note = note
                )
            }.onSuccess { eventChannel.send("تم تسجيل دفع المورد وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل الدفع")) }
        }
    }

    fun recordExpense(category: String, amount: Long, description: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                repository.recordExpense(
                    operationId = operationId,
                    category = category,
                    amount = amount,
                    paymentMethod = "CASH",
                    description = description
                )
            }.onSuccess { eventChannel.send("تم تسجيل المصروف وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل المصروف")) }
        }
    }

    fun recordTransfer(recipient: String, phone: String, amount: Long, fees: Long, direction: String, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                repository.recordTransfer(
                    operationId = operationId,
                    recipientName = recipient,
                    phone = phone,
                    direction = direction,
                    amount = amount,
                    fees = fees,
                    paymentMethod = "CASH",
                    status = "COMPLETED",
                    note = note
                )
            }.onSuccess { eventChannel.send("تم تسجيل الحوالة وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل الحوالة")) }
        }
    }

    fun addNote(title: String, body: String) {
        viewModelScope.launch {
            runCatching {
                require(title.isNotBlank() || body.isNotBlank()) { "اكتب عنوان الملاحظة أو محتواها" }
                val now = System.currentTimeMillis()
                database.references().insertNote(
                    NoteEntity(UUID.randomUUID().toString(), title.trim(), body.trim(), false, now, now)
                )
            }.onSuccess { eventChannel.send("تم حفظ الملاحظة") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر حفظ الملاحظة")) }
        }
    }

    fun toggleNotePin(note: NoteEntity) {
        viewModelScope.launch {
            database.references().updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            database.references().deleteNote(id)
            eventChannel.send("تم حذف الملاحظة")
        }
    }

    fun createAdminUser(displayName: String, pin: String) {
        viewModelScope.launch {
            runCatching {
                require(database.references().observeUsers().first().isEmpty()) { "تم إعداد مستخدم مدير مسبقاً" }
                require(displayName.isNotBlank()) { "اكتب اسم المدير" }
                val credential = PinHasher.create(pin.toCharArray())
                val user = UserEntity(
                    id = UUID.randomUUID().toString(),
                    username = "admin",
                    displayName = displayName.trim(),
                    pinSalt = credential.saltBase64,
                    pinHash = credential.hashBase64,
                    role = "MANAGER",
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
                database.references().insertUser(user)
                _session.value = user
            }.onSuccess { eventChannel.send("تم إعداد حساب المدير") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إعداد حساب المدير")) }
        }
    }

    fun login(username: String, pin: String) {
        viewModelScope.launch {
            val user = database.references().findUser(username.trim())
            val valid = user != null && user.isActive &&
                PinHasher.verify(pin.toCharArray(), PinCredential(user.pinSalt, user.pinHash))
            if (valid) {
                _session.value = user
                eventChannel.send("مرحباً ${user!!.displayName}")
            } else {
                eventChannel.send("اسم المستخدم أو PIN غير صحيح")
            }
        }
    }

    fun logout() { _session.value = null }

    fun addCashier(displayName: String, username: String, pin: String) {
        viewModelScope.launch {
            runCatching {
                require(_session.value?.role == "MANAGER") { "هذه الصلاحية للمدير فقط" }
                require(displayName.isNotBlank() && username.isNotBlank()) { "أدخل اسم المستخدم والاسم الظاهر" }
                val credential = PinHasher.create(pin.toCharArray())
                val user = UserEntity(
                    id = UUID.randomUUID().toString(),
                    username = username.trim(),
                    displayName = displayName.trim(),
                    pinSalt = credential.saltBase64,
                    pinHash = credential.hashBase64,
                    role = "CASHIER",
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
                database.references().insertUser(user)
            }.onSuccess { eventChannel.send("تم إنشاء حساب الكاشير") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إنشاء حساب الكاشير")) }
        }
    }

    fun salesInvoice(id: String) = database.salesInvoices().observeById(id)
    fun salesInvoiceLines(id: String) = database.salesInvoices().observeLines(id)
    fun purchaseInvoice(id: String) = database.purchaseInvoices().observeById(id)
    fun purchaseInvoiceLines(id: String) = database.purchaseInvoices().observeLines(id)

    fun returnSale(invoiceId: String, lines: List<ReturnLineInput>, note: String, operationId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.returnSale(operationId, invoiceId, lines, note) }
                .onSuccess { eventChannel.send("تم حفظ مرتجع البيع بقيمة ${it.total}"); onComplete(true) }
                .onFailure { eventChannel.send(messageFor(it, "تعذر حفظ مرتجع البيع")); onComplete(false) }
        }
    }

    fun returnPurchase(invoiceId: String, lines: List<ReturnLineInput>, note: String, operationId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.returnPurchase(operationId, invoiceId, lines, note) }
                .onSuccess { eventChannel.send("تم حفظ مرتجع الشراء بقيمة ${it.total}"); onComplete(true) }
                .onFailure { eventChannel.send(messageFor(it, "تعذر حفظ مرتجع الشراء")); onComplete(false) }
        }
    }

    fun adjustStock(itemId: String, newQuantityMilli: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching { repository.adjustStock(operationId, itemId, newQuantityMilli, note) }
                .onSuccess { eventChannel.send("تمت تسوية المخزون وتسجيل الحركة") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تعديل المخزون")) }
        }
    }

    fun customerTransactions(customerId: String) = database.ledger().observeCustomer(customerId)
    fun supplierTransactions(supplierId: String) = database.ledger().observeSupplier(supplierId)

    private fun reportRange(period: ReportPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        when (period) {
            ReportPeriod.TODAY -> Unit
            ReportPeriod.WEEK -> {
                val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
            }
            ReportPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis to (System.currentTimeMillis() + 1L)
    }

    private fun todayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return start to calendar.timeInMillis
    }

    private fun messageFor(error: Throwable, fallback: String): String =
        error.message?.takeIf { it.isNotBlank() } ?: fallback

    class Factory(
        private val database: GroceryDatabase,
        private val repository: GroceryRepository
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GroceryViewModel::class.java))
            return GroceryViewModel(database, repository) as T
        }
    }
}
