package com.saleh.alazzi.grocery.ui

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryApp(viewModel: GroceryViewModel) {
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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: Routes.HOME

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("بقالة العزي للمواد الغذائية", fontWeight = FontWeight.Bold)
                        Text("نظام الإدارة والمحاسبة", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                val destinations = listOf(
                    Triple(Routes.HOME, "الرئيسية", Icons.Default.Home),
                    Triple(Routes.CUSTOMERS, "العملاء", Icons.Default.People),
                    Triple(Routes.SUPPLIERS, "الموردون", Icons.Default.Store),
                    Triple(Routes.INVENTORY, "المخزون", Icons.Default.Inventory2)
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
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.CUSTOMERS) { CustomerScreen(customers, viewModel) { nav.navigate("customer/$it") } }
            composable("customer/{accountId}", arguments = listOf(navArgument("accountId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("accountId").orEmpty()
                CustomerAccountScreen(id, customers.firstOrNull { it.id == id }, viewModel)
            }
            composable(Routes.SUPPLIERS) { SupplierScreen(suppliers, viewModel) { nav.navigate("supplier/$it") } }
            composable("supplier/{accountId}", arguments = listOf(navArgument("accountId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("accountId").orEmpty()
                SupplierAccountScreen(id, suppliers.firstOrNull { it.id == id }, viewModel)
            }
            composable(Routes.INVENTORY) { InventoryScreen(items, lowStock.map { it.id }.toSet(), viewModel) }
            composable(Routes.SALE) { InvoiceEntryScreen(true, items, customers, suppliers, viewModel) }
            composable(Routes.PURCHASE) { InvoiceEntryScreen(false, items, customers, suppliers, viewModel) }
            composable(Routes.REPORTS) { ReportsScreen(cash, customers, suppliers, items, lowStock) }
            composable(Routes.NOTES) { SimpleInfoScreen("دفتر الملاحظات", "سيتم ربط إدارة الملاحظات بقاعدة البيانات في المرحلة التالية.") }
            composable(Routes.PRINTER) { PrinterScreen() }
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
                ActionCard("شراء جديد", Icons.Default.ReceiptLong, Modifier.weight(1f)) { onNavigate(Routes.PURCHASE) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("العملاء", Icons.Default.People, Modifier.weight(1f)) { onNavigate(Routes.CUSTOMERS) }
                ActionCard("الموردون", Icons.Default.Store, Modifier.weight(1f)) { onNavigate(Routes.SUPPLIERS) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("التقارير", Icons.Default.Assessment, Modifier.weight(1f)) { onNavigate(Routes.REPORTS) }
                ActionCard("الملاحظات", Icons.Default.ReceiptLong, Modifier.weight(1f)) { onNavigate(Routes.NOTES) }
            }
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
                    }
                }
            }
        }
    }
    if (showAdd) AddItemDialog(viewModel) { showAdd = false }
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
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unitPrice by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf("0") }
    var operationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var saving by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var pendingReceipt by remember { mutableStateOf<ReceiptData?>(null) }
    var pendingPhone by remember { mutableStateOf<String?>(null) }
    var receiptMessage by remember { mutableStateOf("") }

    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val accounts = if (isSale) customers.map { it.id to it.name } else suppliers.map { it.id to it.name }
    val selectedAccountName = accounts.firstOrNull { it.first == selectedAccountId }?.second
    val quantityMilli = runCatching {
        BigDecimal(quantity).multiply(BigDecimal("1000")).longValueExact()
    }.getOrNull()
    val priceValue = unitPrice.toLongOrNull()
    val lineTotal = if (quantityMilli != null && priceValue != null && quantityMilli > 0 && priceValue >= 0) {
        runCatching { MoneyMath.lineTotal(quantityMilli, priceValue) }.getOrNull()
    } else null

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (isSale) "فاتورة بيع جديدة" else "فاتورة شراء جديدة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (isSale) "اختر العميل أو اتركه للبيع النقدي" else "اختر المورد أو اتركه للشراء النقدي", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            FilledTonalButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedAccountName ?: if (isSale) "بيع نقدي بدون عميل" else "شراء نقدي بدون مورد")
            }
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                DropdownMenuItem(text = { Text(if (isSale) "بيع نقدي بدون عميل" else "شراء نقدي بدون مورد") }, onClick = { selectedAccountId = null; accountMenu = false })
                accounts.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { selectedAccountId = id; accountMenu = false })
                }
            }
        }
        Text("الأصناف المتاحة", fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedItemId = item.id
                        unitPrice = (if (isSale) item.salePrice else item.purchasePrice).toString()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedItemId == item.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(if (isSale) item.salePrice.toString() else item.purchasePrice.toString())
                    }
                }
            }
        }
        if (selectedItem != null) {
            Text("الصنف المحدد: \${selectedItem.name}", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, modifier = Modifier.weight(1f), label = { Text("الكمية") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(unitPrice, { unitPrice = it }, modifier = Modifier.weight(1f), label = { Text("سعر الوحدة") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("الإجمالي: \${lineTotal ?: "—"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(paid, { paid = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المبلغ المدفوع") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Button(
            onClick = {
                val item = selectedItem
                val qty = quantityMilli
                val price = priceValue
                val total = lineTotal
                val paidValue = paid.toLongOrNull()
                if (item != null && qty != null && price != null && total != null && paidValue != null && !saving) {
                    saving = true
                    val partyPhone = if (isSale) customers.firstOrNull { it.id == selectedAccountId }?.phone
                        else suppliers.firstOrNull { it.id == selectedAccountId }?.phone
                    val partyName = selectedAccountName
                    val request = CreateInvoiceInput(
                        operationId = operationId,
                        customerOrSupplierId = selectedAccountId,
                        lines = listOf(InvoiceLineInput(item.id, qty, price)),
                        paid = paidValue,
                        paymentMethod = "CASH"
                    )
                    val finish: (Boolean) -> Unit = { success ->
                        saving = false
                        if (success) {
                            operationId = UUID.randomUUID().toString()
                            selectedItemId = null
                            quantity = "1"
                            unitPrice = ""
                            paid = "0"
                        }
                    }
                    val saved: (com.saleh.alazzi.grocery.data.repository.SaveInvoiceResult) -> Unit = { result ->
                        val remaining = total - paidValue
                        val cumulative = if (selectedAccountId == null) null else {
                            val previous = if (isSale) customers.firstOrNull { it.id == selectedAccountId }?.balance
                                else suppliers.firstOrNull { it.id == selectedAccountId }?.balance
                            (previous ?: 0L) + remaining
                        }
                        pendingPhone = partyPhone
                        pendingReceipt = ReceiptData(
                            invoiceType = if (isSale) "فاتورة بيع" else "فاتورة شراء",
                            invoiceNumber = result.invoiceNumber,
                            dateTime = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar")).format(java.util.Date()),
                            partyName = partyName,
                            items = listOf(
                                ReceiptItem(
                                    name = item.name,
                                    quantity = BigDecimal.valueOf(qty, 3).stripTrailingZeros().toPlainString(),
                                    unitPrice = price,
                                    total = total
                                )
                            ),
                            subtotal = total,
                            discount = 0,
                            total = total,
                            paid = paidValue,
                            remaining = remaining,
                            accountBalance = cumulative
                        )
                        receiptMessage = ""
                    }
                    if (isSale) viewModel.createSale(request, finish, saved)
                    else viewModel.createPurchase(request, finish, saved)
                }
            },
            enabled = selectedItem != null && lineTotal != null && paid.toLongOrNull() != null && !saving,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "جارٍ الحفظ..." else "حفظ الفاتورة") }
    }

    pendingReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { pendingReceipt = null },
            title = { Text("تم حفظ الفاتورة رقم \${receipt.invoiceNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("يمكنك حفظ صورة أو PDF أو مشاركة نسخة الفاتورة.")
                    if (receiptMessage.isNotBlank()) Text(receiptMessage, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 384)
                            try { exporter.savePng(bitmap, "Invoice_\${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                        }.onSuccess { receiptMessage = "تم حفظ صورة الفاتورة" }
                            .onFailure { receiptMessage = it.message ?: "تعذر حفظ الصورة" }
                    }) { Text("حفظ صورة الفاتورة") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            try { exporter.savePdf(bitmap, "Invoice_\${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                        }.onSuccess { receiptMessage = "تم حفظ ملف PDF" }
                            .onFailure { receiptMessage = it.message ?: "تعذر إنشاء PDF" }
                    }) { Text("حفظ PDF") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            val uri = try { exporter.savePng(bitmap, "Invoice_\${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                            exporter.share(uri, "image/png")
                        }.onFailure { receiptMessage = it.message ?: "تعذرت المشاركة" }
                    }) { Text("مشاركة صورة الفاتورة") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            val uri = try { exporter.savePdf(bitmap, "Invoice_\${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                            exporter.share(uri, "application/pdf")
                        }.onFailure { receiptMessage = it.message ?: "تعذرت مشاركة PDF" }
                    }) { Text("مشاركة PDF") }
                    TextButton(onClick = {
                        runCatching {
                            val bitmap = ReceiptRenderer.render(receipt, 576)
                            val uri = try { exporter.savePng(bitmap, "Invoice_\${receipt.invoiceNumber}") } finally { bitmap.recycle() }
                            exporter.shareToWhatsApp(uri, "image/png")
                        }.onFailure { receiptMessage = it.message ?: "تعذر فتح WhatsApp أو مشاركة الفاتورة" }
                    }) { Text("مشاركة عبر WhatsApp") }
                    if (!pendingPhone.isNullOrBlank()) {
                        TextButton(onClick = {
                            runCatching { exporter.openWhatsAppChat(pendingPhone.orEmpty()) }
                                .onFailure { receiptMessage = it.message ?: "تعذر فتح محادثة WhatsApp" }
                        }) { Text("فتح محادثة العميل في WhatsApp") }
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
        Text("الرصيد المستحق: \${customer.balance}", color = Color(0xFFB42318), style = MaterialTheme.typography.titleLarge)
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
                        Text("الرصيد بعد العملية: \${tx.balanceAfter}")
                    }
                }
            }
        }
    }
    if (showPayment) PaymentDialog(
        title = "قبض من العميل",
        maxAmount = customer.balance,
        onSave = { amount, note -> viewModel.receiveCustomerPayment(id, amount, note); showPayment = false },
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
        Text("المبلغ المستحق للمورد: \${supplier.balance}", color = Color(0xFFB42318), style = MaterialTheme.typography.titleLarge)
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
                        Text("الرصيد بعد العملية: \${tx.balanceAfter}")
                    }
                }
            }
        }
    }
    if (showPayment) PaymentDialog(
        title = "دفع للمورد",
        maxAmount = supplier.balance,
        onSave = { amount, note -> viewModel.paySupplier(id, amount, note); showPayment = false },
        onDismiss = { showPayment = false }
    )
}

@Composable
private fun PaymentDialog(title: String, maxAmount: Long, onSave: (Long, String) -> Unit, onDismiss: () -> Unit) {
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
                if (value != null && value > 0 && value <= maxAmount) onSave(value, note)
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
                        status = if (found.isEmpty()) "لا توجد طابعات مقترنة. اربط الطابعة من إعدادات Bluetooth في الهاتف." else "تم العثور على \${found.size} جهاز مقترن"
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
                        status = if (found.isEmpty()) "لا توجد طابعات مقترنة. اربط الطابعة من إعدادات Bluetooth في الهاتف." else "تم العثور على \${found.size} جهاز مقترن"
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
