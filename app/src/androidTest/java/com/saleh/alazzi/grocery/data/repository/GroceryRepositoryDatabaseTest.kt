package com.saleh.alazzi.grocery.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
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

}
