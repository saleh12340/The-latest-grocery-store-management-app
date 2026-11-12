package com.saleh.alazzi.grocery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saleh.alazzi.grocery.data.local.entity.*

@Dao
interface BackupDao {
    @Query("SELECT * FROM customers") suspend fun readCustomers(): List<CustomerEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomers(rows: List<CustomerEntity>)
    @Query("DELETE FROM customers") suspend fun clearCustomers(): Int
    @Query("SELECT * FROM suppliers") suspend fun readSuppliers(): List<SupplierEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSuppliers(rows: List<SupplierEntity>)
    @Query("DELETE FROM suppliers") suspend fun clearSuppliers(): Int
    @Query("SELECT * FROM categories") suspend fun readCategories(): List<CategoryEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategories(rows: List<CategoryEntity>)
    @Query("DELETE FROM categories") suspend fun clearCategories(): Int
    @Query("SELECT * FROM units") suspend fun readUnits(): List<UnitEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUnits(rows: List<UnitEntity>)
    @Query("DELETE FROM units") suspend fun clearUnits(): Int
    @Query("SELECT * FROM items") suspend fun readItems(): List<ItemEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItems(rows: List<ItemEntity>)
    @Query("DELETE FROM items") suspend fun clearItems(): Int
    @Query("SELECT * FROM sales_invoices") suspend fun readSalesInvoices(): List<SalesInvoiceEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSalesInvoices(rows: List<SalesInvoiceEntity>)
    @Query("DELETE FROM sales_invoices") suspend fun clearSalesInvoices(): Int
    @Query("SELECT * FROM sales_invoice_items") suspend fun readSalesInvoiceItems(): List<SalesInvoiceItemEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSalesInvoiceItems(rows: List<SalesInvoiceItemEntity>)
    @Query("DELETE FROM sales_invoice_items") suspend fun clearSalesInvoiceItems(): Int
    @Query("SELECT * FROM purchase_invoices") suspend fun readPurchaseInvoices(): List<PurchaseInvoiceEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPurchaseInvoices(rows: List<PurchaseInvoiceEntity>)
    @Query("DELETE FROM purchase_invoices") suspend fun clearPurchaseInvoices(): Int
    @Query("SELECT * FROM purchase_invoice_items") suspend fun readPurchaseInvoiceItems(): List<PurchaseInvoiceItemEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPurchaseInvoiceItems(rows: List<PurchaseInvoiceItemEntity>)
    @Query("DELETE FROM purchase_invoice_items") suspend fun clearPurchaseInvoiceItems(): Int
    @Query("SELECT * FROM customer_transactions") suspend fun readCustomerTransactions(): List<CustomerTransactionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomerTransactions(rows: List<CustomerTransactionEntity>)
    @Query("DELETE FROM customer_transactions") suspend fun clearCustomerTransactions(): Int
    @Query("SELECT * FROM supplier_transactions") suspend fun readSupplierTransactions(): List<SupplierTransactionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplierTransactions(rows: List<SupplierTransactionEntity>)
    @Query("DELETE FROM supplier_transactions") suspend fun clearSupplierTransactions(): Int
    @Query("SELECT * FROM stock_movements") suspend fun readStockMovements(): List<StockMovementEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStockMovements(rows: List<StockMovementEntity>)
    @Query("DELETE FROM stock_movements") suspend fun clearStockMovements(): Int
    @Query("SELECT * FROM cash_transactions") suspend fun readCashTransactions(): List<CashTransactionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCashTransactions(rows: List<CashTransactionEntity>)
    @Query("DELETE FROM cash_transactions") suspend fun clearCashTransactions(): Int
    @Query("SELECT * FROM expenses") suspend fun readExpenses(): List<ExpenseEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExpenses(rows: List<ExpenseEntity>)
    @Query("DELETE FROM expenses") suspend fun clearExpenses(): Int
    @Query("SELECT * FROM transfers") suspend fun readTransfers(): List<TransferEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransfers(rows: List<TransferEntity>)
    @Query("DELETE FROM transfers") suspend fun clearTransfers(): Int
    @Query("SELECT * FROM returns") suspend fun readReturns(): List<ReturnEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReturns(rows: List<ReturnEntity>)
    @Query("DELETE FROM returns") suspend fun clearReturns(): Int
    @Query("SELECT * FROM return_items") suspend fun readReturnItems(): List<ReturnItemEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReturnItems(rows: List<ReturnItemEntity>)
    @Query("DELETE FROM return_items") suspend fun clearReturnItems(): Int
    @Query("SELECT * FROM notes") suspend fun readNotes(): List<NoteEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNotes(rows: List<NoteEntity>)
    @Query("DELETE FROM notes") suspend fun clearNotes(): Int
    @Query("SELECT * FROM app_settings") suspend fun readAppSettings(): List<AppSettingEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAppSettings(rows: List<AppSettingEntity>)
    @Query("DELETE FROM app_settings") suspend fun clearAppSettings(): Int
    @Query("SELECT * FROM activity_logs") suspend fun readActivityLogs(): List<ActivityLogEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertActivityLogs(rows: List<ActivityLogEntity>)
    @Query("DELETE FROM activity_logs") suspend fun clearActivityLogs(): Int
    @Query("SELECT * FROM users") suspend fun readUsers(): List<UserEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUsers(rows: List<UserEntity>)
    @Query("DELETE FROM users") suspend fun clearUsers(): Int
    @Query("SELECT * FROM backup_history") suspend fun readBackupHistory(): List<BackupHistoryEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBackupHistory(rows: List<BackupHistoryEntity>)
    @Query("DELETE FROM backup_history") suspend fun clearBackupHistory(): Int
}
