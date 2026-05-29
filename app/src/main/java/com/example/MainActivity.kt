package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.ScrapViewModel
import com.example.ui.SavedCalculationWithLiveTotal
import com.example.ui.theme.MyApplicationTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContract
import android.content.Intent
import android.app.Activity
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CALCULATOR("Kalkulator", Icons.Default.ShoppingCart),
    PRICE_LIST("Cennik", Icons.Default.List),
    HISTORY("Historia", Icons.Default.Refresh)
}

enum class PriceListSubTab(val title: String) {
    METALS("Metale"),
    COMPLEX_PRODUCTS("Zestawy złożone")
}

class CreateJsonDocumentContract : ActivityResultContract<String, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): android.net.Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: ScrapViewModel by viewModels {
        ScrapViewModel.Factory(application)
    }

    private val exportPriceListLauncher = registerForActivityResult(
        CreateJsonDocumentContract()
    ) { uri ->
        uri?.let { saveJsonToUri(it) }
    }

    private val importPriceListLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { loadJsonFromUri(it) }
    }

    fun triggerExport() {
        try {
            exportPriceListLauncher.launch("cennik_zlomu.json")
        } catch (e: Exception) {
            Toast.makeText(this, "Błąd systemowy: Brak aplikacji do obsługi eksportu (ACTION_CREATE_DOCUMENT).", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerImport() {
        try {
            importPriceListLauncher.launch("application/json")
        } catch (e: Exception) {
            Toast.makeText(this, "Błąd systemowy: Brak aplikacji do obsługi wyboru plików (GetContent).", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveJsonToUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val jsonString = viewModel.exportPriceListJson()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this@MainActivity, "Cennik został pomyślnie wyeksportowany!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Błąd eksportu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadJsonFromUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val result = viewModel.importPriceListJson(jsonString)
                    if (result.success) {
                        Toast.makeText(this@MainActivity, "Pomyślnie zaimportowano: ${result.message}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Błąd importu: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Błąd odczytu pliku: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ScrapCalculationsApp(viewModel)
            }
        }
    }
}

// FORMATTING HELPERS
fun formatPrice(value: Double): String {
    val df = DecimalFormat("#,##0.00")
    df.decimalFormatSymbols = DecimalFormatSymbols(Locale("pl", "PL"))
    return "${df.format(value)} zł"
}

fun formatQuantity(value: Double, unit: String): String {
    val df = DecimalFormat("#,##0.###")
    df.decimalFormatSymbols = DecimalFormatSymbols(Locale("pl", "PL"))
    return "${df.format(value)} $unit"
}

fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("pl", "PL"))
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapCalculationsApp(viewModel: ScrapViewModel) {
    var curTab by remember { mutableStateOf(AppTab.CALCULATOR) }

    // States from viewmodel
    val materials by viewModel.materials.collectAsState()
    val complexProducts by viewModel.complexProductsWithDetails.collectAsState()
    val draftItems by viewModel.draftItems.collectAsState()
    val savedCalculations by viewModel.dynamicSavedCalculations.collectAsState()
    val draftTotal by viewModel.draftTotal.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            var showDatabaseDialog by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Kalkulator Złomu",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDatabaseDialog = true },
                        modifier = Modifier.testTag("database_management_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Zarządzanie bazą danych"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
            if (showDatabaseDialog) {
                DatabaseManagementDialog(
                    viewModel = viewModel,
                    onDismiss = { showDatabaseDialog = false }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = curTab == tab,
                        onClick = { curTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (curTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_button_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (curTab) {
                AppTab.CALCULATOR -> CalculatorScreen(
                    viewModel = viewModel,
                    materials = materials,
                    complexProducts = complexProducts,
                    draftItems = draftItems,
                    draftTotal = draftTotal
                )
                AppTab.PRICE_LIST -> PriceListScreen(
                    viewModel = viewModel,
                    materials = materials,
                    complexProducts = complexProducts
                )
                AppTab.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    savedCalculations = savedCalculations
                )
            }
        }
    }
}

fun android.content.Context.findActivity(): MainActivity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is MainActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun DatabaseManagementDialog(
    viewModel: ScrapViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearPriceListConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Zarządzanie Danymi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider()

                // Section 1: Backup (Export / Import)
                Text(
                    text = "Kopia zapasowa i cennik",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Wyeksportuj obecny cennik (produkty proste i złożone) do pliku JSON lub zaimportuj wcześniejszy plik, aby zsynchronizować zmiany.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            activity?.triggerExport()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_button"),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eksportuj")
                    }

                    OutlinedButton(
                        onClick = {
                            activity?.triggerImport()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_backup_button"),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importuj")
                    }
                }

                HorizontalDivider()

                // Section 2: Destructive actions
                Text(
                    text = "Opcje czyszczenia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )

                // Button for Clear Calculations History
                Button(
                    onClick = { showClearHistoryConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_history_trigger_button"),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wyczyść historię kalkulacji")
                }

                // Button for Clear Price List
                Button(
                    onClick = { showClearPriceListConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_pricelist_trigger_button"),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wyczyść cennik i bazę")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Zamknij")
                }
            }
        }
    }

    // Confirmation dialogues
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Wyczyścić historię?") },
            text = { Text("Czy na pewno chcesz trwale usunąć wszystkie zapisane historyczne kalkulacje? Ta operacja jest nieodwracalna.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryConfirm = false
                        Toast.makeText(context, "Historia kalkulacji została wyczyszczona!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Tak, wyczyść")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    if (showClearPriceListConfirm) {
        AlertDialog(
            onDismissRequest = { showClearPriceListConfirm = false },
            title = { Text("Wyczyścić cennik?") },
            text = { Text("Czy na pewno chcesz usunąć cały cennik (metale oraz produkty złożone)? Spowoduje to również wyczyszczenie bieżącego koszyka kalkulacji.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearPriceList()
                        showClearPriceListConfirm = false
                        Toast.makeText(context, "Cennik został wyczyszczony!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Tak, wyczyść")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPriceListConfirm = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

// --- TAB 1: CALCULATOR SCREEN ---
@Composable
fun CalculatorScreen(
    viewModel: ScrapViewModel,
    materials: List<Material>,
    complexProducts: List<ComplexProductWithDetails>,
    draftItems: List<CalculationItem>,
    draftTotal: Double
) {
    var showSelectMaterialDialog by remember { mutableStateOf(false) }
    var selectedMaterial by remember { mutableStateOf<Material?>(null) }
    var selectedComplexProduct by remember { mutableStateOf<ComplexProductWithDetails?>(null) }
    var isComplexSelected by remember { mutableStateOf(false) }

    var quantityText by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    var selectedUnit by remember { mutableStateOf("szt.") }
    var weightPerPieceText by remember { mutableStateOf("1.0") }

    // Auto-select first element if none selected, or clear if deleted
    LaunchedEffect(materials, complexProducts) {
        if (selectedMaterial != null && !materials.any { it.id == selectedMaterial?.id }) {
            selectedMaterial = null
        }
        if (selectedComplexProduct != null && !complexProducts.any { it.id == selectedComplexProduct?.id }) {
            selectedComplexProduct = null
        }
        if (selectedMaterial == null && !isComplexSelected && materials.isNotEmpty()) {
            selectedMaterial = materials.first()
        }
        if (selectedComplexProduct == null && isComplexSelected && complexProducts.isNotEmpty()) {
            selectedComplexProduct = complexProducts.first()
        }
    }

    LaunchedEffect(selectedMaterial, selectedComplexProduct, isComplexSelected) {
        if (isComplexSelected) {
            selectedUnit = "szt."
        } else {
            selectedUnit = selectedMaterial?.unit ?: "kg"
        }
    }

    LaunchedEffect(selectedComplexProduct, selectedMaterial, isComplexSelected, selectedUnit) {
        if (selectedUnit == "szt.") {
            if (isComplexSelected) {
                selectedComplexProduct?.let { prod ->
                    val defWeight = prod.components.sumOf { comp ->
                        val qty = comp.component.quantity
                        if (comp.material.unit.lowercase() == "g") qty / 1000.0 else qty
                    }
                    val df = DecimalFormat("#.###", DecimalFormatSymbols(Locale.US))
                    weightPerPieceText = if (defWeight > 0.0) df.format(defWeight) else "1.0"
                }
            } else {
                weightPerPieceText = "1.0"
            }
        }
    }

    val pBase = if (isComplexSelected) {
        selectedComplexProduct?.totalPrice ?: 0.0
    } else {
        selectedMaterial?.pricePerUnit ?: 0.0
    }
    val wp = weightPerPieceText.replace(",", ".").toDoubleOrNull() ?: 1.0

    val pSzt = if (isComplexSelected) {
        pBase
    } else {
        val baseUnit = selectedMaterial?.unit?.lowercase() ?: "kg"
        if (baseUnit == "g") {
            pBase * (wp * 1000.0)
        } else {
            pBase * wp
        }
    }

    val displayPrice = when (selectedUnit) {
        "szt." -> pSzt
        "kg" -> pSzt / (if (wp > 0.0) wp else 1.0)
        "g" -> (pSzt / (if (wp > 0.0) wp else 1.0)) / 1000.0
        else -> pSzt
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // SUMMARY BANNER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AKTUALNE WYLICZENIE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatPrice(draftTotal),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("draft_total_text")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Liczba pozycji: ${draftItems.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (draftItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.clearDraft() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clear_draft_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wyczyść")
                            }

                            Button(
                                onClick = { showSaveDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("save_draft_button")
                            ) {
                                Icon(Icons.Default.Done, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zapisz w gotowe")
                            }
                        }
                    }
                }
            }
        }

        // ADD POSITION CONTROLLER
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Dodaj pozycję do kalkulacji",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // CHOSEN PICKER TRIGGER
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSelectMaterialDialog = true }
                            .testTag("material_picker_trigger"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isComplexSelected) "PRODUKT ZŁOŻONY / URZĄDZENIE" else "MATERIAŁ PROSTY / METAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isComplexSelected) {
                                        selectedComplexProduct?.name ?: "Wybierz wyrób złożony..."
                                    } else {
                                        selectedMaterial?.name ?: "Wybierz metal..."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isComplexSelected) {
                                    selectedComplexProduct?.let {
                                        Text(
                                            text = "Cena bazowa: ${formatPrice(it.totalPrice)} / szt. | Wybrana: ${formatPrice(displayPrice)} / $selectedUnit",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (it.components.isNotEmpty()) {
                                            Text(
                                                text = "Skład: " + it.components.joinToString { c -> "${formatQuantity(c.component.quantity, c.material.unit)} ${c.material.name}" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    selectedMaterial?.let {
                                        Text(
                                            text = "Cena bazowa: ${formatPrice(it.pricePerUnit)} / ${it.unit} | Wybrana: ${formatPrice(displayPrice)} / $selectedUnit",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Zmień pozycję"
                            )
                        }
                    }

                    // UNIT TOGGLE ROW (szt., kg, g)
                    Text(
                        text = "Wybierz jednostkę rozliczeniową:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("szt.", "kg", "g").forEach { unit ->
                            val selected = selectedUnit == unit
                            if (selected) {
                                Button(
                                    onClick = { selectedUnit = unit },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(unit, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { selectedUnit = unit },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(unit)
                                }
                            }
                        }
                    }

                    // PIECE WEIGHT OVERRIDE INPUT
                    val showWeightInput = isComplexSelected || (selectedUnit == "szt.")
                    if (showWeightInput) {
                        OutlinedTextField(
                            value = weightPerPieceText,
                            onValueChange = { input ->
                                val normalized = input.replace(",", ".")
                                if (normalized.isEmpty() || normalized.toDoubleOrNull() != null || normalized.endsWith(".")) {
                                    weightPerPieceText = normalized
                                }
                            },
                            label = { Text("Masa 1 sztuki (kg)") },
                            placeholder = { Text("1.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            trailingIcon = {
                                Text(
                                    text = "kg",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("piece_weight_input_field")
                        )
                    }

                    // QUANTITY INPUT ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { input ->
                                val normalized = input.replace(",", ".")
                                if (normalized.isEmpty() || normalized.toDoubleOrNull() != null || normalized.endsWith(".")) {
                                    quantityText = normalized
                                }
                            },
                            label = { Text("Ilość / masa") },
                            placeholder = { Text("0.0") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true,
                            trailingIcon = {
                                Text(
                                    text = selectedUnit,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("quantity_input_field")
                        )

                        Button(
                            onClick = {
                                val qty = quantityText.toDoubleOrNull()
                                if (qty != null && qty > 0.0) {
                                    if (isComplexSelected) {
                                        val prod = selectedComplexProduct
                                        if (prod != null) {
                                            viewModel.addCustomDraftItem(
                                                materialId = -prod.id,
                                                name = prod.name,
                                                price = displayPrice,
                                                unit = selectedUnit,
                                                quantity = qty
                                            )
                                            quantityText = ""
                                        }
                                    } else {
                                        val mat = selectedMaterial
                                        if (mat != null) {
                                            viewModel.addCustomDraftItem(
                                                materialId = mat.id,
                                                name = mat.name,
                                                price = displayPrice,
                                                unit = selectedUnit,
                                                quantity = qty
                                             )
                                             quantityText = ""
                                         }
                                     }
                                 }
                             },
                             enabled = (quantityText.toDoubleOrNull() ?: 0.0) > 0.0 && (if (isComplexSelected) selectedComplexProduct != null else selectedMaterial != null) && (wp > 0.0),
                             shape = RoundedCornerShape(12.dp),
                             modifier = Modifier
                                 .weight(1f)
                                 .height(56.dp)
                                 .testTag("add_item_to_draft_button")
                         ) {
                             Icon(Icons.Default.Add, contentDescription = "Dodaj")
                             Spacer(modifier = Modifier.width(4.dp))
                             Text("Dodaj")
                         }
                     }
                }
            }
        }

        // CURRENT BILL ITEMS HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pozycje w rozliczeniu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (draftItems.isNotEmpty()) {
                    Text(
                        text = "${draftItems.size} poz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // LIST OF CURRENT ITEMS
        if (draftItems.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Twoja lista jest pusta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wybierz materiał lub wyrób złożony z bazy u góry, wpisz ilość i kliknij Dodaj, aby zsumować.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            items(draftItems, key = { it.id }) { item ->
                DraftItemRow(
                    item = item,
                    complexProducts = complexProducts,
                    onDelete = { viewModel.deleteDraftItem(item.id) }
                )
            }
        }
    }

    // SELECT MATERIAL OR COMPLEX PRODUCT DIALOG
    if (showSelectMaterialDialog) {
        MaterialOrComplexSelectDialog(
            materials = materials,
            complexProducts = complexProducts,
            onDismiss = { showSelectMaterialDialog = false },
            onSelectMaterial = { mat ->
                selectedMaterial = mat
                isComplexSelected = false
                showSelectMaterialDialog = false
            },
            onSelectComplex = { prod ->
                selectedComplexProduct = prod
                isComplexSelected = true
                showSelectMaterialDialog = false
            }
        )
    }

    // SAVE CALCULATION DIALOG
    if (showSaveDialog) {
        var titleText by remember { mutableStateOf("") }
        val automaticPlaceholder = "Wyliczenie ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}"

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Zapisz wyliczenie w historii") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Wprowadź tytuł dla tego rozliczenia, aby łatwo odnaleźć je później.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Nazwa wyliczenia") },
                        placeholder = { Text(automaticPlaceholder) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_calculation_title_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalTitle = titleText.ifBlank { automaticPlaceholder }
                        viewModel.saveCurrentCalculation(finalTitle)
                        viewModel.clearDraft()
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_calculation_button")
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
fun DraftItemRow(
    item: CalculationItem,
    complexProducts: List<ComplexProductWithDetails>,
    onDelete: () -> Unit
) {
    val isComplex = item.materialId < 0
    val complexProduct = if (isComplex) complexProducts.find { it.id == -item.materialId } else null
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isComplex) { isExpanded = !isExpanded }
            .testTag("draft_item_row_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isComplex) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Rozwiń skład",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp).size(20.dp)
                            )
                        }
                        Text(
                            text = item.materialName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Cena: ${formatPrice(item.materialPrice)} / ${item.materialUnit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Masa: ${formatQuantity(item.quantity, item.materialUnit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatPrice(item.totalCost),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_draft_item_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń pozycję",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (isComplex && isExpanded && complexProduct != null) {
                val weightPerPieceKg = complexProduct.components.sumOf { comp ->
                    val compQty = comp.component.quantity
                    if (comp.material.unit.lowercase() == "g") compQty / 1000.0 else compQty
                }.takeIf { it > 0.0 } ?: 1.0

                val equivalentPieces = when (item.materialUnit.lowercase()) {
                    "kg" -> item.quantity / weightPerPieceKg
                    "g", "gramy" -> (item.quantity / 1000.0) / weightPerPieceKg
                    else -> item.quantity
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Skład zestawu (${formatQuantity(item.quantity, item.materialUnit)} ≈ ${formatQuantity(equivalentPieces, "szt.")}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    complexProduct.components.forEach { c ->
                        val singleQuantity = c.component.quantity
                        val totalQuantity = singleQuantity * equivalentPieces
                        val currentUnitRec = c.material.pricePerUnit
                        val totalComponentCost = totalQuantity * currentUnitRec

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = c.material.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${formatQuantity(singleQuantity, c.material.unit)} x ${formatQuantity(equivalentPieces, "szt.")} = ${formatQuantity(totalQuantity, c.material.unit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatPrice(totalComponentCost),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class SearchItem {
    abstract val name: String

    data class MetalItem(val material: Material) : SearchItem() {
        override val name: String get() = material.name
    }

    data class ComplexItem(val complex: ComplexProductWithDetails) : SearchItem() {
        override val name: String get() = complex.name
    }
}

@Composable
fun MaterialOrComplexSelectDialog(
    materials: List<Material>,
    complexProducts: List<ComplexProductWithDetails>,
    onDismiss: () -> Unit,
    onSelectMaterial: (Material) -> Unit,
    onSelectComplex: (ComplexProductWithDetails) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredMaterials = remember(materials, searchQuery) {
        if (searchQuery.isBlank()) materials
        else materials.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredComplex = remember(complexProducts, searchQuery) {
        if (searchQuery.isBlank()) complexProducts
        else complexProducts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val combinedItems = remember(filteredMaterials, filteredComplex) {
        val list1 = filteredMaterials.map { SearchItem.MetalItem(it) }
        val list2 = filteredComplex.map { SearchItem.ComplexItem(it) }
        (list1 + list2).sortedBy { it.name }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = "Dodaj pozycję do kalkulacji",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Wyszukaj metal lub produkt złożony...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("material_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (combinedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak pasujących pozycji w bazie",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(combinedItems) { item ->
                            when (item) {
                                is SearchItem.MetalItem -> {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectMaterial(item.material) }
                                            .testTag("select_material_option_${item.material.id}"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Text(
                                                        text = "METAL",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = item.material.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Text(
                                                text = "${formatPrice(item.material.pricePerUnit)} / ${item.material.unit}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                                is SearchItem.ComplexItem -> {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectComplex(item.complex) }
                                            .testTag("select_complex_option_${item.complex.id}"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "WYRÓB",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = item.complex.name,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                Text(
                                                    text = "${formatPrice(item.complex.totalPrice)} / szt.",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (item.complex.description.isNotBlank()) {
                                                Text(
                                                    text = item.complex.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj")
                    }
                }
            }
        }
    }
}

// --- TAB 2: PRICE LIST SCREEN (DATABASE EDITOR) ---
@Composable
fun PriceListScreen(
    viewModel: ScrapViewModel,
    materials: List<Material>,
    complexProducts: List<ComplexProductWithDetails>
) {
    var searchQuery by remember { mutableStateOf("") }

    var showMaterialFormDialog by remember { mutableStateOf(false) }
    var materialToEdit by remember { mutableStateOf<Material?>(null) }

    var showComplexFormDialog by remember { mutableStateOf(false) }
    var complexProductToEdit by remember { mutableStateOf<ComplexProductWithDetails?>(null) }

    var showAddTypeChooser by remember { mutableStateOf(false) }

    val filteredMaterials = remember(materials, searchQuery) {
        if (searchQuery.isBlank()) {
            materials
        } else {
            materials.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredComplex = remember(complexProducts, searchQuery) {
        if (searchQuery.isBlank()) {
            complexProducts
        } else {
            complexProducts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val categories = remember(filteredMaterials) {
        filteredMaterials.groupBy { it.category }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cennik i Baza (zmieszana)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Szukaj metali i zestawów złożonych...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("price_list_search_input")
            )

            if (filteredMaterials.isEmpty() && filteredComplex.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Brak pasujących pozycji w bazie",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Simple metals categorised headings
                    categories.forEach { (cat, list) ->
                        item(key = "hdr_cat_$cat") {
                            Text(
                                text = cat.uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { "mat_${it.id}" }) { mat ->
                            MaterialPriceCard(
                                material = mat,
                                onEdit = {
                                    materialToEdit = mat
                                    showMaterialFormDialog = true
                                },
                                onDelete = { viewModel.deleteMaterial(mat.id) }
                            )
                        }
                    }

                    // Complex products heading
                    if (filteredComplex.isNotEmpty()) {
                        item(key = "hdr_complex_sets") {
                            Text(
                                text = "ZESTAWY I WYROBY ZŁOŻONE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(filteredComplex, key = { "complex_${it.id}" }) { prod ->
                            ComplexProductCard(
                                productDetails = prod,
                                onEdit = {
                                    complexProductToEdit = prod
                                    showComplexFormDialog = true
                                },
                                onDelete = { viewModel.deleteComplexProduct(prod.id) }
                            )
                        }
                    }
                }
            }
        }

        LargeFloatingActionButton(
            onClick = { showAddTypeChooser = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 8.dp)
                .testTag("add_new_material_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Dodaj do bazy"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dodaj pozycję",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAddTypeChooser) {
        AlertDialog(
            onDismissRequest = { showAddTypeChooser = false },
            title = { Text("Dodaj nową pozycję do bazy") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Button(
                        onClick = {
                            materialToEdit = null
                            showMaterialFormDialog = true
                            showAddTypeChooser = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Czysty metal (surowiec)")
                    }
                    Button(
                        onClick = {
                            complexProductToEdit = null
                            showComplexFormDialog = true
                            showAddTypeChooser = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zestaw złożony (wyrób)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddTypeChooser = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    // ADD / EDIT MATERIAL DIALOG
    if (showMaterialFormDialog) {
        MaterialFormDialog(
            existingMaterial = materialToEdit,
            onDismiss = { showMaterialFormDialog = false },
            onSave = { name, price, unit, category ->
                viewModel.saveMaterial(
                    name = name,
                    price = price,
                    unit = unit,
                    category = category,
                    id = materialToEdit?.id ?: 0
                )
                showMaterialFormDialog = false
            }
        )
    }

    // ADD / EDIT COMPLEX PRODUCT DIALOG
    if (showComplexFormDialog) {
        ComplexProductFormDialog(
            existingProduct = complexProductToEdit,
            allMaterials = materials,
            onDismiss = { showComplexFormDialog = false },
            onSave = { name, desc, components ->
                viewModel.saveComplexProduct(
                    name = name,
                    description = desc,
                    components = components,
                    id = complexProductToEdit?.product?.id ?: 0
                )
                showComplexFormDialog = false
            }
        )
    }
}

@Composable
fun ComplexProductCard(
    productDetails: ComplexProductWithDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("complex_product_card_${productDetails.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productDetails.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (productDetails.description.isNotBlank()) {
                        Text(
                            text = productDetails.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatPrice(productDetails.totalPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_complex_btn_${productDetails.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edytuj produkt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!productDetails.product.isDefault) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_complex_btn_${productDetails.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Usuń produkt",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Wyrób systemowy (zablokowany)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(20.dp)
                        )
                    }
                }
            }

            if (productDetails.components.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Konstytutywny odzysk surowców:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    productDetails.components.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${c.material.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatQuantity(c.component.quantity, c.material.unit)} (wartość: ${formatPrice(c.totalCost)})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComplexProductFormDialog(
    existingProduct: ComplexProductWithDetails?,
    allMaterials: List<Material>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<ComplexProductComponent>) -> Unit
) {
    var name by remember { mutableStateOf(existingProduct?.product?.name ?: "") }
    var description by remember { mutableStateOf(existingProduct?.product?.description ?: "") }

    var tempComponents by remember {
        mutableStateOf(
            existingProduct?.components?.map { it.component } ?: emptyList<ComplexProductComponent>()
        )
    }

    var selectedMatIdForNewComp by remember { mutableStateOf<Int?>(allMaterials.firstOrNull()?.id) }
    var newCompQtyStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingProduct == null) "Dodaj nowy produkt złożony" else "Edytuj produkt złożony",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nazwa wyrobu") },
                        placeholder = { Text("np. Dysk twardy, Procesor Intel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Krótki opis wyrobu") },
                        placeholder = { Text("np. Odzysk z jednego dysku 3.5 cala") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = "Składniki i zawartość",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (tempComponents.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Brak składników. Skonfiguruj poniżej.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(tempComponents) { comp ->
                        val mat = allMaterials.find { it.id == comp.materialId }
                        if (mat != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mat.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Masa: ${formatQuantity(comp.quantity, mat.unit)} × ${formatPrice(mat.pricePerUnit)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Wartość: ${formatPrice(comp.quantity * mat.pricePerUnit)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            tempComponents = tempComponents.filter { it.materialId != comp.materialId }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Usuń składnik",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val liveTotal = tempComponents.sumOf { comp ->
                    val mat = allMaterials.find { it.id == comp.materialId }
                    (comp.quantity * (mat?.pricePerUnit ?: 0.0))
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RAZEM WARTOŚĆ:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatPrice(liveTotal),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Dodaj składnik metalowy",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            var expanded by remember { mutableStateOf(false) }
                            val currentSelectedMat = allMaterials.find { it.id == selectedMatIdForNewComp } ?: allMaterials.firstOrNull()

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentSelectedMat?.name ?: "Zaznacz metal...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 240.dp)
                                ) {
                                    allMaterials.filter { m -> tempComponents.none { it.materialId == m.id } }
                                        .forEach { mat ->
                                            DropdownMenuItem(
                                                text = { Text("${mat.name} (${mat.unit})") },
                                                onClick = {
                                                    selectedMatIdForNewComp = mat.id
                                                    expanded = false
                                                }
                                            )
                                        }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newCompQtyStr,
                                    onValueChange = { input ->
                                        val normalized = input.replace(",", ".")
                                        if (normalized.isEmpty() || normalized.toDoubleOrNull() != null || normalized.endsWith(".")) {
                                            newCompQtyStr = normalized
                                        }
                                    },
                                    label = { Text("Masa zawartości") },
                                    placeholder = { Text("0.0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f),
                                    trailingIcon = {
                                        Text(
                                            text = currentSelectedMat?.unit ?: "g",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                )

                                Button(
                                    onClick = {
                                        val qty = newCompQtyStr.toDoubleOrNull()
                                        val matId = selectedMatIdForNewComp ?: currentSelectedMat?.id
                                        if (qty != null && qty > 0.0 && matId != null) {
                                            tempComponents = tempComponents + ComplexProductComponent(
                                                productId = existingProduct?.product?.id ?: 0,
                                                materialId = matId,
                                                quantity = qty
                                            )
                                            newCompQtyStr = ""
                                            // Reset selected to prevent accidental duplicates unless clicked explicitly
                                            val remaining = allMaterials.filter { m -> tempComponents.none { it.materialId == m.id } }
                                            selectedMatIdForNewComp = remaining.firstOrNull()?.id
                                        }
                                    },
                                    enabled = (newCompQtyStr.toDoubleOrNull() ?: 0.0) > 0.0 && currentSelectedMat != null,
                                    modifier = Modifier.weight(1f).height(56.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Dodaj")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dodaj")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && tempComponents.isNotEmpty()) {
                        onSave(name.trim(), description.trim(), tempComponents)
                    }
                },
                enabled = name.isNotBlank() && tempComponents.isNotEmpty()
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
fun MaterialPriceCard(
    material: Material,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("material_card_${material.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Jednostka: ${material.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatPrice(material.pricePerUnit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_material_btn_${material.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edytuj cenę",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!material.isDefault) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_material_btn_${material.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń z bazy",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Surowiec systemowy (zablokowany)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialFormDialog(
    existingMaterial: Material?,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf(existingMaterial?.name ?: "") }
    var priceStr by remember { mutableStateOf(existingMaterial?.pricePerUnit?.toString() ?: "") }
    var unit by remember { mutableStateOf(existingMaterial?.unit ?: "kg") }
    var category by remember { mutableStateOf(existingMaterial?.category ?: "Metale Kolorowe") }

    val categoriesList = listOf("Metale Kolorowe", "Metale Szlachetne", "Stal i Żeliwo", "Inne")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingMaterial == null) "Dodaj nowy metal do bazy" else "Edytuj pozycję w bazie",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nazwa materiału") },
                        placeholder = { Text("np. Miedź Świecąca, Cynk") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_material_name")
                    )
                }

                item {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it.replace(",", ".") },
                        label = { Text("Cena jednostkowa (zł)") },
                        placeholder = { Text("np. 32.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_material_price")
                    )
                }

                item {
                    Text(
                        text = "Jednostka miary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("kg", "g").forEach { u ->
                            val isSel = unit == u
                            FilterChip(
                                selected = isSel,
                                onClick = { unit = u },
                                label = { Text(if (u == "kg") "Kilogram (kg)" else "Gram (g)") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("form_unit_chip_$u"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Grupa / Kategoria",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoriesList.forEach { cat ->
                            val isSel = category == cat
                            ElevatedFilterChip(
                                selected = isSel,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                modifier = Modifier.testTag("form_category_chip_${cat.hashCode()}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && price > 0.0) {
                        onSave(name.trim(), price, unit, category)
                    }
                },
                enabled = name.isNotBlank() && (priceStr.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier.testTag("form_save_button")
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}


// --- TAB 3: HISTORY SCREEN (SAVED RUNS) ---
@Composable
fun HistoryScreen(
    viewModel: ScrapViewModel,
    savedCalculations: List<SavedCalculationWithLiveTotal>
) {
    val selectedId by viewModel.selectedCalculationId.collectAsState()
    val savedItems by viewModel.liveSelectedCalculationItems.collectAsState()

    var showDetailsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Zapisane Kalkulacje",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (savedCalculations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Brak zapisanej historii",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Stwórz kalkulację w pierwszej zakładce i zapisz ją.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(savedCalculations, key = { it.calculation.id }) { calcWithTotal ->
                    HistoryCard(
                        calculationWithTotal = calcWithTotal,
                        onViewDetails = {
                            viewModel.selectCalculation(calcWithTotal.calculation.id)
                            showDetailsDialog = true
                        },
                        onDelete = { viewModel.deleteSavedCalculation(calcWithTotal.calculation.id) }
                    )
                }
            }
        }
    }

    // DETAIL MODAL DIALOG
    if (showDetailsDialog && selectedId != null) {
        val matchingHeader = savedCalculations.find { it.calculation.id == selectedId }
        AlertDialog(
            onDismissRequest = {
                showDetailsDialog = false
                viewModel.selectCalculation(null)
            },
            title = {
                Column {
                    Text(
                        text = matchingHeader?.calculation?.title ?: "Szczegóły kalkulacji",
                        fontWeight = FontWeight.Bold
                    )
                    matchingHeader?.calculation?.let {
                        Text(
                            text = formatDateTime(it.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    if (savedItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(savedItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.materialName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${formatQuantity(item.quantity, item.materialUnit)} × ${formatPrice(item.materialPrice)} / ${item.materialUnit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatPrice(item.totalCost),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RAZEM:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = formatPrice(matchingHeader?.liveTotal ?: 0.0),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDetailsDialog = false
                        viewModel.selectCalculation(null)
                    },
                    modifier = Modifier.testTag("close_details_dialog")
                ) {
                    Text("Zamknij")
                }
            }
        )
    }
}

@Composable
fun HistoryCard(
    calculationWithTotal: SavedCalculationWithLiveTotal,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val calculation = calculationWithTotal.calculation
    val liveTotal = calculationWithTotal.liveTotal
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() }
            .testTag("history_card_${calculation.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = calculation.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatDateTime(calculation.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Kliknij po szczegóły",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatPrice(liveTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_history_btn_${calculation.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Usuń z historii",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
