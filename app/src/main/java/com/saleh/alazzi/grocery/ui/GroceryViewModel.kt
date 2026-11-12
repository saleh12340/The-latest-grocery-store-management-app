package com.saleh.alazzi.grocery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.local.entity.SupplierEntity
import com.saleh.alazzi.grocery.data.local.entity.NoteEntity
import com.saleh.alazzi.grocery.data.repository.CreateInvoiceInput
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Calendar

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


    val cashBalance = database.cash().observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    private val todayRange = todayRange()
    val salesToday = database.salesInvoices().observeSalesTotal(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val purchasesToday = database.purchaseInvoices().observePurchaseTotal(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val invoicesToday = database.salesInvoices().observeSalesCount(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val grossProfitToday = database.salesInvoices().observeGrossProfit(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)


    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun addCustomer(name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم العميل" }
                val now = System.currentTimeMillis()
                database.customers().insert(
                    CustomerEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        phone = phone.trim().ifBlank { null },
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
                val now = System.currentTimeMillis()
                database.suppliers().insert(
                    SupplierEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        phone = phone.trim().ifBlank { null },
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
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم الصنف" }
                val now = System.currentTimeMillis()
                val quantityMilli = (quantity.toBigDecimalOrNull() ?: error("الكمية غير صحيحة"))
                    .multiply(java.math.BigDecimal("1000")).longValueExact()
                val buy = purchasePrice.toLongOrNull() ?: error("سعر الشراء غير صحيح")
                val sell = salePrice.toLongOrNull() ?: error("سعر البيع غير صحيح")
                require(quantityMilli >= 0 && buy >= 0 && sell >= 0) { "لا تقبل القيم السالبة" }
                database.items().insert(
                    ItemEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        barcode = barcode.trim().ifBlank { null },
                        quantityMilli = quantityMilli,
                        purchasePrice = buy,
                        salePrice = sell,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }.onSuccess { eventChannel.send("تمت إضافة الصنف") }
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

    fun receiveCustomerPayment(customerId: String, amount: Long, note: String) {
        viewModelScope.launch {
            runCatching {
                repository.receiveCustomerPayment(
                    operationId = UUID.randomUUID().toString(),
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = "CASH",
                    note = note
                )
            }.onSuccess { eventChannel.send("تم تسجيل قبض العميل وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل القبض")) }
        }
    }

    fun paySupplier(supplierId: String, amount: Long, note: String) {
        viewModelScope.launch {
            runCatching {
                repository.paySupplier(
                    operationId = UUID.randomUUID().toString(),
                    supplierId = supplierId,
                    amount = amount,
                    paymentMethod = "CASH",
                    note = note
                )
            }.onSuccess { eventChannel.send("تم تسجيل دفع المورد وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل الدفع")) }
        }
    }

    fun recordExpense(category: String, amount: Long, description: String) {
        viewModelScope.launch {
            runCatching {
                repository.recordExpense(
                    operationId = UUID.randomUUID().toString(),
                    category = category,
                    amount = amount,
                    paymentMethod = "CASH",
                    description = description
                )
            }.onSuccess { eventChannel.send("تم تسجيل المصروف وتحديث الصندوق") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تسجيل المصروف")) }
        }
    }

    fun recordTransfer(recipient: String, phone: String, amount: Long, fees: Long, direction: String, note: String) {
        viewModelScope.launch {
            runCatching {
                repository.recordTransfer(
                    operationId = UUID.randomUUID().toString(),
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

    fun customerTransactions(customerId: String) = database.ledger().observeCustomer(customerId)
    fun supplierTransactions(supplierId: String) = database.ledger().observeSupplier(supplierId)

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
