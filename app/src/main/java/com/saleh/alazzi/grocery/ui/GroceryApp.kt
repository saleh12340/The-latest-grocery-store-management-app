package com.saleh.alazzi.grocery.ui

import android.net.Uri
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.saleh.alazzi.grocery.printing.InvoiceExporter
import com.saleh.alazzi.grocery.printing.BluetoothPrinterClient
import com.saleh.alazzi.grocery.printing.PairedPrinter
import com.saleh.alazzi.grocery.printing.ReceiptData
import com.saleh.alazzi.grocery.printing.ReceiptItem
import com.saleh.alazzi.grocery.printing.ReceiptRenderer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.local.entity.SupplierEntity
import com.saleh.alazzi.grocery.data.repository.CreateInvoiceInput
import com.saleh.alazzi.grocery.data.repository.InvoiceLineInput
import com.saleh.alazzi.grocery.domain.money.MoneyMath
import java.math.BigDecimal
import java.util.UUID

private object Routes {
    const val HOME = "home"
    const val CUSTOMERS = "customers"
    const val SUPPLIERS = "suppliers"
    const val INVENTORY = "inventory"
    const val SALE = "sale"
    const val PURCHASE = "purchase"
    const val REPORTS = "reports"
    const val NOTES = "notes"
    const val PRINTER = "printer"
    const val EXPENSES = "expenses"
    const val TRANSFERS = "transfers"
    const val BACKUP = "backup"
    const val USERS = "users"
    const val INVOICES = "invoices"
    const val SALES_HISTORY = "sales-history"
    const val PURCHASE_HISTORY = "purchase-history"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryApp(viewModel: GroceryViewModel, backupManager: GroceryBackupManager) {
    val nav = rememberNavController()
    val customers by viewModel.customers.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val items by viewModel.items.collectAsState()
    val lowStock by viewModel.lowStockItems.collectAsState()
    val cash by viewModel.cashBalance.collectAsState()
    val salesToday by viewModel.salesToday.collectAsState()
    val purchasesToday by viewModel.purchasesToday.collectAsState()
    val invoicesToday by viewModel.invoicesToday.collectAsState()
    val grossProfitToday by viewModel.grossProfitToday.collectAsState()
    val salesInvoices by viewModel.salesInvoices.collectAsState()
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val transfers by viewModel.transfers.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val users by viewModel.users.collectAsState()
    val session by viewModel.session.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: Routes.HOME

    if (users.isEmpty()) { AdminSetupScreen(viewModel); return }
    if (session == null) { LoginScreen(viewModel); return }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("بقالة العزي للمواد الغذائية", fontWeight = FontWeight.Bold)
                        Text("نظام الإدارة والمحاسبة", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = { TextButton(onClick = { viewModel.logout() }) { Text("خروج") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                val destinations = if (session?.role == "MANAGER") listOf(
                    Triple(Routes.HOME, "الرئيسية", Icons.Default.Home),
                    Triple(Routes.CUSTOMERS, "العملاء", Icons.Default.People),
                    Triple(Routes.SUPPLIERS, "الموردون", Icons.Default.Store),
                    Triple(Routes.INVENTORY, "المخزون", Icons.Default.Inventory2)
                ) else listOf(
                    Triple(Routes.HOME, "الرئيسية", Icons.Default.Home),
                    Triple(Routes.CUSTOMERS, "العملاء", Icons.Default.People)
                )
                destinations.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    customers = customers.size,
                    suppliers = suppliers.size,
                    itemCount = items.size,
                    lowStockCount = lowStock.size,
                    cashBalance = cash,
                    salesToday = salesToday,
                    purchasesToday = purchasesToday,
                    invoicesToday = invoicesToday,
                    grossProfitToday = grossProfitToday,
                    isManager = session?.role == "MANAGER",
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.CUSTOMERS) { CustomerScreen(customers, viewModel) { nav.navigate("customer/$it") } }
            composable("customer/{accountId}", arguments = listOf(navArgument("accountId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("accountId").orEmpty()
                CustomerAccountScreen(id, customers.firstOrNull { it.id == id }, viewModel)
            }
            composable(Routes.SUPPLIERS) { if (session?.role == "MANAGER") SupplierScreen(suppliers, viewModel) { nav.navigate("supplier/$it") } else EmptyState("غير مصرح", "هذه الشاشة للمدير فقط") }
            composable("supplier/{accountId}", arguments = listOf(navArgument("accountId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("accountId").orEmpty()
                SupplierAccountScreen(id, suppliers.firstOrNull { it.id == id }, viewModel)
            }
            composable(Routes.INVENTORY) { if (session?.role == "MANAGER") InventoryScreen(items, lowStock.map { it.id }.toSet(), viewModel) else EmptyState("غير مصرح", "هذه الشاشة للمدير فقط") }
            composable(Routes.SALE) { InvoiceEntryScreen(true, items, customers, suppliers, viewModel) }
            composable(Routes.INVOICES) {
                InvoiceHubScreen(
                    isManager = session?.role == "MANAGER",
                    onSales = { nav.navigate(Routes.SALES_HISTORY) },
                    onPurchases = { nav.navigate(Routes.PURCHASE_HISTORY) }
                )
            }
            composable(Routes.SALES_HISTORY) { SalesInvoiceHistoryScreen(salesInvoices) { nav.navigate("sales-invoice/$it") } }
            composable(Routes.PURCHASE_HISTORY) { if (session?.role == "MANAGER") PurchaseInvoiceHistoryScreen(purchaseInvoices) { nav.navigate("purchase-invoice/$it") } else EmptyState("غير مصرح", "فواتير المشتريات للمدير فقط") }
            composable("sales-invoice/{invoiceId}", arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })) { entry ->
                SalesInvoiceDetailScreen(entry.arguments?.getString("invoiceId").orEmpty(), viewModel) { nav.popBackStack() }
            }
            composable("purchase-invoice/{invoiceId}", arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })) { entry ->
                PurchaseInvoiceDetailScreen(entry.arguments?.getString("invoiceId").orEmpty(), viewModel) { nav.popBackStack() }
            }

            composable(Routes.PURCHASE) { if (session?.role == "MANAGER") InvoiceEntryScreen(false, items, customers, suppliers, viewModel) else EmptyState("غير مصرح", "المشتريات للمدير فقط") }
            composable(Routes.REPORTS) { if (session?.role == "MANAGER") ReportsScreen(cash, customers, suppliers, items, lowStock) else EmptyState("غير مصرح", "التقارير للمدير فقط") }
            composable(Routes.NOTES) { NotesScreen(notes, viewModel) }
            composable(Routes.PRINTER) { PrinterScreen() }
            composable(Routes.EXPENSES) { if (session?.role == "MANAGER") ExpensesScreen(expenses, viewModel) else EmptyState("غير مصرح", "المصروفات للمدير فقط") }
            composable(Routes.TRANSFERS) { if (session?.role == "MANAGER") TransfersScreen(transfers, viewModel) else EmptyState("غير مصرح", "الحوالات للمدير فقط") }
            composable(Routes.BACKUP) { if (session?.role == "MANAGER") BackupScreen(backupManager) else EmptyState("غير مصرح", "النسخ الاحتياطي للمدير فقط") }
            composable(Routes.USERS) { if (session?.role == "MANAGER") UsersScreen(users, viewModel) else EmptyState("غير مصرح", "إدارة المستخدمين للمدير فقط") }
        }
    }
}

@Composable
private fun HomeScreen(
    customers: Int,
    suppliers: Int,
    itemCount: Int,
    lowStockCount: Int,
    cashBalance: Long,
    salesToday: Long,
    purchasesToday: Long,
    invoicesToday: Long,
    grossProfitToday: Long,
    isManager: Boolean,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Text("مرحباً بك", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("إدارة البقالة من مكان واحد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("مبيعات اليوم", salesToday.toString(), Color(0xFF155E9B), Modifier.weight(1f))
                MetricCard("مشتريات اليوم", purchasesToday.toString(), Color(0xFF155E9B), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("رصيد الصندوق", cashBalance.toString(), Color(0xFF155E9B), Modifier.weight(1f))
                MetricCard("أصناف المخزون", itemCount.toString(), Color(0xFF16804A), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("الربح الإجمالي اليوم", grossProfitToday.toString(), if (grossProfitToday >= 0) Color(0xFF16804A) else Color(0xFFB42318), Modifier.weight(1f))
                MetricCard("فواتير البيع", invoicesToday.toString(), Color(0xFF155E9B), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("العملاء", customers.toString(), Color(0xFF155E9B), Modifier.weight(1f))
                MetricCard("الموردون", suppliers.toString(), Color(0xFF155E9B), Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (lowStockCount > 0) Color(0xFFFFF1E8) else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (lowStockCount > 0) Color(0xFFB54708) else MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("تنبيهات المخزون", fontWeight = FontWeight.Bold)
                        Text(if (lowStockCount == 0) "لا توجد أصناف تحت حد التنبيه" else "$lowStockCount صنف يحتاج إلى مراجعة")
                    }
                    TextButton(onClick = { onNavigate(Routes.INVENTORY) }) { Text("عرض") }
                }
            }
        }
        item { Text("الإجراءات السريعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("بيع جديد", Icons.Default.PointOfSale, Modifier.weight(1f)) { onNavigate(Routes.SALE) }
                if (isManager) ActionCard("شراء جديد", Icons.Default.ReceiptLong, Modifier.weight(1f)) { onNavigate(Routes.PURCHASE) }
            }
        }
        item { ActionCard("الفواتير", Icons.Default.ReceiptLong, Modifier.fillMaxWidth()) { onNavigate(Routes.INVOICES) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("العملاء", Icons.Default.People, Modifier.weight(1f)) { onNavigate(Routes.CUSTOMERS) }
                if (isManager) ActionCard("الموردون", Icons.Default.Store, Modifier.weight(1f)) { onNavigate(Routes.SUPPLIERS) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isManager) ActionCard("التقارير", Icons.Default.Assessment, Modifier.weight(1f)) { onNavigate(Routes.REPORTS) }
                ActionCard("الملاحظات", Icons.Default.ReceiptLong, Modifier.weight(1f)) { onNavigate(Routes.NOTES) }
            }
        }
        if (isManager) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard("المصروفات", Icons.Default.AccountBalance, Modifier.weight(1f)) { onNavigate(Routes.EXPENSES) }
                    ActionCard("الحوالات", Icons.Default.ReceiptLong, Modifier.weight(1f)) { onNavigate(Routes.TRANSFERS) }
                }
            }
            item { ActionCard("النسخ الاحتياطي والاستعادة", Icons.Default.AccountBalance, Modifier.fillMaxWidth()) { onNavigate(Routes.BACKUP) } }
            item { ActionCard("إدارة المستخدمين", Icons.Default.People, Modifier.fillMaxWidth()) { onNavigate(Routes.USERS) } }
        }
        item { ActionCard("الطباعة الحرارية", Icons.Default.ReceiptLong, Modifier.fillMaxWidth()) { onNavigate(Routes.PRINTER) } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun ActionCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            Text(label, Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun CustomerScreen(values: List<CustomerEntity>, viewModel: GroceryViewModel, onOpen: (String) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("العملاء", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("إضافة") }
        }
        if (values.isEmpty()) EmptyState("لا يوجد عملاء بعد", "أضف أول عميل للبدء")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { customer ->
                Card(modifier = Modifier.clickable { onOpen(customer.id) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(customer.name, fontWeight = FontWeight.Bold)
                        Text(customer.phone ?: "لا يوجد رقم هاتف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الرصيد المستحق: ${customer.balance}", color = if (customer.balance > 0) Color(0xFFB42318) else Color(0xFF16804A))
                    }
                }
            }
        }
    }
    if (showAdd) AddPersonDialog("إضافة عميل", viewModel::addCustomer) { showAdd = false }
}

