package com.saleh.alazzi.grocery.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPayload(
    val formatVersion: Int,
    val createdAt: Long,
    val customers: List<CustomerEntity>,
    val suppliers: List<SupplierEntity>,
    val categories: List<CategoryEntity>,
    val units: List<UnitEntity>,
    val items: List<ItemEntity>,
    val salesInvoices: List<SalesInvoiceEntity>,
    val salesInvoiceItems: List<SalesInvoiceItemEntity>,
    val purchaseInvoices: List<PurchaseInvoiceEntity>,
    val purchaseInvoiceItems: List<PurchaseInvoiceItemEntity>,
    val customerTransactions: List<CustomerTransactionEntity>,
    val supplierTransactions: List<SupplierTransactionEntity>,
    val stockMovements: List<StockMovementEntity>,
    val cashTransactions: List<CashTransactionEntity>,
    val expenses: List<ExpenseEntity>,
    val transfers: List<TransferEntity>,
    val returns: List<ReturnEntity>,
    val returnItems: List<ReturnItemEntity>,
    val notes: List<NoteEntity>,
    val settings: List<AppSettingEntity>,
    val users: List<UserEntity>,
    val activityLogs: List<ActivityLogEntity>,
    val backupHistory: List<BackupHistoryEntity>
)

data class BackupEnvelope(
    val formatVersion: Int,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val sha256: String
)
data class BackupResult(val recordCount: Int, val checksum: String, val preRestoreFile: String? = null)

