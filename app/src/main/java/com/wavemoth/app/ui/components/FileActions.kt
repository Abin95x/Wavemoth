package com.wavemoth.app.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a function that asks the user where to save a file (system "Save as" screen),
 * then writes [content] there. [mime] is e.g. "text/csv" or "application/json".
 */
@Composable
fun rememberFileSaver(mime: String): (suggestedName: String, content: () -> String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> String)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val content = pending
        pending = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(content().toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
    }
    return { name, content ->
        pending = content
        launcher.launch(name)
    }
}

/** Returns a function that opens the system file picker and hands back the chosen file's text. */
@Composable
fun rememberFileOpener(onText: (name: String, text: String?) -> Unit): (mimeTypes: Array<String>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        onText(name?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported playlist", text)
    }
    return { types -> launcher.launch(types) }
}

/** File types the pickers accept for playlist / backup files. */
val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")
val BACKUP_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Turns a playlist name into a safe file name. */
fun safeFileName(name: String, extension: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "playlist" } + ".$extension"
