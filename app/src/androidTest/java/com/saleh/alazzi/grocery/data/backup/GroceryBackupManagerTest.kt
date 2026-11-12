package com.saleh.alazzi.grocery.data.backup

import android.content.Context
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GroceryBackupManagerTest {
    private lateinit var database: GroceryDatabase
    private lateinit var manager: GroceryBackupManager
    private lateinit var backupFile: File
    private lateinit var backupUri: android.net.Uri

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, GroceryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = GroceryBackupManager(context, database)
        backupFile = File(context.cacheDir, "backup_test.json")
        backupUri = FileProvider.getUriForFile(context, "${context.packageName}.files", backupFile)
    }

    @After
    fun tearDown() {
        database.close()
        backupFile.delete()
    }

    @Test
    fun exportsChecksummedJsonAndRestoresAtomically() = runBlocking {
        val exported = manager.exportTo(backupUri)
        assertEquals(0, exported.recordCount)
        database.customers().insert(
            CustomerEntity("temporary", "سجل مؤقت", createdAt = 1L, updatedAt = 1L)
        )
        assertNotNull(database.customers().findById("temporary"))

        val restored = manager.restoreFrom(backupUri, confirmed = true)

        assertNull(database.customers().findById("temporary"))
        assertNotNull(restored.preRestoreFile)
        assertEquals(0, restored.recordCount)
    }

    @Test
    fun refusesRestoreWithoutExplicitConfirmation() = runBlocking {
        manager.exportTo(backupUri)
        val error = runCatching { manager.restoreFrom(backupUri, confirmed = false) }.exceptionOrNull()
        assertNotNull(error)
    }
}
