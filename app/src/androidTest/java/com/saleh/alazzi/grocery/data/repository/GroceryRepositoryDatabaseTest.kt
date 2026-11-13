package com.saleh.alazzi.grocery.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.AppSettingEntity
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import com.saleh.alazzi.grocery.data.local.entity.ExpenseEntity
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroceryRepositoryDatabaseTest {
    private lateinit var database: GroceryDatabase
    private lateinit var repository: GroceryRepository
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, GroceryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GroceryRepository(database)
        database.customers().insert(
            CustomerEntity(
                id = "customer-1",
                name = "عميل اختبار",
                balance = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        database.suppliers().insert(
            SupplierEntity(
                id = "supplier-1",
                name = "مورد اختبار",
                balance = 0,
                createdAt = now,
                updatedAt = now
            )
        )
        database.items().insert(
            ItemEntity(
                id = "item-1",
                name = "صنف اختبار",
                quantityMilli = 5_000,
                purchasePrice = 60,
                salePrice = 100,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun manualCustomerAndSupplierDebitsAreAtomicAndIdempotent() = runBlocking {
        repository.addCustomerDebit("customer-debit-1", "customer-1", 500L, "رصيد افتتاحي", now)
        repository.addCustomerDebit("customer-debit-1", "customer-1", 900L, "إعادة محاولة", now + 1)
        repository.addSupplierDebit("supplier-debit-1", "supplier-1", 700L, "فاتورة يدوية", now + 2)
        repository.addSupplierDebit("supplier-debit-1", "supplier-1", 1_100L, "إعادة محاولة", now + 3)

        assertEquals(500L, database.customers().findById("customer-1")!!.balance)
        assertEquals(700L, database.suppliers().findById("supplier-1")!!.balance)
        assertEquals(1, database.ledger().observeCustomer("customer-1").first().size)
        assertEquals(1, database.ledger().observeSupplier("supplier-1").first().size)
        assertEquals(0L, database.cash().observeBalance().first())
    }

    @Test
    fun salePostsInvoiceStockLedgerAndCashExactlyOnce() = runBlocking {
        val input = CreateInvoiceInput(
            operationId = "sale-operation-001",
            customerOrSupplierId = "customer-1",
            lines = listOf(InvoiceLineInput("item-1", 2_000, 100)),
            paid = 50,
            paymentMethod = "CASH",
            createdAt = now
        )

        val first = repository.createSale(input)
        val retry = repository.createSale(input)

        assertEquals(false, first.wasDuplicate)
        assertEquals(true, retry.wasDuplicate)
        assertEquals(first.invoiceId, retry.invoiceId)
        assertEquals(3_000L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(150L, database.customers().findById("customer-1")!!.balance)
        assertEquals(50L, database.cash().observeBalance().first())
        assertEquals(1, database.salesInvoices().lines(first.invoiceId).size)
        assertEquals(2, database.ledger().observeCustomer("customer-1").first().size)
        assertEquals(80L, database.salesInvoices().observeGrossProfit(now - 1, now + 1).first())
    }

    @Test
    fun insufficientStockRollsBackInvoiceLedgerAndCash() = runBlocking {
        val input = CreateInvoiceInput(
            operationId = "sale-operation-insufficient",
            customerOrSupplierId = "customer-1",
            lines = listOf(InvoiceLineInput("item-1", 6_000, 100)),
            paid = 100,
            paymentMethod = "CASH",
            createdAt = now
        )

        runCatching { repository.createSale(input) }

        assertEquals(null, database.salesInvoices().findByOperationId(input.operationId))
        assertEquals(5_000L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(0L, database.customers().findById("customer-1")!!.balance)
        assertEquals(0L, database.cash().observeBalance().first())
        assertNotNull(database.customers().findById("customer-1"))
    }

    @Test
    fun partialSaleReturnRestoresStockAndReducesCustomerDebt() = runBlocking {
        val sale = repository.createSale(
            CreateInvoiceInput(
                operationId = "sale-for-return",
                customerOrSupplierId = "customer-1",
                lines = listOf(InvoiceLineInput("item-1", 2_000, 100)),
                paid = 0,
                paymentMethod = "CASH",
                createdAt = now
            )
        )
        val originalLine = database.salesInvoices().lines(sale.invoiceId).single()
        val returned = repository.returnSale(
            operationId = "sale-return-1",
            invoiceId = sale.invoiceId,
            lines = listOf(ReturnLineInput(originalLine.id, 1_000)),
            createdAt = now + 1
        )
        assertEquals(100L, returned.total)
        assertEquals(4_000L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(100L, database.customers().findById("customer-1")!!.balance)

        val retry = repository.returnSale(
            operationId = "sale-return-1",
            invoiceId = sale.invoiceId,
            lines = listOf(ReturnLineInput(originalLine.id, 1_000)),
            createdAt = now + 2
        )
        assertEquals(true, retry.wasDuplicate)
        assertEquals(4_000L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(100L, database.customers().findById("customer-1")!!.balance)
        assertEquals(1, database.returns().observeForInvoice(sale.invoiceId).first().size)
    }

    @Test
    fun purchaseInvoiceAddsStockAndRecordsMovementExactlyOnce() = runBlocking {
        val input = CreateInvoiceInput(
            operationId = "purchase-stock-once",
            customerOrSupplierId = "supplier-1",
            lines = listOf(InvoiceLineInput("item-1", 2_500, 100)),
            paid = 0,
            paymentMethod = "CASH",
            createdAt = now
        )

        val first = repository.createPurchase(input)
        val retry = repository.createPurchase(input)

        assertEquals(false, first.wasDuplicate)
        assertEquals(true, retry.wasDuplicate)
        assertEquals(first.invoiceId, retry.invoiceId)
        assertEquals(7_500L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(1, database.stock().observeMovements("item-1").first().size)
        assertEquals(1, database.purchaseInvoices().lines(first.invoiceId).size)
        assertEquals(250L, database.purchaseInvoices().findById(first.invoiceId)!!.total)
    }

    @Test
    fun purchaseReturnReducesSupplierDebtAndStock() = runBlocking {
        val purchase = repository.createPurchase(
            CreateInvoiceInput(
                operationId = "purchase-for-return",
                customerOrSupplierId = "supplier-1",
                lines = listOf(InvoiceLineInput("item-1", 2_000, 80)),
                paid = 0,
                paymentMethod = "CASH",
                createdAt = now
            )
        )
        val originalLine = database.purchaseInvoices().lines(purchase.invoiceId).single()
        val returned = repository.returnPurchase(
            operationId = "purchase-return-1",
            invoiceId = purchase.invoiceId,
            lines = listOf(ReturnLineInput(originalLine.id, 1_000)),
            createdAt = now + 1
        )
        assertEquals(80L, returned.total)
        assertEquals(6_000L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(80L, database.suppliers().findById("supplier-1")!!.balance)
    }


    @Test
    fun storeProfileSettingsArePersistedAndObservable() = runBlocking {
        database.references().putSetting(AppSettingEntity("store_name", "بقالة العزي", now))
        database.references().putSetting(AppSettingEntity("store_phone", "777000000", now + 1))
        val values = database.references().observeSettings().first().associate { it.key to it.value }

        assertEquals("بقالة العزي", values["store_name"])
        assertEquals("777000000", values["store_phone"])
    }

    @Test
    fun dailyReportAggregatesSalesReturnsPurchaseReturnsAndExpenses() = runBlocking {
        val sale = repository.createSale(
            CreateInvoiceInput(
                operationId = "report-sale",
                customerOrSupplierId = "customer-1",
                lines = listOf(InvoiceLineInput("item-1", 1_000, 100)),
                paid = 100,
                paymentMethod = "CASH",
                createdAt = now
            )
        )
        val saleLine = database.salesInvoices().lines(sale.invoiceId).single()
        repository.returnSale(
            operationId = "report-sale-return",
            invoiceId = sale.invoiceId,
            lines = listOf(ReturnLineInput(saleLine.id, 1_000)),
            createdAt = now + 1
        )

        val purchase = repository.createPurchase(
            CreateInvoiceInput(
                operationId = "report-purchase",
                customerOrSupplierId = "supplier-1",
                lines = listOf(InvoiceLineInput("item-1", 1_000, 80)),
                paid = 80,
                paymentMethod = "CASH",
                createdAt = now
            )
        )
        val purchaseLine = database.purchaseInvoices().lines(purchase.invoiceId).single()
        repository.returnPurchase(
            operationId = "report-purchase-return",
            invoiceId = purchase.invoiceId,
            lines = listOf(ReturnLineInput(purchaseLine.id, 1_000)),
            createdAt = now + 1
        )
        repository.recordExpense(
            operationId = "report-expense",
            category = "تشغيل",
            amount = 25,
            paymentMethod = "CASH",
            description = "مصروف اختبار",
            createdAt = now + 2
        )

        val from = now - 1
        val to = now + 10
        assertEquals(100L, database.salesInvoices().observeSalesTotal(from, to).first())
        assertEquals(100L, database.returns().observeSalesReturnsTotal(from, to).first())
        assertEquals(80L, database.purchaseInvoices().observePurchaseTotal(from, to).first())
        assertEquals(80L, database.returns().observePurchaseReturnsTotal(from, to).first())
        assertEquals(25L, database.expenses().observeTotal(from, to).first())

        val summary = database.salesInvoices().observeFinancialSummary(from, to).first()
        assertEquals(100L, summary.sales)
        assertEquals(80L, summary.purchases)
        assertEquals(100L, summary.salesReturns)
        assertEquals(80L, summary.purchaseReturns)
        assertEquals(25L, summary.expenses)
        assertEquals(1L, summary.salesInvoices)
    }

    @Test
    fun stockAdjustmentCreatesAuditMovementAndIsIdempotent() = runBlocking {
        repository.adjustStock("adjust-1", "item-1", 7_500, "جرد فعلي", now)
        repository.adjustStock("adjust-1", "item-1", 9_000, "إعادة محاولة", now + 1)

        assertEquals(7_500L, database.items().findById("item-1")!!.quantityMilli)
        assertEquals(1, database.stock().observeMovements("item-1").first().size)
    }

}