@Composable
private fun SupplierScreen(values: List<SupplierEntity>, viewModel: GroceryViewModel, onOpen: (String) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الموردون", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("إضافة") }
        }
        if (values.isEmpty()) EmptyState("لا يوجد موردون بعد", "أضف أول مورد للبدء")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { supplier ->
                Card(modifier = Modifier.clickable { onOpen(supplier.id) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(supplier.name, fontWeight = FontWeight.Bold)
                        Text(supplier.phone ?: "لا يوجد رقم هاتف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("المستحق للمورد: ${supplier.balance}", color = if (supplier.balance > 0) Color(0xFFB42318) else Color(0xFF16804A))
                    }
                }
            }
        }
    }
    if (showAdd) AddPersonDialog("إضافة مورد", viewModel::addSupplier) { showAdd = false }
}

@Composable
private fun AddPersonDialog(title: String, onSave: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(address, { address = it }, label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone, address, notes); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun InventoryScreen(values: List<ItemEntity>, lowStockIds: Set<String>, viewModel: GroceryViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var adjustTarget by remember { mutableStateOf<ItemEntity?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المخزون والأصناف", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("صنف") }
        }
        if (values.isEmpty()) EmptyState("المخزون فارغ", "أضف الأصناف والكميات الافتتاحية")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { item ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (item.id in lowStockIds) Text("مخزون منخفض", color = Color(0xFFB42318), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("الكمية: ${BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString()}")
                        Text("شراء: ${item.purchasePrice}   بيع: ${item.salePrice}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { adjustTarget = item }) { Text("تسوية كمية المخزون") }
                    }
                }
            }
        }
    }
    if (showAdd) AddItemDialog(viewModel) { showAdd = false }
    adjustTarget?.let { item -> StockAdjustmentDialog(item, viewModel) { adjustTarget = null } }
}


