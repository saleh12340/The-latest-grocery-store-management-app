package com.saleh.alazzi.grocery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.saleh.alazzi.grocery.data.local.entity.*

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: CustomerEntity)
    @Query("UPDATE customers SET name=:name, phone=:phone, address=:address, notes=:notes, updatedAt=:now WHERE id=:id")
    suspend fun updateProfile(id: String, name: String, phone: String?, address: String?, notes: String?, now: Long): Int
    @Query("SELECT * FROM customers ORDER BY createdAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<CustomerEntity>>
    @Query("UPDATE customers SET balance=:balance, updatedAt=:now WHERE id=:id")
    suspend fun setBalance(id: String, balance: Long, now: Long): Int
}

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: SupplierEntity)
    @Query("SELECT * FROM suppliers ORDER BY createdAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SupplierEntity>>
    @Query("SELECT * FROM suppliers WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): SupplierEntity?
    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<SupplierEntity>>
    @Query("UPDATE suppliers SET balance=:balance, updatedAt=:now WHERE id=:id")
    suspend fun setBalance(id: String, balance: Long, now: Long): Int
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: ItemEntity)
    @Query("SELECT * FROM items WHERE isActive=1 ORDER BY createdAt DESC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): ItemEntity?
    @Query("SELECT * FROM items WHERE barcode=:barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?
    @Query("SELECT * FROM items WHERE isActive=1 AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name LIMIT 50")
    fun search(query: String): Flow<List<ItemEntity>>
    @Query("UPDATE items SET quantityMilli=:quantity, updatedAt=:now WHERE id=:id")
    suspend fun setQuantity(id: String, quantity: Long, now: Long): Int
    @Query("SELECT * FROM items WHERE isActive=1 AND quantityMilli<=lowStockThresholdMilli ORDER BY quantityMilli ASC")
    fun observeLowStock(): Flow<List<ItemEntity>>
}

@Dao
interface SalesInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(invoice: SalesInvoiceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLines(lines: List<SalesInvoiceItemEntity>)
    @Query("SELECT * FROM sales_invoices WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): SalesInvoiceEntity?
    @Query("SELECT * FROM sales_invoices WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): SalesInvoiceEntity?
    @Query("SELECT * FROM sales_invoices ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<SalesInvoiceEntity>>
    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId=:invoiceId")
    suspend fun lines(invoiceId: String): List<SalesInvoiceItemEntity>
    @Query("SELECT COALESCE(MAX(CAST(invoiceNumber AS INTEGER)), 0) FROM sales_invoices")
    suspend fun maxInvoiceNumber(): Long
    @Query("SELECT * FROM sales_invoices WHERE customerId=:customerId ORDER BY createdAt DESC")
    fun observeForCustomer(customerId: String): Flow<List<SalesInvoiceEntity>>
    @Query("UPDATE sales_invoices SET status=:status WHERE id=:id AND status='POSTED'")
    suspend fun changePostedStatus(id: String, status: String): Int
}

@Dao
interface PurchaseInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(invoice: PurchaseInvoiceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLines(lines: List<PurchaseInvoiceItemEntity>)
    @Query("SELECT * FROM purchase_invoices ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<PurchaseInvoiceEntity>>
    @Query("SELECT * FROM purchase_invoices WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): PurchaseInvoiceEntity?
    @Query("SELECT * FROM purchase_invoice_items WHERE invoiceId=:invoiceId")
    suspend fun lines(invoiceId: String): List<PurchaseInvoiceItemEntity>
    @Query("SELECT COALESCE(MAX(CAST(invoiceNumber AS INTEGER)), 0) FROM purchase_invoices")
    suspend fun maxInvoiceNumber(): Long
    @Query("SELECT * FROM purchase_invoices WHERE supplierId=:supplierId ORDER BY createdAt DESC")
    fun observeForSupplier(supplierId: String): Flow<List<PurchaseInvoiceEntity>>
}

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomer(value: CustomerTransactionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplier(value: SupplierTransactionEntity)
    @Query("SELECT * FROM customer_transactions WHERE customerId=:id ORDER BY createdAt DESC")
    fun observeCustomer(id: String): Flow<List<CustomerTransactionEntity>>
    @Query("SELECT * FROM supplier_transactions WHERE supplierId=:id ORDER BY createdAt DESC")
    fun observeSupplier(id: String): Flow<List<SupplierTransactionEntity>>
    @Query("SELECT * FROM customer_transactions WHERE operationId=:operationId LIMIT 1")
    suspend fun findCustomerOperation(operationId: String): CustomerTransactionEntity?
    @Query("SELECT * FROM supplier_transactions WHERE operationId=:operationId LIMIT 1")
    suspend fun findSupplierOperation(operationId: String): SupplierTransactionEntity?
}

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMovement(value: StockMovementEntity)
    @Query("SELECT * FROM stock_movements WHERE itemId=:itemId ORDER BY createdAt DESC")
    fun observeMovements(itemId: String): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM stock_movements WHERE sourceType=:sourceType AND sourceId=:sourceId")
    suspend fun findBySource(sourceType: String, sourceId: String): List<StockMovementEntity>
}

@Dao
interface CashDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: CashTransactionEntity)
    @Query("SELECT * FROM cash_transactions ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<CashTransactionEntity>>
    @Query("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM cash_transactions")
    fun observeBalance(): Flow<Long>
    @Query("SELECT * FROM cash_transactions WHERE idempotencyKey=:key LIMIT 1")
    suspend fun findByIdempotencyKey(key: String): CashTransactionEntity?
}

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: ExpenseEntity)
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>
}

@Dao
interface ReferenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategory(value: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUnit(value: UnitEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNote(value: NoteEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(value: AppSettingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertActivity(value: ActivityLogEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUser(value: UserEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBackup(value: BackupHistoryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReturn(value: ReturnEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReturnItems(values: List<ReturnItemEntity>)
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM app_settings WHERE key=:key LIMIT 1")
    suspend fun setting(key: String): AppSettingEntity?
}
