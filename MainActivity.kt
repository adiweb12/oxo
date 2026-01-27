package com.oxo.builder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    
    private val client = OkHttpClient()
    private val RENDER_URL = "https://your-oxo-service.onrender.com/build"
    private val projectLogs = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectRoot = File(filesDir, "OxoProject")

        setContent {
            OxoTheme {
                TerminalScreen(
                    logs = projectLogs,
                    onCommand = { cmd -> handleCommand(cmd, projectRoot) }
                )
            }
        }
    }

    private fun handleCommand(cmd: String, root: File) {
        log("oxo@user:~$ $cmd")
        when (cmd.lowercase()) {
            "create android folder" -> generateOxoProject(root)
            "build" -> startCloudBuild(root)
            "clear" -> projectLogs.clear()
            else -> log("Command not found: $cmd")
        }
    }

    private fun generateOxoProject(root: File) {
        root.deleteRecursively()
        val javaDir = File(root, "app/src/main/java/com/oxo/generated")
        javaDir.mkdirs()
        
        // Essential Android Files
        File(root, "gradlew").writeText("#!/bin/bash\n./gradlew \"$@\"")
        File(root, "build.gradle").writeText("// Oxo Root Build File")
        File(root, "app/build.gradle").writeText("""
            plugins { id 'com.android.application' }
            android { 
                namespace 'com.oxo.generated'
                compileSdk 34
                defaultConfig { applicationId "com.oxo.generated"; minSdk 24 }
            }
        """.trimIndent())

        log("System: Oxo structure initialized at /app/oxo/project")
    }

    private fun startCloudBuild(root: File) {
        val zipFile = File(cacheDir, "oxo_bundle.zip")
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { log("System: Compressing project...") }
            zipProject(root, zipFile)
            
            withContext(Dispatchers.Main) { log("System: Sending to Render Cloud...") }
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "project.zip", zipFile.asRequestBody("application/zip".toMediaTypeOrNull()))
                .build()

            val request = Request.Builder().url(RENDER_URL).post(requestBody).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val apkFile = File(getExternalFilesDir(null), "OxoBuild.apk")
                        response.body?.byteStream()?.copyTo(apkFile.outputStream())
                        withContext(Dispatchers.Main) { log("SUCCESS: APK generated at ${apkFile.name}") }
                    } else {
                        withContext(Dispatchers.Main) { log("BUILD ERROR: Check cloud logs.") }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { log("NETWORK ERROR: ${e.localizedMessage}") }
            }
        }
    }

    private fun zipProject(source: File, destination: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { out ->
            source.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(source.toURI().relativize(file.toURI()).path)
                    out.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(out) }
                }
            }
        }
    }

    private fun log(msg: String) { projectLogs.add(msg) }
}

@Composable
fun TerminalScreen(logs: List<String>, onCommand: (String) -> Unit) {
    var textState by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(16.dp)) {
        Text("OXO BUILDER v1.0", color = Color(0xFF00FF41), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(logs) { log ->
                Text(log, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }

        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF00FF41), fontFamily = FontFamily.Monospace),
            label = { Text("Enter Command", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF41),
                unfocusedBorderColor = Color.DarkGray
            )
        )
        
        Button(
            onClick = { onCommand(textState); textState = "" },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Text("RUN COMMAND", color = Color(0xFF00FF41))
        }
    }
}

@Composable
fun OxoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00FF41)), content = content)
}
