package com.suyena.bulksms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BulkSmsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkSmsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rawNumbersInput by remember { mutableStateOf("") }
    var numbersList by remember { mutableStateOf<List<String>>(emptyList()) }
    var batchSizeInput by remember { mutableStateOf("120") }
    var messageText by remember { mutableStateOf("") }
    var maxPartsLimitInput by remember { mutableStateOf("10") }
    var isSendingAll by remember { mutableStateOf(false) }
    var activeSendingBatchIndex by remember { mutableStateOf(-1) }
    var statusMessage by remember { mutableStateOf("") }

    val batchStatuses = remember { mutableStateMapOf<Int, String>() }

    val batchSize = batchSizeInput.toIntOrNull()?.takeIf { it > 0 } ?: 120
    val maxPartsLimit = maxPartsLimitInput.toIntOrNull()?.takeIf { it in 1..20 } ?: 10

    // Compute bulk groups breakdown (e.g. 300 -> 120, 120, 60)
    val batches = remember(numbersList, batchSize) {
        if (numbersList.isEmpty() || batchSize <= 0) {
            emptyList()
        } else {
            numbersList.chunked(batchSize)
        }
    }

    // Keep batch statuses in sync with current batches
    LaunchedEffect(batches) {
        batchStatuses.clear()
        batches.forEachIndexed { index, _ ->
            batchStatuses[index] = "Pending"
        }
    }

    // Measure SMS message parts count
    val smsParts = remember(context, messageText) {
        if (messageText.isEmpty()) 0 else SmsHelper.divideMessage(context, messageText).size
    }
    val exceedsPartsLimit = smsParts > maxPartsLimit

    // Function to parse input text (comma, newline, space separated)
    fun parseNumbers(text: String) {
        rawNumbersInput = text
        val parsed = text.split(Regex("[,\\r\\n]+"))
            .map { it.trim().filter { char -> char.isDigit() || char == '+' } }
            .filter { it.isNotEmpty() }
        numbersList = parsed
        statusMessage = if (parsed.isNotEmpty()) "Parsed ${parsed.size} numbers." else ""
    }

    // File Picker for importing text files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val content = reader.readText()
                        parseNumbers(content)
                        Toast.makeText(context, "Imported ${numbersList.size} numbers", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // File Saver for exporting comma-separated text files
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        val commaSeparated = numbersList.joinToString(", ")
                        writer.write(commaSeparated)
                        Toast.makeText(context, "Exported ${numbersList.size} numbers successfully!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error exporting file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Permissions to request
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasSmsPermission) {
            Toast.makeText(context, "SMS permission required. If blocked, tap 'Open App Settings'.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    suspend fun sendSingleBatch(batchIndex: Int, groupNumbers: List<String>) {
        activeSendingBatchIndex = batchIndex
        batchStatuses[batchIndex] = "Sending (0/${groupNumbers.size})"

        var successCount = 0
        var failCount = 0

        for ((idx, number) in groupNumbers.withIndex()) {
            val result = withContext(Dispatchers.IO) {
                SmsHelper.sendSms(context, number, messageText, maxPartsLimit)
            }
            if (result.isSuccess) successCount++ else failCount++
            batchStatuses[batchIndex] = "Sending (${idx + 1}/${groupNumbers.size})"
            delay(300) // Delay to avoid carrier throttling
        }

        batchStatuses[batchIndex] = if (failCount == 0) "Completed ($successCount sent)" else "Finished ($successCount ok, $failCount failed)"
        activeSendingBatchIndex = -1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.suyena_logo),
                            contentDescription = "Suyena SMS Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Suyena SMS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Permission Blocked / Required Banner
            if (!hasSmsPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚠️ SMS Permission Blocked or Required",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Android requires SMS permission to send messages. If the prompt was blocked or denied, you can allow it directly in App Settings.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "💡 Android 13/14 Tip: If settings are grayed out, open App Info > tap the 3 dots (⋮) top-right > choose 'Allow restricted settings'.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Open App Settings")
                            }
                            OutlinedButton(
                                onClick = {
                                    permissionsLauncher.launch(permissionsToRequest.toTypedArray())
                                }
                            ) {
                                Text("Retry Prompt")
                            }
                        }
                    }
                }
            }
            // 1. Phone Numbers Input / Import / Export
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("1. Phone Numbers (Import file or Paste)", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    OutlinedTextField(
                        value = rawNumbersInput,
                        onValueChange = { parseNumbers(it) },
                        label = { Text("Comma-separated numbers or paste numbers") },
                        placeholder = { Text("e.g. +1234567890, +0987654321, 11223344") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("text/plain", "*/*")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Import .txt File")
                        }

                        OutlinedButton(
                            enabled = numbersList.isNotEmpty(),
                            onClick = { exportFileLauncher.launch("comma_separated_numbers.txt") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Export .txt")
                        }

                        TextButton(
                            enabled = rawNumbersInput.isNotEmpty(),
                            onClick = { parseNumbers("") }
                        ) {
                            Text("Clear")
                        }
                    }

                    Text(
                        text = "Total Loaded Numbers: ${numbersList.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 2. Group Size & SMS Limit Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = batchSizeInput,
                    onValueChange = { batchSizeInput = it },
                    label = { Text("Group Size (e.g. 120)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = maxPartsLimitInput,
                    onValueChange = { maxPartsLimitInput = it },
                    label = { Text("Max SMS Parts (4-10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // 3. Message Content
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("SMS Message Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
                isError = exceedsPartsLimit
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SMS Parts: $smsParts / Max $maxPartsLimit limit",
                    color = if (exceedsPartsLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Text(
                    text = "Sent as concatenated SMS (No MMS)",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp
                )
            }

            if (exceedsPartsLimit) {
                Text(
                    text = "Message exceeds $maxPartsLimit SMS parts limit! Shorten text to proceed.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }

            // 4. Groups Breakdown & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bulk Groups Breakdown (${batches.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Button(
                    enabled = !isSendingAll && activeSendingBatchIndex == -1 && numbersList.isNotEmpty() && messageText.isNotEmpty() && !exceedsPartsLimit,
                    onClick = {
                        if (!hasSmsPermission) {
                            Toast.makeText(context, "SMS permission is required. Please allow it in App Settings.", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            return@Button
                        }

                        isSendingAll = true
                        scope.launch {
                            for ((index, group) in batches.withIndex()) {
                                sendSingleBatch(index, group)
                            }
                            isSendingAll = false
                            statusMessage = "Completed sending all ${batches.size} groups!"
                        }
                    }
                ) {
                    Text(if (isSendingAll) "Sending All..." else "Send All Groups")
                }
            }

            // Batches List (e.g. 120, 120, 60)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(batches) { index, group ->
                    val status = batchStatuses[index] ?: "Pending"
                    val isCurrentSending = activeSendingBatchIndex == index

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentSending)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Group ${index + 1} (${group.size} numbers)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = status,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        status.startsWith("Completed") -> MaterialTheme.colorScheme.primary
                                        status.startsWith("Sending") -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = 12.sp
                                )
                            }

                            Text(
                                text = "First: ${group.firstOrNull() ?: ""} | Last: ${group.lastOrNull() ?: ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    enabled = !isSendingAll && activeSendingBatchIndex == -1 && messageText.isNotEmpty() && !exceedsPartsLimit,
                                    onClick = {
                                        if (!hasSmsPermission) {
                                            Toast.makeText(context, "SMS permission is required. Please allow it in App Settings.", Toast.LENGTH_LONG).show()
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                            return@OutlinedButton
                                        }

                                        scope.launch {
                                            sendSingleBatch(index, group)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Send Group ${index + 1} Only", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