@Composable
private fun StockAdjustmentDialog(item: ItemEntity, viewModel: GroceryViewModel, onDismiss: () -> Unit) {
    var quantity by rememberSaveable(item.id) {
        mutableStateOf(BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString())
    }
    var note by remember { mutableStateOf("") }
    var operationId by rememberSaveable(item.id) { mutableStateOf(UUID.randomUUID().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسوية مخزون: ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الرصيد الحالي: ${BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString()}")
                OutlinedTextField(quantity, { quantity = it }, label = { Text("الكمية الفعلية الجديدة") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, label = { Text("سبب التسوية") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val milli = runCatching { BigDecimal(quantity).multiply(BigDecimal("1000")).longValueExact() }.getOrNull()
                if (milli != null && milli >= 0 && note.isNotBlank()) {
                    viewModel.adjustStock(item.id, milli, note, operationId)
                    onDismiss()
                }
            }) { Text("حفظ التسوية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddItemDialog(viewModel: GroceryViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("0") }
    var purchase by remember { mutableStateOf("0") }
    var sale by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صنف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الصنف") }, singleLine = true)
                OutlinedTextField(barcode, { barcode = it }, label = { Text("الباركود") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("الكمية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(purchase, { purchase = it }, label = { Text("سعر الشراء") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(sale, { sale = it }, label = { Text("سعر البيع") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { Button(onClick = { viewModel.addItem(name, barcode, quantity, purchase, sale); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private data class DraftInvoiceLine(
    val itemId: String,
    val name: String,
    val quantityMilli: Long,
    val unitPrice: Long,
    val lineTotal: Long
)

@Composable
private fun InvoiceEntryScreen(
    isSale: Boolean,
    items: List<ItemEntity>,
    customers: List<CustomerEntity>,
    suppliers: List<SupplierEntity>,
    viewModel: GroceryViewModel
) {
    val context = LocalContext.current
    val exporter = remember(context) { InvoiceExporter(context) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var itemQuery by rememberSaveable { mutableStateOf("") }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unitPrice by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("0") }
    var paid by rememberSaveable { mutableStateOf("0") }
    var cart by remember { mutableStateOf<List<DraftInvoiceLine>>(emptyList()) }
    var operationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var saving by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var pendingReceipt by remember { mutableStateOf<ReceiptData?>(null) }
    var pendingPhone by remember { mutableStateOf<String?>(null) }
    var receiptMessage by remember { mutableStateOf("") }

    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val filteredItems = if (itemQuery.isBlank()) items else items.filter { item ->
        item.name.contains(itemQuery.trim(), ignoreCase = true) || (item.barcode?.contains(itemQuery.trim(), ignoreCase = true) == true)
    }
    val accounts = if (isSale) customers.map { it.id to it.name } else suppliers.map { it.id to it.name }
    val selectedAccountName = accounts.firstOrNull { it.first == selectedAccountId }?.second
    val quantityMilli = runCatching { BigDecimal(quantity).multiply(BigDecimal("1000")).longValueExact() }.getOrNull()
    val priceValue = unitPrice.toLongOrNull()
    val draftLineTotal = if (quantityMilli != null && priceValue != null && quantityMilli > 0 && priceValue >= 0) {
        runCatching { MoneyMath.lineTotal(quantityMilli, priceValue) }.getOrNull()
    } else null
    val subtotal = runCatching { cart.fold(0L) { sum, line -> Math.addExact(sum, line.lineTotal) } }.getOrNull()
    val discountValue = discount.toLongOrNull()
    val finalTotal = if (subtotal != null && discountValue != null && discountValue >= 0 && discountValue <= subtotal) {
        runCatching { MoneyMath.total(listOf(subtotal), discountValue) }.getOrNull()
    } else null
    val paidValue = paid.toLongOrNull()
    val remaining = if (finalTotal != null && paidValue != null && paidValue in 0..finalTotal) finalTotal - paidValue else null

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(if (isSale) "فاتورة بيع جديدة" else "فاتورة شراء جديدة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedAccountName ?: if (isSale) "بيع نقدي بدون عميل" else "شراء نقدي بدون مورد")
        }
        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
            DropdownMenuItem(text = { Text(if (isSale) "بيع نقدي بدون عميل" else "شراء نقدي بدون مورد") }, onClick = { selectedAccountId = null; accountMenu = false })
            accounts.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { selectedAccountId = id; accountMenu = false }) }
        }

        Text("اختر صنفاً لإضافته", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = itemQuery,
            onValueChange = { itemQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("بحث باسم الصنف أو الباركود") },
            singleLine = true
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (filteredItems.isEmpty()) item { EmptyState("لا توجد أصناف مطابقة", "تحقق من الاسم أو الباركود") }
            items(filteredItems, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedItemId = item.id
                        unitPrice = (if (isSale) item.salePrice else item.purchasePrice).toString()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedItemId == item.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            Text("المتوفر: ${BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (isSale) item.salePrice.toString() else item.purchasePrice.toString())
                    }
                }
            }
        }

        if (selectedItem != null) {
            Text("الصنف المحدد: ${selectedItem.name}", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, modifier = Modifier.weight(1f), label = { Text("الكمية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(unitPrice, { unitPrice = it }, modifier = Modifier.weight(1f), label = { Text("سعر الوحدة") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("إجمالي السطر: ${draftLineTotal ?: "—"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    val qty = quantityMilli ?: return@Button
                    val price = priceValue ?: return@Button
                    val total = draftLineTotal ?: return@Button
                    val existing = cart.firstOrNull { it.itemId == selectedItem.id }
                    cart = if (existing == null) {
                        cart + DraftInvoiceLine(selectedItem.id, selectedItem.name, qty, price, total)
                    } else {
                        val combinedQty = Math.addExact(existing.quantityMilli, qty)
                        cart.map {
                            if (it.itemId == selectedItem.id) it.copy(
                                quantityMilli = combinedQty,
                                lineTotal = MoneyMath.lineTotal(combinedQty, existing.unitPrice)
                            ) else it
                        }
                    }
                    selectedItemId = null
                    quantity = "1"
                    unitPrice = ""
                },
                enabled = quantityMilli != null && quantityMilli > 0 && priceValue != null && priceValue >= 0 && draftLineTotal != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("إضافة الصنف إلى الفاتورة") }
        }

        Text("أصناف الفاتورة: ${cart.size}", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.heightIn(max = 118.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(cart, key = { it.itemId }) { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.name, Modifier.weight(1f), maxLines = 1)
                    Text("${BigDecimal.valueOf(line.quantityMilli, 3).stripTrailingZeros().toPlainString()} × ${line.unitPrice} = ${line.lineTotal}")
                    TextButton(onClick = { cart = cart.filterNot { it.itemId == line.itemId } }) { Text("حذف") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(discount, { discount = it }, modifier = Modifier.weight(1f), label = { Text("الخصم") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(paid, { paid = it }, modifier = Modifier.weight(1f), label = { Text("المدفوع") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Text("الإجمالي: ${subtotal ?: "—"}    بعد الخصم: ${finalTotal ?: "—"}    المتبقي: ${remaining ?: "—"}", fontWeight = FontWeight.Bold)
        if (selectedAccountId == null && remaining != null && remaining > 0) {
            Text("الفاتورة النقدية تتطلب سداد كامل الإجمالي أو اختيار حساب", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val sub = subtotal ?: return@Button
                val disc = discountValue ?: return@Button
                val total = finalTotal ?: return@Button
                val paidNow = paidValue ?: return@Button
                val remainingNow = remaining ?: return@Button
                if (cart.isEmpty() || saving) return@Button
                saving = true
                val cartSnapshot = cart.toList()
                val partyPhone = if (isSale) customers.firstOrNull { it.id == selectedAccountId }?.phone
                    else suppliers.firstOrNull { it.id == selectedAccountId }?.phone
                val partyName = selectedAccountName
                val request = CreateInvoiceInput(
                    operationId = operationId,
                    customerOrSupplierId = selectedAccountId,
                    lines = cartSnapshot.map { InvoiceLineInput(it.itemId, it.quantityMilli, it.unitPrice) },
                    discount = disc,
                    paid = paidNow,
                    paymentMethod = "CASH"
                )
                val finish: (Boolean) -> Unit = { success ->
                    saving = false
                    if (success) {
                        operationId = UUID.randomUUID().toString()
                        selectedItemId = null
                        quantity = "1"
                        unitPrice = ""
                        discount = "0"
                        paid = "0"
                        cart = emptyList()
                    }
                }
                val saved: (com.saleh.alazzi.grocery.data.repository.SaveInvoiceResult) -> Unit = { result ->
                    val cumulative = if (selectedAccountId == null) null else {
                        val previous = if (isSale) customers.firstOrNull { it.id == selectedAccountId }?.balance
                            else suppliers.firstOrNull { it.id == selectedAccountId }?.balance
                        (previous ?: 0L) + remainingNow
                    }
                    pendingPhone = partyPhone
                    pendingReceipt = ReceiptData(
                        invoiceType = if (isSale) "فاتورة بيع" else "فاتورة شراء",
                        invoiceNumber = result.invoiceNumber,
                        dateTime = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar")).format(java.util.Date()),
                        partyName = partyName,
                        items = cartSnapshot.map { line ->
                            ReceiptItem(
                                name = line.name,
                                quantity = BigDecimal.valueOf(line.quantityMilli, 3).stripTrailingZeros().toPlainString(),
                                unitPrice = line.unitPrice,
                                total = line.lineTotal
                            )
                        },
                        subtotal = sub,
                        discount = disc,
                        total = total,
                        paid = paidNow,
                        remaining = remainingNow,
                        accountBalance = cumulative
                    )
                    receiptMessage = ""
                }
                if (isSale) viewModel.createSale(request, finish, saved)
                else viewModel.createPurchase(request, finish, saved)
            },
            enabled = cart.isNotEmpty() && finalTotal != null && paidValue != null && paidValue >= 0 && paidValue <= (finalTotal ?: 0L) && (selectedAccountId != null || remaining == 0L) && !saving,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "جارٍ الحفظ..." else "حفظ الفاتورة") }
    }

    pendingReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { pendingReceipt = null },
            title = { Text("تم حفظ الفاتورة رقم ${receipt.invoiceNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("يمكنك حفظ صورة أو PDF أو مشاركة نسخة الفاتورة.")
                    if (receiptMessage.isNotBlank()) Text(receiptMessage, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 384)
                            try { exporter.savePng(bitmap, "Invoice_${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                        }.onSuccess { receiptMessage = "تم حفظ صورة الفاتورة" }
                            .onFailure { receiptMessage = it.message ?: "تعذر حفظ الصورة" }
                    }) { Text("حفظ صورة الفاتورة") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            try { exporter.savePdf(bitmap, "Invoice_${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                        }.onSuccess { receiptMessage = "تم حفظ ملف PDF" }
                            .onFailure { receiptMessage = it.message ?: "تعذر إنشاء PDF" }
                    }) { Text("حفظ PDF") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            val uri = try { exporter.savePng(bitmap, "Invoice_${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                            exporter.share(uri, "image/png")
                        }.onFailure { receiptMessage = it.message ?: "تعذرت المشاركة" }
                    }) { Text("مشاركة صورة الفاتورة") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            val uri = try { exporter.savePdf(bitmap, "Invoice_${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                            exporter.share(uri, "application/pdf")
                        }.onFailure { receiptMessage = it.message ?: "تعذرت مشاركة PDF" }
                    }) { Text("مشاركة PDF") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            val uri = try { exporter.savePng(bitmap, "Invoice_${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                            exporter.shareToWhatsApp(uri, "image/png")
                        }.onFailure { receiptMessage = it.message ?: "تعذر فتح WhatsApp أو مشاركة الفاتورة" }
                    }) { Text("مشاركة عبر WhatsApp") }
                    if (!pendingPhone.isNullOrBlank()) {
                        TextButton(onClick = {
                            runCatching { exporter.openWhatsAppChat(pendingPhone.orEmpty()) }
                                .onFailure { receiptMessage = it.message ?: "تعذر فتح محادثة WhatsApp" }
                        }) { Text("فتح محادثة WhatsApp") }
                    }
                }
            },
            confirmButton = { Button(onClick = { pendingReceipt = null }) { Text("إغلاق") } }
        )
    }
}


@Composable
private fun CustomerAccountScreen(id: String, customer: CustomerEntity?, viewModel: GroceryViewModel) {
    val transactions by viewModel.customerTransactions(id).collectAsState(initial = emptyList())
    var showPayment by remember { mutableStateOf(false) }
    if (customer == null) { EmptyState("العميل غير موجود", "قد يكون السجل غير متاح"); return }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(customer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(customer.phone ?: "لا يوجد رقم هاتف")
        Text("الرصيد المستحق: ${customer.balance}", color = Color(0xFFB42318), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { showPayment = true }, modifier = Modifier.fillMaxWidth()) { Text("قبض من العميل") }
        Text("سجل العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (transactions.isEmpty()) EmptyState("لا توجد عمليات", "ستظهر الفواتير والدفعات هنا")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.id }) { tx ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text(if (tx.type == "PAYMENT") "تسديد / قبض" else tx.type, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(tx.amount.toString(), color = if (tx.type == "PAYMENT") Color(0xFF16804A) else Color(0xFFB42318))
                        }
                        Text(tx.note ?: tx.sourceType, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الرصيد بعد العملية: ${tx.balanceAfter}")
                    }
                }
            }
        }
    }
    if (showPayment) PaymentDialog(
        title = "قبض من العميل",
        maxAmount = customer.balance,
        onSave = { amount, note, operationId -> viewModel.receiveCustomerPayment(id, amount, note, operationId); showPayment = false },
        onDismiss = { showPayment = false }
    )
}

@Composable
private fun SupplierAccountScreen(id: String, supplier: SupplierEntity?, viewModel: GroceryViewModel) {
    val transactions by viewModel.supplierTransactions(id).collectAsState(initial = emptyList())
    var showPayment by remember { mutableStateOf(false) }
    if (supplier == null) { EmptyState("المورد غير موجود", "قد يكون السجل غير متاح"); return }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(supplier.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(supplier.phone ?: "لا يوجد رقم هاتف")
        Text("المبلغ المستحق للمورد: ${supplier.balance}", color = Color(0xFFB42318), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { showPayment = true }, modifier = Modifier.fillMaxWidth()) { Text("دفع للمورد") }
        Text("سجل العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (transactions.isEmpty()) EmptyState("لا توجد عمليات", "ستظهر فواتير الشراء والدفعات هنا")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.id }) { tx ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text(if (tx.type == "PAYMENT") "دفع للمورد" else tx.type, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(tx.amount.toString(), color = if (tx.type == "PAYMENT") Color(0xFF16804A) else Color(0xFFB42318))
                        }
                        Text(tx.note ?: tx.sourceType, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الرصيد بعد العملية: ${tx.balanceAfter}")
                    }
                }
            }
        }
    }
    if (showPayment) PaymentDialog(
        title = "دفع للمورد",
        maxAmount = supplier.balance,
        onSave = { amount, note, operationId -> viewModel.paySupplier(id, amount, note, operationId); showPayment = false },
        onDismiss = { showPayment = false }
    )
}

@Composable
private fun PaymentDialog(title: String, maxAmount: Long, onSave: (Long, String, String) -> Unit, onDismiss: () -> Unit) {
    var operationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الرصيد الحالي: $maxAmount")
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("البيان") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = amount.toLongOrNull()
                if (value != null && value > 0 && value <= maxAmount) onSave(value, note, operationId)
            }) { Text("حفظ العملية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}


@Composable
private fun PrinterScreen() {
    val context = LocalContext.current
    val client = remember(context) { BluetoothPrinterClient(context) }
    val scope = rememberCoroutineScope()
    var printers by remember { mutableStateOf(emptyList<PairedPrinter>()) }
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var paperWidth by rememberSaveable { mutableStateOf(384) }
    var status by remember { mutableStateOf("اعرض الطابعات المقترنة بالهاتف أولاً") }
    var busy by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                busy = true
                runCatching { client.pairedPrinters() }
                    .onSuccess { found ->
                        printers = found
                        if (selectedAddress !in found.map { it.address }) selectedAddress = found.firstOrNull()?.address
                        status = if (found.isEmpty()) "لا توجد طابعات مقترنة. اربط الطابعة من إعدادات Bluetooth في الهاتف." else "تم العثور على ${found.size} جهاز مقترن"
                    }
                    .onFailure { status = it.message ?: "تعذر قراءة أجهزة Bluetooth" }
                busy = false
            }
        } else status = "لم يتم منح إذن Bluetooth"
    }

    fun loadPrinters() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            scope.launch {
                busy = true
                runCatching { client.pairedPrinters() }
                    .onSuccess { found ->
                        printers = found
                        if (selectedAddress !in found.map { it.address }) selectedAddress = found.firstOrNull()?.address
                        status = if (found.isEmpty()) "لا توجد طابعات مقترنة. اربط الطابعة من إعدادات Bluetooth في الهاتف." else "تم العثور على ${found.size} جهاز مقترن"
                    }
                    .onFailure { status = it.message ?: "تعذر قراءة أجهزة Bluetooth" }
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الطباعة الحرارية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("يدعم الاتصال بطابعات ESC/POS المقترنة عبر Bluetooth. لا يبدأ البحث عن أجهزة غير مقترنة.")
        Button(onClick = { loadPrinters() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "جارٍ الفحص..." else "عرض الطابعات المقترنة")
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("عرض الورق")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { paperWidth = 384 }) { Text(if (paperWidth == 384) "✓ 58mm" else "58mm") }
            FilledTonalButton(onClick = { paperWidth = 576 }) { Text(if (paperWidth == 576) "✓ 80mm" else "80mm") }
        }
        Text("اختر الطابعة", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(printers, key = { it.address }) { printer ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedAddress = printer.address },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedAddress == printer.address) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(printer.name, fontWeight = FontWeight.Bold)
                        Text(printer.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            enabled = selectedAddress != null && !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val address = selectedAddress ?: return@Button
                scope.launch {
                    busy = true
                    status = "جارٍ إرسال إيصال الاختبار..."
                    val bitmap = ReceiptRenderer.render(
                        ReceiptData(
                            invoiceType = "اختبار الطباعة",
                            invoiceNumber = "TEST-001",
                            dateTime = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar")).format(java.util.Date()),
                            partyName = null,
                            items = listOf(ReceiptItem("اختبار وضوح العربية", "1", 100, 100)),
                            subtotal = 100,
                            discount = 0,
                            total = 100,
                            paid = 100,
                            remaining = 0
                        ),
                        paperWidth
                    )
                    try {
                        client.print(bitmap, address, paperWidth)
                        status = "تم إرسال بيانات الاختبار إلى الطابعة"
                    } catch (error: Throwable) {
                        status = error.message ?: "فشلت الطباعة"
                    } finally {
                        bitmap.recycle()
                        busy = false
                    }
                }
            }
        ) { Text("طباعة إيصال اختبار") }
    }
}


