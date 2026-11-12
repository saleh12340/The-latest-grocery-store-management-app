package com.saleh.alazzi.grocery.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers", indices = [Index(value = ["phone"], unique = true)])
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val balance: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "suppliers", indices = [Index(value = ["phone"], unique = true)])
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val balance: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "units", indices = [Index(value = ["name"], unique = true)])
data class UnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["barcode"], unique = true), Index("name"), Index("categoryId"), Index("unitId")]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val barcode: String? = null,
    val categoryId: String? = null,
    val unitId: String? = null,
    val quantityMilli: Long = 0,
    val lowStockThresholdMilli: Long = 0,
    val purchasePrice: Long = 0,
    val salePrice: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "sales_invoices",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["invoiceNumber"], unique = true), Index(value = ["operationId"], unique = true), Index("customerId"), Index("createdAt")]
)
data class SalesInvoiceEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val invoiceNumber: String,
    val customerId: String? = null,
    val createdAt: Long,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paid: Long,
    val remaining: Long,
    val paymentMethod: String,
    val notes: String? = null,
    val status: String = "POSTED"
)

@Entity(
    tableName = "sales_invoice_items",
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("invoiceId"), Index("itemId")]
)
data class SalesInvoiceItemEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val itemId: String,
    val itemNameSnapshot: String,
    val quantityMilli: Long,
    val unitPrice: Long,
    val unitCostSnapshot: Long,
    val lineTotal: Long
)

@Entity(
    tableName = "purchase_invoices",
    foreignKeys = [ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["invoiceNumber"], unique = true), Index(value = ["operationId"], unique = true), Index("supplierId"), Index("createdAt")]
)
data class PurchaseInvoiceEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val invoiceNumber: String,
    val supplierId: String? = null,
    val createdAt: Long,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paid: Long,
    val remaining: Long,
    val paymentMethod: String,
    val notes: String? = null,
    val status: String = "POSTED"
)

@Entity(
    tableName = "purchase_invoice_items",
    foreignKeys = [
        ForeignKey(entity = PurchaseInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("invoiceId"), Index("itemId")]
)
data class PurchaseInvoiceItemEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val itemId: String,
    val itemNameSnapshot: String,
    val quantityMilli: Long,
    val unitCost: Long,
    val lineTotal: Long
)

@Entity(
    tableName = "customer_transactions",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId"), Index("createdAt"), Index(value = ["operationId"], unique = true)]
)
data class CustomerTransactionEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val customerId: String,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val sourceType: String,
    val sourceId: String?,
    val note: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "supplier_transactions",
    foreignKeys = [ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("supplierId"), Index("createdAt"), Index(value = ["operationId"], unique = true)]
)
data class SupplierTransactionEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val supplierId: String,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val sourceType: String,
    val sourceId: String?,
    val note: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("itemId"), Index("createdAt"), Index(value = ["sourceType", "sourceId", "itemId"])]
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val itemId: String,
    val movementType: String,
    val quantityInMilli: Long,
    val quantityOutMilli: Long,
    val balanceBeforeMilli: Long,
    val balanceAfterMilli: Long,
    val sourceType: String,
    val sourceId: String,
    val note: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "cash_transactions",
    indices = [Index(value = ["idempotencyKey"], unique = true), Index("createdAt"), Index("sourceId")]
)
data class CashTransactionEntity(
    @PrimaryKey val id: String,
    val idempotencyKey: String,
    val direction: String,
    val amount: Long,
    val sourceType: String,
    val sourceId: String?,
    val paymentMethod: String,
    val note: String? = null,
    val createdAt: Long
)

@Entity(tableName = "expenses", indices = [Index("createdAt"), Index("category"), Index(value = ["operationId"], unique = true)])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val category: String,
    val amount: Long,
    val paymentMethod: String,
    val description: String? = null,
    val createdAt: Long
)


@Entity(tableName = "transfers", indices = [Index(value = ["operationId"], unique = true), Index("createdAt"), Index("phone")])
data class TransferEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val recipientName: String,
    val phone: String,
    val direction: String,
    val amount: Long,
    val fees: Long,
    val netAmount: Long,
    val status: String,
    val note: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "returns",
    indices = [Index(value = ["operationId"], unique = true), Index("originalInvoiceId"), Index("createdAt")]
)
data class ReturnEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val returnType: String,
    val originalInvoiceId: String,
    val total: Long,
    val createdAt: Long,
    val note: String? = null
)

@Entity(
    tableName = "return_items",
    foreignKeys = [ForeignKey(entity = ReturnEntity::class, parentColumns = ["id"], childColumns = ["returnId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("returnId"), Index("originalInvoiceItemId")]
)
data class ReturnItemEntity(
    @PrimaryKey val id: String,
    val returnId: String,
    val originalInvoiceItemId: String,
    val itemId: String,
    val quantityMilli: Long,
    val amount: Long
)

@Entity(tableName = "notes", indices = [Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)

@Entity(tableName = "activity_logs", indices = [Index("createdAt"), Index("actorId")])
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val actorId: String?,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val details: String? = null,
    val createdAt: Long
)

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val pinSalt: String,
    val pinHash: String,
    val role: String,
    val isActive: Boolean = true,
    val createdAt: Long
)

@Entity(tableName = "backup_history", indices = [Index("createdAt")])
data class BackupHistoryEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val formatVersion: Int,
    val checksumSha256: String,
    val createdAt: Long,
    val result: String
)


data class FinancialReportSummary(
    val sales: Long,
    val purchases: Long,
    val grossProfit: Long,
    val salesInvoices: Long,
    val salesReturns: Long,
    val purchaseReturns: Long,
    val expenses: Long
)
