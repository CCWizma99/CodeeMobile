package com.example.kotlintexteditor

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                TextEditorApp()
            }
        }
    }
}

// Syntax highlighting theme colors
object SyntaxColors {
    val keyword = Color(0xFF569CD6)          // Blue for keywords
    val string = Color(0xFFD69E2E)           // Orange for strings
    val comment = Color(0xFF6A9955)          // Green for comments
    val number = Color(0xFFB5CEA8)           // Light green for numbers
    val function = Color(0xFFDCDCAA)         // Yellow for functions
    val type = Color(0xFF4EC9B0)             // Cyan for types
    val operator = Color(0xFFD4D4D4)         // Light gray for operators
    val normal = Color(0xFFD4D4D4)           // Default text color
}

// Language detection and syntax highlighting
class SyntaxHighlighter {

    private val languageKeywords = mapOf(
        "kotlin" to setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
            "const", "constructor", "continue", "crossinline", "data", "do", "dynamic", "else",
            "enum", "expect", "external", "false", "field", "file", "final", "finally", "for",
            "fun", "get", "if", "import", "in", "init", "inline", "inner", "interface", "internal",
            "is", "lateinit", "noinline", "null", "object", "open", "operator", "out", "override",
            "package", "param", "private", "property", "protected", "public", "receiver", "reified",
            "return", "sealed", "set", "setparam", "super", "suspend", "tailrec", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "vararg", "when", "where", "while"
        ),
        "java" to setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "null", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false"
        ),
        "python" to setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield", "print", "len", "range", "str", "int", "float"
        ),
        "cpp" to setOf(
            "alignas", "alignof", "and", "and_eq", "asm", "atomic_cancel", "atomic_commit",
            "atomic_noexcept", "auto", "bitand", "bitor", "bool", "break", "case", "catch",
            "char", "char8_t", "char16_t", "char32_t", "class", "compl", "concept", "const",
            "consteval", "constexpr", "constinit", "const_cast", "continue", "co_await", "co_return",
            "co_yield", "decltype", "default", "delete", "do", "double", "dynamic_cast", "else",
            "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto",
            "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not",
            "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected", "public",
            "reflexpr", "register", "reinterpret_cast", "requires", "return", "short", "signed",
            "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "synchronized",
            "template", "this", "thread_local", "throw", "true", "try", "typedef", "typeid",
            "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t",
            "while", "xor", "xor_eq", "include", "define", "undef", "ifdef", "ifndef", "endif"
        ),
        "c" to setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
            "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
            "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
            "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "_Alignas",
            "_Alignof", "_Atomic", "_Static_assert", "_Noreturn", "_Thread_local", "_Generic",
            "include", "define", "undef", "ifdef", "ifndef", "endif", "printf", "scanf", "malloc", "free"
        )
    )

    fun detectLanguage(filename: String, content: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "cpp", "cc", "cxx", "c++" -> "cpp"
            "c", "h" -> "c"
            else -> {
                // Try to detect based on content patterns
                when {
                    content.contains("fun main") || content.contains("class") && content.contains("kotlin") -> "kotlin"
                    content.contains("public static void main") || content.contains("System.out.println") -> "java"
                    content.contains("def ") || content.contains("import ") && content.contains("python") -> "python"
                    content.contains("#include <iostream>") || content.contains("std::") -> "cpp"
                    content.contains("#include <stdio.h>") || content.contains("printf") -> "c"
                    else -> "kotlin" // default
                }
            }
        }
    }

    fun highlightSyntax(text: String, language: String): AnnotatedString {
        return buildAnnotatedString {
            val keywords = languageKeywords[language] ?: languageKeywords["kotlin"]!!

            var currentIndex = 0
            val length = text.length

            while (currentIndex < length) {
                when {
                    // Handle single-line comments
                    (language in setOf("kotlin", "java", "cpp", "c") && text.startsWith("//", currentIndex)) ||
                            (language == "python" && text.startsWith("#", currentIndex)) -> {
                        val endIndex = text.indexOf('\n', currentIndex).let { if (it == -1) length else it }
                        withStyle(SpanStyle(color = SyntaxColors.comment)) {
                            append(text.substring(currentIndex, endIndex))
                        }
                        currentIndex = endIndex
                    }

                    // Handle multi-line comments for C-style languages
                    language in setOf("kotlin", "java", "cpp", "c") && text.startsWith("/*", currentIndex) -> {
                        val endIndex = text.indexOf("*/", currentIndex + 2).let {
                            if (it == -1) length else it + 2
                        }
                        withStyle(SpanStyle(color = SyntaxColors.comment)) {
                            append(text.substring(currentIndex, endIndex))
                        }
                        currentIndex = endIndex
                    }

                    // Handle strings (double quotes)
                    text[currentIndex] == '"' -> {
                        val endIndex = findStringEnd(text, currentIndex, '"')
                        withStyle(SpanStyle(color = SyntaxColors.string)) {
                            append(text.substring(currentIndex, endIndex))
                        }
                        currentIndex = endIndex
                    }

                    // Handle strings (single quotes)
                    text[currentIndex] == '\'' -> {
                        val endIndex = findStringEnd(text, currentIndex, '\'')
                        withStyle(SpanStyle(color = SyntaxColors.string)) {
                            append(text.substring(currentIndex, endIndex))
                        }
                        currentIndex = endIndex
                    }

                    // Handle numbers
                    text[currentIndex].isDigit() -> {
                        val endIndex = findNumberEnd(text, currentIndex)
                        withStyle(SpanStyle(color = SyntaxColors.number)) {
                            append(text.substring(currentIndex, endIndex))
                        }
                        currentIndex = endIndex
                    }

                    // Handle identifiers and keywords
                    text[currentIndex].isLetter() || text[currentIndex] == '_' -> {
                        val endIndex = findIdentifierEnd(text, currentIndex)
                        val word = text.substring(currentIndex, endIndex)

                        when {
                            keywords.contains(word) -> {
                                withStyle(SpanStyle(color = SyntaxColors.keyword, fontWeight = FontWeight.Bold)) {
                                    append(word)
                                }
                            }
                            // Function detection (followed by parentheses)
                            endIndex < length && text[endIndex] == '(' -> {
                                withStyle(SpanStyle(color = SyntaxColors.function)) {
                                    append(word)
                                }
                            }
                            // Type detection (capitalized words)
                            word[0].isUpperCase() -> {
                                withStyle(SpanStyle(color = SyntaxColors.type)) {
                                    append(word)
                                }
                            }
                            else -> {
                                withStyle(SpanStyle(color = SyntaxColors.normal)) {
                                    append(word)
                                }
                            }
                        }
                        currentIndex = endIndex
                    }

                    // Handle operators
                    text[currentIndex] in "+-*/=<>!&|^%~" -> {
                        withStyle(SpanStyle(color = SyntaxColors.operator)) {
                            append(text[currentIndex])
                        }
                        currentIndex++
                    }

                    // Default case
                    else -> {
                        withStyle(SpanStyle(color = SyntaxColors.normal)) {
                            append(text[currentIndex])
                        }
                        currentIndex++
                    }
                }
            }
        }
    }

    private fun findStringEnd(text: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == quote -> return i + 1
                text[i] == '\\' && i + 1 < text.length -> i += 2 // Skip escaped character
                else -> i++
            }
        }
        return text.length
    }

    private fun findNumberEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == 'f' || text[i] == 'L')) {
            i++
        }
        return i
    }

    private fun findIdentifierEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) {
            i++
        }
        return i
    }
}