@Composable
private fun ExpensesScreen(values: List<com.saleh.alazzi.grocery.data.local.entity.ExpenseEntity>, viewModel: GroceryViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المصروفات", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("مصروف") }
        }
        if (values.isEmpty()) EmptyState("لا توجد مصروفات", "سجّل المصروفات لمتابعة حركة الصندوق")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { expense ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text(expense.category, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("-${expense.amount}", color = Color(0xFFB42318))
                        }
                        Text(expense.description ?: "بدون وصف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showAdd) {
        var category by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var operationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("تسجيل مصروف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(category, { category = it }, label = { Text("نوع المصروف") }, singleLine = true)
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("الوصف") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val value = amount.toLongOrNull()
                    if (value != null && value > 0 && category.isNotBlank()) {
                        viewModel.recordExpense(category, value, description, operationId)
                        showAdd = false
                    }
                }) { Text("حفظ المصروف") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun TransfersScreen(values: List<com.saleh.alazzi.grocery.data.local.entity.TransferEntity>, viewModel: GroceryViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الحوالات", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("حوالة") }
        }
        if (values.isEmpty()) EmptyState("لا توجد حوالات", "ستظهر الحوالات المسجلة هنا")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { transfer ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text(transfer.recipientName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(transfer.amount.toString())
                        }
                        Text(transfer.phone)
                        Text("الرسوم: ${transfer.fees}   الصافي: ${transfer.netAmount}")
                        Text(if (transfer.direction == "OUT") "صادرة" else "واردة", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showAdd) {
        var recipient by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var fees by remember { mutableStateOf("0") }
        var note by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf("OUT") }
        var operationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("تسجيل حوالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(recipient, { recipient = it }, label = { Text("اسم المستلم") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { direction = "OUT" }) { Text(if (direction == "OUT") "✓ صادرة" else "صادرة") }
                        FilledTonalButton(onClick = { direction = "IN" }) { Text(if (direction == "IN") "✓ واردة" else "واردة") }
                    }
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(fees, { fees = it }, label = { Text("الرسوم") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("ملاحظات") })
                    val a = amount.toLongOrNull() ?: 0L
                    val f = fees.toLongOrNull() ?: 0L
                    Text("الصافي / حركة الصندوق: ${if (direction == "OUT") a + f else a - f}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val a = amount.toLongOrNull()
                    val f = fees.toLongOrNull()
                    if (recipient.isNotBlank() && a != null && f != null && a > 0 && f >= 0) {
                        viewModel.recordTransfer(recipient, phone, a, f, direction, note, operationId)
                        showAdd = false
                    }
                }) { Text("حفظ الحوالة") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }
}


@Composable
private fun NotesScreen(values: List<com.saleh.alazzi.grocery.data.local.entity.NoteEntity>, viewModel: GroceryViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("دفتر الملاحظات", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("ملاحظة") }
        }
        if (values.isEmpty()) EmptyState("دفتر الملاحظات فارغ", "أضف ملاحظاتك المهمة هنا")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { note ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.title.ifBlank { "ملاحظة" }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { viewModel.toggleNotePin(note) }) { Text(if (note.isPinned) "إلغاء التثبيت" else "تثبيت") }
                            TextButton(onClick = { viewModel.deleteNote(note.id) }) { Text("حذف", color = Color(0xFFB42318)) }
                        }
                        Text(note.body)
                    }
                }
            }
        }
    }
    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("إضافة ملاحظة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("المحتوى") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() || body.isNotBlank()) {
                        viewModel.addNote(title, body)
                        showAdd = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }
}