class GroceryBackupManager(
    private val context: Context,
    private val database: GroceryDatabase
) {
    companion object {
        const val FORMAT_VERSION = 1
        private const val PBKDF2_ITERATIONS = 310_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val random = SecureRandom()

    suspend fun exportTo(uri: Uri, passphrase: String): BackupResult = withContext(Dispatchers.IO) {
        require(passphrase.length >= 8) { "كلمة مرور النسخة يجب ألا تقل عن 8 أحرف" }
        val payload = readSnapshot()
        val payloadJson = gson.toJson(payload)
        val checksum = sha256(payloadJson)
        val envelope = encrypt(payloadJson, passphrase, checksum)
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(gson.toJson(envelope))
        } ?: error("تعذر فتح ملف النسخة الاحتياطية")
        database.references().insertBackup(
            BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                uri = uri.toString(),
                formatVersion = FORMAT_VERSION,
                checksumSha256 = checksum,
                createdAt = System.currentTimeMillis(),
                result = "EXPORTED_ENCRYPTED"
            )
        )
        BackupResult(countRecords(payload), checksum)
    }

    suspend fun restoreFrom(uri: Uri, passphrase: String, confirmed: Boolean): BackupResult = withContext(Dispatchers.IO) {
        require(confirmed) { "يجب تأكيد الاستعادة صراحةً" }
        require(passphrase.length >= 8) { "أدخل كلمة مرور النسخة الاحتياطية" }
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("تعذر قراءة ملف النسخة الاحتياطية")
        val envelope = gson.fromJson(json, BackupEnvelope::class.java) ?: error("ملف النسخة غير صالح")
        require(envelope.formatVersion == FORMAT_VERSION) { "إصدار النسخة غير مدعوم: \${envelope.formatVersion}" }
        val payloadJson = decrypt(envelope, passphrase)
        val calculated = sha256(payloadJson)
        require(MessageDigest.isEqual(calculated.toByteArray(), envelope.sha256.toByteArray())) {
            "فشل التحقق من سلامة النسخة الاحتياطية"
        }
        val payload = gson.fromJson(payloadJson, BackupPayload::class.java) ?: error("بيانات النسخة غير موجودة")
        require(payload.formatVersion == FORMAT_VERSION) { "مخطط بيانات النسخة غير مدعوم" }

        val before = readSnapshot()
        val beforeJson = gson.toJson(before)
        val preRestore = File(context.filesDir, "pre_restore_\${System.currentTimeMillis()}.json")
        preRestore.writeText(gson.toJson(encrypt(beforeJson, passphrase, sha256(beforeJson))), Charsets.UTF_8)

        database.withTransaction {
            clearCurrentData()
            insertPayload(payload)
            database.references().insertBackup(
                BackupHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    uri = preRestore.absolutePath,
                    formatVersion = FORMAT_VERSION,
                    checksumSha256 = sha256(beforeJson),
                    createdAt = System.currentTimeMillis(),
                    result = "RESTORE_PREIMAGE_ENCRYPTED"
                )
            )
            database.references().insertBackup(
                BackupHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    formatVersion = FORMAT_VERSION,
                    checksumSha256 = calculated,
                    createdAt = System.currentTimeMillis(),
                    result = "RESTORED_ENCRYPTED"
                )
            )
        }
        BackupResult(countRecords(payload), calculated, preRestore.absolutePath)
    }

    private fun encrypt(plainText: String, passphrase: String, checksum: String): BackupEnvelope {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return BackupEnvelope(
            formatVersion = FORMAT_VERSION,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted),
            sha256 = checksum
        )
    }

    private fun decrypt(envelope: BackupEnvelope, passphrase: String): String {
        return try {
            val salt = Base64.getDecoder().decode(envelope.saltBase64)
            val iv = Base64.getDecoder().decode(envelope.ivBase64)
            val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)
            require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "بيانات تشفير النسخة غير صحيحة" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalArgumentException("كلمة المرور غير صحيحة أو ملف النسخة تالف", error)
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private suspend fun readSnapshot(): BackupPayload = database.withTransaction {
        val dao = database.backup()
        BackupPayload(
            formatVersion = FORMAT_VERSION,
            createdAt = System.currentTimeMillis(),
            customers = dao.readCustomers(),
            suppliers = dao.readSuppliers(),
            categories = dao.readCategories(),
            units = dao.readUnits(),
            items = dao.readItems(),
            salesInvoices = dao.readSalesInvoices(),
            salesInvoiceItems = dao.readSalesInvoiceItems(),
            purchaseInvoices = dao.readPurchaseInvoices(),
            purchaseInvoiceItems = dao.readPurchaseInvoiceItems(),
            customerTransactions = dao.readCustomerTransactions(),
            supplierTransactions = dao.readSupplierTransactions(),
            stockMovements = dao.readStockMovements(),
            cashTransactions = dao.readCashTransactions(),
            expenses = dao.readExpenses(),
            transfers = dao.readTransfers(),
            returns = dao.readReturns(),
            returnItems = dao.readReturnItems(),
            notes = dao.readNotes(),
            settings = dao.readAppSettings(),
            users = dao.readUsers(),
            activityLogs = dao.readActivityLogs(),
            backupHistory = dao.readBackupHistory()
        )
    }

    private suspend fun clearCurrentData() {
        val dao = database.backup()
        dao.clearReturnItems()
        dao.clearReturns()
        dao.clearSalesInvoiceItems()
        dao.clearPurchaseInvoiceItems()
        dao.clearCustomerTransactions()
        dao.clearSupplierTransactions()
        dao.clearStockMovements()
        dao.clearCashTransactions()
        dao.clearExpenses()
        dao.clearTransfers()
        dao.clearSalesInvoices()
        dao.clearPurchaseInvoices()
        dao.clearItems()
        dao.clearCustomers()
        dao.clearSuppliers()
        dao.clearCategories()
        dao.clearUnits()
        dao.clearNotes()
        dao.clearAppSettings()
        dao.clearUsers()
        dao.clearActivityLogs()
        dao.clearBackupHistory()
    }

    private suspend fun insertPayload(p: BackupPayload) {
        val dao = database.backup()
        dao.insertCategories(p.categories)
        dao.insertUnits(p.units)
        dao.insertCustomers(p.customers)
        dao.insertSuppliers(p.suppliers)
        dao.insertItems(p.items)
        dao.insertSalesInvoices(p.salesInvoices)
        dao.insertSalesInvoiceItems(p.salesInvoiceItems)
        dao.insertPurchaseInvoices(p.purchaseInvoices)
        dao.insertPurchaseInvoiceItems(p.purchaseInvoiceItems)
        dao.insertCustomerTransactions(p.customerTransactions)
        dao.insertSupplierTransactions(p.supplierTransactions)
        dao.insertStockMovements(p.stockMovements)
        dao.insertCashTransactions(p.cashTransactions)
        dao.insertExpenses(p.expenses)
        dao.insertTransfers(p.transfers)
        dao.insertReturns(p.returns)
        dao.insertReturnItems(p.returnItems)
        dao.insertNotes(p.notes)
        dao.insertAppSettings(p.settings)
        dao.insertUsers(p.users)
        dao.insertActivityLogs(p.activityLogs)
        dao.insertBackupHistory(p.backupHistory)
    }

    private fun countRecords(p: BackupPayload): Int =
        p.customers.size + p.suppliers.size + p.categories.size + p.units.size + p.items.size +
            p.salesInvoices.size + p.salesInvoiceItems.size + p.purchaseInvoices.size + p.purchaseInvoiceItems.size +
            p.customerTransactions.size + p.supplierTransactions.size + p.stockMovements.size + p.cashTransactions.size +
            p.expenses.size + p.transfers.size + p.returns.size + p.returnItems.size + p.notes.size +
            p.settings.size + p.users.size + p.activityLogs.size + p.backupHistory.size

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
