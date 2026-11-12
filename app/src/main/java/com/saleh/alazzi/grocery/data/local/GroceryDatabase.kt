package com.saleh.alazzi.grocery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.saleh.alazzi.grocery.data.local.dao.*
import com.saleh.alazzi.grocery.data.local.entity.*

@Database(
    entities = [
        CustomerEntity::class,
        SupplierEntity::class,
        CategoryEntity::class,
        UnitEntity::class,
        ItemEntity::class,
        SalesInvoiceEntity::class,
        SalesInvoiceItemEntity::class,
        PurchaseInvoiceEntity::class,
        PurchaseInvoiceItemEntity::class,
        CustomerTransactionEntity::class,
        SupplierTransactionEntity::class,
        StockMovementEntity::class,
        CashTransactionEntity::class,
        ExpenseEntity::class,
        ReturnEntity::class,
        ReturnItemEntity::class,
        NoteEntity::class,
        AppSettingEntity::class,
        ActivityLogEntity::class,
        UserEntity::class,
        BackupHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GroceryDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun suppliers(): SupplierDao
    abstract fun items(): ItemDao
    abstract fun salesInvoices(): SalesInvoiceDao
    abstract fun purchaseInvoices(): PurchaseInvoiceDao
    abstract fun ledger(): LedgerDao
    abstract fun stock(): StockDao
    abstract fun cash(): CashDao
    abstract fun expenses(): ExpenseDao
    abstract fun references(): ReferenceDao

    companion object {
        @Volatile private var INSTANCE: GroceryDatabase? = null

        fun getInstance(context: Context): GroceryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GroceryDatabase::class.java,
                    "al_azzi_grocery.db"
                )
                    // Never discard financial or inventory data during an upgrade.
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