@Composable
private fun BackupScreen(manager: GroceryBackupManager) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("احفظ نسخة JSON مشفرة من بياناتك قبل تحديث الجهاز أو التطبيق.") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { manager.exportTo(uri, passphrase) }
                .onSuccess { status = "نجح النسخ الاحتياطي: ${it.recordCount} سجل. SHA-256: ${it.checksum}" }
                .onFailure { status = it.message ?: "فشل إنشاء النسخة الاحتياطية" }
            busy = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreUri = uri
            showConfirm = true
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("النسخ الاحتياطي والاستعادة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("يتم تشفير ملف JSON باستخدام AES-GCM ومفتاح مشتق من كلمة مرورك. احتفظ بكلمة المرور لاستعادة النسخة.")
        OutlinedTextField(passphrase, { passphrase = it }, label = { Text("كلمة مرور النسخة (8 أحرف على الأقل)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        Button(
            enabled = !busy && passphrase.length >= 8,
            onClick = { createLauncher.launch("AlAzzi_Backup_${System.currentTimeMillis()}.json") },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "جارٍ التنفيذ..." else "إنشاء نسخة احتياطية") }
        FilledTonalButton(
            enabled = !busy && passphrase.length >= 8,
            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("اختيار نسخة للاستعادة") }
        Text(
            status,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "تنبيه: الاستعادة تستبدل البيانات الحالية بعد التأكيد. ينشئ التطبيق نسخة داخلية قبل الاستعادة، ويتراجع تلقائياً إذا فشلت معاملة قاعدة البيانات.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showConfirm && restoreUri != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false; restoreUri = null },
            title = { Text("تأكيد استعادة البيانات") },
            text = { Text("سيتم استبدال بيانات التطبيق الحالية بالبيانات الموجودة في النسخة المحددة. سيتم حفظ نسخة داخلية من البيانات الحالية أولاً. هل تريد المتابعة؟") },
            confirmButton = {
                Button(onClick = {
                    val uri = restoreUri ?: return@Button
                    showConfirm = false
                    scope.launch {
                        busy = true
                        runCatching { manager.restoreFrom(uri, passphrase, confirmed = true) }
                            .onSuccess {
                                status = "نجحت الاستعادة: ${it.recordCount} سجل. تم حفظ نسخة ما قبل الاستعادة."
                            }
                            .onFailure { status = it.message ?: "فشلت الاستعادة ولم يتم اعتماد التغييرات" }
                        busy = false
                        restoreUri = null
                    }
                }) { Text("تأكيد الاستعادة") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false; restoreUri = null }) { Text("إلغاء") }
            }
        )
    }
}