@Composable
fun SyntaxHighlightedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle()
) {
    val syntaxHighlighter = remember { SyntaxHighlighter() }
    val highlightedText = remember(value.text, language) {
        syntaxHighlighter.highlightSyntax(value.text, language)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        cursorBrush = SolidColor(SyntaxColors.normal),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        text = "Enter your code here...",
                        color = Color(0xFF808080),
                        fontSize = textStyle.fontSize,
                        fontFamily = textStyle.fontFamily
                    )
                }
                // Show syntax highlighted text as overlay
                if (value.text.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            text = highlightedText,
                            style = textStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // Invisible text field for input
                Box(modifier = Modifier.fillMaxWidth()) {
                    innerTextField()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorApp() {
    var codeFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var output by remember { mutableStateOf("") }
    var codeHistory by remember { mutableStateOf(listOf<String>()) }
    var currentFileName by remember { mutableStateOf("untitled.kt") }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showFileNameDialog by remember { mutableStateOf(false) }
    var tempFileName by remember { mutableStateOf("") }
    var pendingCompile by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val syntaxHighlighter = remember { SyntaxHighlighter() }

    // Detect current language based on filename and content
    val currentLanguage = remember(currentFileName, codeFieldValue.text) {
        syntaxHighlighter.detectLanguage(currentFileName, codeFieldValue.text)
    }

    fun saveFileDirectly(fileName: String): Boolean {
        return try {
            val codedDir = File(context.filesDir, "coded")
            if (!codedDir.exists()) {
                codedDir.mkdirs()
            }

            val sourceFile = File(codedDir, fileName)
            sourceFile.writeText(codeFieldValue.text)

            currentFileName = fileName
            currentFilePath = sourceFile.absolutePath

            Toast.makeText(context, "File saved directly: ${sourceFile.absolutePath}", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun performCompile(context: Context, fileName: String, onResult: (String) -> Unit) {
        try {
            val codedDir = File(context.getExternalFilesDir(null), "coded")
            if (!codedDir.exists()) {
                codedDir.mkdirs()
            }

            val sourceFile = File(codedDir, fileName)
            sourceFile.writeText(codeFieldValue.text)

            val runFile = File(codedDir, "run.txt")
            val writeSuccess = try {
                runFile.writeText(fileName)
                true
            } catch (e: Exception) {
                false
            }

            val result = when {
                codeFieldValue.text.trim().isEmpty() -> "❌ Error: No code to compile"
                !writeSuccess -> "❌ Error: Failed to generate run.txt trigger file"
                codeFieldValue.text.contains("fun main") -> {
                    "✅ Compilation request sent!\n🔥 Generated run.txt: ${runFile.absolutePath}\n📁 Source file: ${sourceFile.absolutePath}\n🚀 PC watcher will pull and compile automatically"
                }
                codeFieldValue.text.contains("class") && codeFieldValue.text.contains("{") -> {
                    "✅ Class compilation requested!\n🔥 Generated run.txt: ${runFile.absolutePath}\n📁 Source file: ${sourceFile.absolutePath}\n🚀 PC watcher will pull and compile automatically"
                }
                codeFieldValue.text.contains("println") || codeFieldValue.text.contains("print") -> {
                    "✅ Code compilation requested!\n🔥 Generated run.txt: ${runFile.absolutePath}\n📁 Source file: ${sourceFile.absolutePath}\n🚀 PC watcher will pull and compile automatically"
                }
                else -> {
                    "⚠️ Warning: Code may have syntax issues\n🔥 Generated run.txt anyway: ${runFile.absolutePath}\n📁 Source file: ${sourceFile.absolutePath}\n🚀 PC watcher will pull and attempt compilation"
                }
            }

            val outputFile = File(codedDir, fileName.substringBeforeLast('.') + ".txt")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            onResult(result)
            Toast.makeText(context, "run.txt created! PC watcher should detect it.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            onResult("💥 Error during compilation: ${e.message}")
            Toast.makeText(context, "Compilation error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    if (codeFieldValue.text.isNotEmpty()) {
                        codeHistory = codeHistory + codeFieldValue.text
                    }
                    codeFieldValue = TextFieldValue(content)
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "opened_file.kt"
                    currentFileName = fileName
                    currentFilePath = uri.toString()
                    Toast.makeText(context, "File opened successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                outputStream?.use { stream ->
                    stream.write(codeFieldValue.text.toByteArray())
                }
                currentFilePath = uri.toString()
                Toast.makeText(context, "File saved to documents (may have modified extension)", Toast.LENGTH_SHORT).show()

                if (pendingCompile) {
                    pendingCompile = false
                    performCompile(context, currentFileName) { result: String ->
                        output = result
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                pendingCompile = false
            }
        }
    }

    // Custom colors matching the dark theme
    val backgroundColor = Color(0xFF1A1A1A)
    val editorBackground = Color(0xFF2D2D2D)
    val buttonBackground = Color(0xFFE8E8E8)
    val buttonTextColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFFE0E0E0)
    val accentColor = Color(0xFF4A90E2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Header with CODED branding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CODED",
                color = textColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = 0.dp, vertical = 10.dp),
            ) {
                Button(
                    onClick = {
                        if (codeFieldValue.text.isNotEmpty()) {
                            showFileNameDialog = true
                        } else {
                            Toast.makeText(context, "No code to save", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTextColor
                    ),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        openFileLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTextColor
                    ),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Open", fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // File name display and language info
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "File: $currentFileName",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Language: ${currentLanguage.uppercase()}",
                    color = SyntaxColors.keyword.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Phone Path: /storage/emulated/0/Android/data/com.example.kotlintexteditor/files/coded/",
                color = textColor.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
            Text(
                text = "PC pulls from above path continuously",
                color = Color(0xFF4A90E2).copy(alpha = 0.7f),
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Main code editor with syntax highlighting
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = editorBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            SyntaxHighlightedTextField(
                value = codeFieldValue,
                onValueChange = { codeFieldValue = it },
                language = currentLanguage,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                textStyle = TextStyle(
                    color = SyntaxColors.normal,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo button
            IconButton(
                onClick = {
                    if (codeHistory.isNotEmpty()) {
                        codeFieldValue = TextFieldValue(codeHistory.last())
                        codeHistory = codeHistory.dropLast(1)
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = buttonBackground,
                        shape = RoundedCornerShape(25.dp)
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Undo",
                    tint = buttonTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Compile button
            Button(
                onClick = {
                    if (codeFieldValue.text.trim().isEmpty()) {
                        output = "❌ Error: No code to compile"
                        return@Button
                    }

                    if (currentFileName.isBlank() || currentFileName == "untitled.kt") {
                        showSaveDialog = true
                        pendingCompile = true
                    } else {
                        performCompile(context, currentFileName) { result: String ->
                            output = result
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBackground,
                    contentColor = buttonTextColor
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .height(50.dp)
                    .widthIn(min = 120.dp)
            ) {
                Text(
                    "Compile",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }

            // Reset/Refresh button
            IconButton(
                onClick = {
                    if (codeFieldValue.text.isNotEmpty()) {
                        codeHistory = codeHistory + codeFieldValue.text
                    }
                    codeFieldValue = TextFieldValue("")
                    output = ""
                    currentFileName = "untitled.kt"
                    currentFilePath = null
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = buttonBackground,
                        shape = RoundedCornerShape(25.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = buttonTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center
        ){
            Button(
                onClick = {
                    val codedDir = File(context.getExternalFilesDir(null), "coded")
                    val outputFile = File(codedDir, currentFileName.substringBeforeLast('.') + ".txt")

                    if (outputFile.exists()) {
                        val executionOutput = outputFile.readText()
                        output = "📤 Latest Output from PC:\n$executionOutput"
                        Toast.makeText(context, "Output loaded!", Toast.LENGTH_SHORT).show()
                    } else {
                        output = "⚠️ No output file found yet.\nPC may still be processing or compilation failed."
                        Toast.makeText(context, "No output file found", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBackground,
                    contentColor = buttonTextColor
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .height(50.dp)
                    .widthIn(min = 120.dp)
            ) {
                Text("Check Output",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }

        // Output section
        if (output.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .heightIn(min = 200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = editorBackground
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    // Close/Hide icon
                    IconButton(
                        onClick = { output = "" },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hide output",
                            tint = Color.Gray
                        )
                    }

                    // Output text
                    Text(
                        text = output,
                        color = when {
                            output.contains("✅") -> Color(0xFF4CAF50)
                            output.contains("⚠️") -> Color(0xFFFF9800)
                            output.contains("🔥") -> Color(0xFF2196F3)
                            else -> Color(0xFFFF5252)
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.CenterStart)
                    )
                }
            }
        }

        // File Name Dialog
        if (showFileNameDialog) {
            AlertDialog(
                onDismissRequest = { showFileNameDialog = false },
                title = { Text("Enter filename") },
                text = {
                    Column {
                        Text(
                            "Files are saved directly to app storage to avoid extension issues.",
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = tempFileName,
                            onValueChange = { tempFileName = it },
                            label = { Text("Filename (with extension)") },
                            placeholder = { Text("example.kt, test.py, hello.c") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (tempFileName.isNotBlank() && tempFileName.contains('.')) {
                                    if (saveFileDirectly(tempFileName)) {
                                        codeHistory = codeHistory + codeFieldValue.text
                                        showFileNameDialog = false
                                        tempFileName = ""
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter a valid filename with extension", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Save Direct")
                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showFileNameDialog = false
                            tempFileName = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Save Dialog (for compilation)
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSaveDialog = false
                    pendingCompile = false
                },
                title = { Text("Save Required") },
                text = { Text("Your code needs to be saved before compilation. Enter a filename:") },
                confirmButton = {
                    Button(
                        onClick = {
                            showSaveDialog = false
                            showFileNameDialog = true
                        }
                    ) {
                        Text("Enter Filename")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showSaveDialog = false
                            pendingCompile = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}