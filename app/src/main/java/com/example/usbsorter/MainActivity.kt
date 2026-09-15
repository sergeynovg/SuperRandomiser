package com.example.usbsorter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppScreen() }
            }
        }
    }
}

@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var log by remember { mutableStateOf("Шаг 1: выбери USB-накопитель.") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            treeUri = uri
            log = "Накопитель выбран:\n$uri"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("USB Sorter", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = { picker.launch(null) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("1. Выбрать USB-накопитель") }

        Button(
            onClick = {
                val uri = treeUri ?: return@Button
                busy = true
                scope.launch {
                    log = withContext(Dispatchers.IO) { UsbOps.process(ctx, uri) }
                    busy = false
                }
            },
            enabled = treeUri != null && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("2. Обработать (mp3/aac → корень, остальное удалить)") }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Работаю…")
            }
        }

        Text(log, style = MaterialTheme.typography.bodySmall)
    }
}