@Composable
private fun AdminSetupScreen(viewModel: GroceryViewModel) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("إعداد مدير النظام", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("هذه أول مرة يفتح فيها التطبيق. أنشئ PIN للمدير لحماية البيانات والعمليات.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(displayName, { displayName = it }, label = { Text("اسم المدير") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(pin, { pin = it }, label = { Text("PIN من 4 إلى 8 أرقام") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        OutlinedTextField(confirm, { confirm = it }, label = { Text("تأكيد PIN") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Button(
            onClick = { if (pin == confirm) viewModel.createAdminUser(displayName, pin) },
            enabled = displayName.isNotBlank() && pin.length in 4..8 && pin.all(Char::isDigit) && pin == confirm,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text("إنشاء حساب المدير") }
    }
}

@Composable
private fun LoginScreen(viewModel: GroceryViewModel) {
    var username by rememberSaveable { mutableStateOf("admin") }
    var pin by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تسجيل الدخول", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("بقالة العزي للمواد الغذائية")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(username, { username = it }, label = { Text("اسم المستخدم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(pin, { pin = it }, label = { Text("PIN") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Button(onClick = { viewModel.login(username, pin); pin = "" }, enabled = username.isNotBlank() && pin.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("دخول")
        }
    }
}

@Composable
private fun UsersScreen(values: List<com.saleh.alazzi.grocery.data.local.entity.UserEntity>, viewModel: GroceryViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("إدارة المستخدمين", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Text("إضافة كاشير") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { user ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(user.displayName, fontWeight = FontWeight.Bold)
                        Text(user.username)
                        Text(if (user.role == "MANAGER") "مدير" else "كاشير")
                    }
                }
            }
        }
    }
    if (showAdd) {
        var displayName by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("إنشاء حساب كاشير") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(displayName, { displayName = it }, label = { Text("اسم الكاشير") }, singleLine = true)
                    OutlinedTextField(username, { username = it }, label = { Text("اسم المستخدم") }, singleLine = true)
                    OutlinedTextField(pin, { pin = it }, label = { Text("PIN من 4 إلى 8 أرقام") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addCashier(displayName, username, pin)
                    showAdd = false
                }, enabled = displayName.isNotBlank() && username.isNotBlank() && pin.length in 4..8 && pin.all(Char::isDigit)) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }
}


private data class ReturnLineUi(
    val id: String,
    val name: String,
    val quantityMilli: Long,
    val unitPrice: Long,
    val lineTotal: Long
)

@Composable
private fun InvoiceHubScreen(isManager: Boolean, onSales: () -> Unit, onPurchases: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الفواتير", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Button(onClick = onSales, modifier = Modifier.fillMaxWidth()) { Text("فواتير المبيعات") }
        if (isManager) FilledTonalButton(onClick = onPurchases, modifier = Modifier.fillMaxWidth()) { Text("فواتير المشتريات") }
    }
}

@Composable
private fun SalesInvoiceHistoryScreen(values: List<com.saleh.alazzi.grocery.data.local.entity.SalesInvoiceEntity>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("فواتير المبيعات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) EmptyState("لا توجد فواتير بيع", "ستظهر الفواتير بعد حفظها")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { invoice ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(invoice.id) }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text("فاتورة #${invoice.invoiceNumber}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(invoice.total.toString())
                        }
                        Text("المدفوع: ${invoice.paid}   المتبقي: ${invoice.remaining}")
                        Text("الحالة: ${invoice.status}")
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseInvoiceHistoryScreen(values: List<com.saleh.alazzi.grocery.data.local.entity.PurchaseInvoiceEntity>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("فواتير المشتريات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) EmptyState("لا توجد فواتير شراء", "ستظهر الفواتير بعد حفظها")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values, key = { it.id }) { invoice ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(invoice.id) }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text("فاتورة #${invoice.invoiceNumber}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(invoice.total.toString())
                        }
                        Text("المدفوع: ${invoice.paid}   المتبقي: ${invoice.remaining}")
                        Text("الحالة: ${invoice.status}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesInvoiceDetailScreen(id: String, viewModel: GroceryViewModel, onBack: () -> Unit) {
    val invoice by viewModel.salesInvoice(id).collectAsState(initial = null)
    val lines by viewModel.salesInvoiceLines(id).collectAsState(initial = emptyList())
    var showReturn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("رجوع") }
        if (invoice == null) EmptyState("الفاتورة غير موجودة", "تعذر تحميل بيانات الفاتورة")
        else {
            Text("فاتورة بيع #${invoice!!.invoiceNumber}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("الإجمالي: ${invoice!!.total}   المدفوع: ${invoice!!.paid}   المتبقي: ${invoice!!.remaining}")
            Text("الحالة: ${invoice!!.status}")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(lines, key = { it.id }) { line ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(line.itemNameSnapshot, fontWeight = FontWeight.Bold)
                            Text("الكمية: ${line.quantityMilli / 1000.0}   سعر الوحدة: ${line.unitPrice}   الإجمالي: ${line.lineTotal}")
                        }
                    }
                }
            }
            Button(onClick = { showReturn = true }, enabled = invoice!!.status == "POSTED", modifier = Modifier.fillMaxWidth()) { Text("إنشاء مرتجع بيع") }
        }
    }
    if (showReturn && invoice != null) ReturnDialog(
        title = "مرتجع فاتورة البيع",
        lines = lines.map { ReturnLineUi(it.id, it.itemNameSnapshot, it.quantityMilli, it.unitPrice, it.lineTotal) },
        onSave = { returnLines, note, operationId -> viewModel.returnSale(id, returnLines, note, operationId) { saved -> if (saved) showReturn = false } },
        onDismiss = { showReturn = false }
    )
}

@Composable
private fun PurchaseInvoiceDetailScreen(id: String, viewModel: GroceryViewModel, onBack: () -> Unit) {
    val invoice by viewModel.purchaseInvoice(id).collectAsState(initial = null)
    val lines by viewModel.purchaseInvoiceLines(id).collectAsState(initial = emptyList())
    var showReturn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("رجوع") }
        if (invoice == null) EmptyState("الفاتورة غير موجودة", "تعذر تحميل بيانات الفاتورة")
        else {
            Text("فاتورة شراء #${invoice!!.invoiceNumber}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("الإجمالي: ${invoice!!.total}   المدفوع: ${invoice!!.paid}   المتبقي: ${invoice!!.remaining}")
            Text("الحالة: ${invoice!!.status}")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(lines, key = { it.id }) { line ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(line.itemNameSnapshot, fontWeight = FontWeight.Bold)
                            Text("الكمية: ${line.quantityMilli / 1000.0}   سعر الشراء: ${line.unitCost}   الإجمالي: ${line.lineTotal}")
                        }
                    }
                }
            }
            Button(onClick = { showReturn = true }, enabled = invoice!!.status == "POSTED", modifier = Modifier.fillMaxWidth()) { Text("إنشاء مرتجع شراء") }
        }
    }
    if (showReturn && invoice != null) ReturnDialog(
        title = "مرتجع فاتورة الشراء",
        lines = lines.map { ReturnLineUi(it.id, it.itemNameSnapshot, it.quantityMilli, it.unitCost, it.lineTotal) },
        onSave = { returnLines, note, operationId -> viewModel.returnPurchase(id, returnLines, note, operationId) { saved -> if (saved) showReturn = false } },
        onDismiss = { showReturn = false }
    )
}

@Composable
private fun ReturnDialog(
    title: String,
    lines: List<ReturnLineUi>,
    onSave: (List<com.saleh.alazzi.grocery.data.repository.ReturnLineInput>, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var operationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var quantities by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lines, key = { it.id }) { line ->
                        Column {
                            Text(line.name, fontWeight = FontWeight.Bold)
                            Text("الكمية الأصلية: ${line.quantityMilli / 1000.0}")
                            OutlinedTextField(
                                value = quantities[line.id] ?: "",
                                onValueChange = { quantities = quantities + (line.id to it) },
                                label = { Text("الكمية المرتجعة") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("سبب / ملاحظة المرتجع") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val selected = lines.mapNotNull { line ->
                    val raw = quantities[line.id]?.toBigDecimalOrNull() ?: return@mapNotNull null
                    val milli = runCatching { raw.multiply(BigDecimal("1000")).longValueExact() }.getOrNull()
                    if (milli != null && milli > 0) com.saleh.alazzi.grocery.data.repository.ReturnLineInput(line.id, milli) else null
                }
                if (selected.isNotEmpty()) onSave(selected, note, operationId)
            }, enabled = quantities.values.any { it.toBigDecimalOrNull()?.signum() == 1 }) { Text("حفظ المرتجع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ReportsScreen(
    cash: Long,
    customers: List<CustomerEntity>,
    suppliers: List<SupplierEntity>,
    items: List<ItemEntity>,
    lowStock: List<ItemEntity>
) {
    val customerDebt = customers.sumOf { it.balance }
    val supplierDue = suppliers.sumOf { it.balance }
    val stockValue = items.sumOf { item -> runCatching { MoneyMath.lineTotal(item.quantityMilli, item.purchasePrice) }.getOrDefault(0L) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("ملخص الحسابات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { ReportRow("رصيد الصندوق", cash) }
        item { ReportRow("ديون العملاء", customerDebt) }
        item { ReportRow("مستحقات الموردين", supplierDue) }
        item { ReportRow("قيمة المخزون التقديرية", stockValue) }
        item { ReportRow("عدد الأصناف منخفضة المخزون", lowStock.size.toLong()) }
        item {
            Text(
                "هذه مؤشرات أولية مباشرة من قاعدة البيانات. تقارير الأرباح والمبيعات حسب الفترة ستُضاف بعد استكمال احتساب تكلفة البضاعة والمرتجعات.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportRow(label: String, value: Long) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(value.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SimpleInfoScreen(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(message)
    }
}
