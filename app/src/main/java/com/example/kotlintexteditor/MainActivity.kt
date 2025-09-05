package com.example.kotlintexteditor

import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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
    val operator = Color(0xFF0040FF)         // Blue for operators
    val normal = Color(0xFFD4D4D4)           // Default text color
    val warning = Color(0xFFFFA500)          // Orange for warnings
}

// ADVANCED: Enhanced history state with cursor position tracking
data class EditorState(
    val text: String,
    val selection: TextRange,
    val timestamp: Long = System.currentTimeMillis()
)

// ADVANCED: History manager with intelligent change detection
class AdvancedHistoryManager {
    private var undoStack = mutableListOf<EditorState>()
    private var redoStack = mutableListOf<EditorState>()
    private var lastSavedState: EditorState? = null

    // Configuration
    private val maxHistorySize = 100
    private val minSignificantChange = 3 // Minimum characters for significant change
    private val maxTimeBetweenChanges = 2000L // 2 seconds

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun getUndoCount(): Int = undoStack.size
    fun getRedoCount(): Int = redoStack.size

    private fun isSignificantChange(oldState: EditorState?, newState: EditorState): Boolean {
        if (oldState == null) return true

        val textDiff = kotlin.math.abs(newState.text.length - oldState.text.length)
        val timeDiff = newState.timestamp - oldState.timestamp
        val selectionChanged = newState.selection != oldState.selection

        return when {
            // Significant text change
            textDiff >= minSignificantChange -> true
            // Long time between changes (user paused typing)
            timeDiff > maxTimeBetweenChanges -> true
            // Selection change with some text change
            selectionChanged && textDiff > 0 -> true
            // Different lines (enter/delete line)
            newState.text.count { it == '\n' } != oldState.text.count { it == '\n' } -> true
            else -> false
        }
    }

    // ADVANCED: Add state with intelligent merging
    fun addState(newState: EditorState, forceAdd: Boolean = false) {
        val shouldAdd = forceAdd || isSignificantChange(lastSavedState, newState)

        if (shouldAdd) {
            // Add current state to undo stack
            lastSavedState?.let { undoStack.add(it) }

            // Clear redo stack on new action
            redoStack.clear()

            // Limit stack size
            if (undoStack.size > maxHistorySize) {
                undoStack.removeAt(0)
            }

            lastSavedState = newState
        } else {
            // Just update the current state without adding to history
            lastSavedState = newState
        }
    }

    // ADVANCED: Undo with cursor position restoration
    fun undo(): EditorState? {
        if (undoStack.isEmpty()) return null

        val currentState = lastSavedState
        val previousState = undoStack.removeLastOrNull() ?: return null

        // Add current state to redo stack
        currentState?.let { redoStack.add(it) }

        lastSavedState = previousState
        return previousState
    }

    // ADVANCED: Redo with cursor position restoration
    fun redo(): EditorState? {
        if (redoStack.isEmpty()) return null

        val currentState = lastSavedState
        val nextState = redoStack.removeLastOrNull() ?: return null

        // Add current state to undo stack
        currentState?.let { undoStack.add(it) }

        lastSavedState = nextState
        return nextState
    }


    // ADVANCED: Clear all history
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastSavedState = null
    }

    // ADVANCED: Force save current state (for major operations)
    fun forceSaveState(state: EditorState) {
        addState(state, forceAdd = true)
    }
}

// XML parser for language keywords
class LanguageKeywordsParser(private val context: Context) {

    fun parseKeywordsFromXml(): Map<String, Set<String>> {
        val keywordsMap = mutableMapOf<String, Set<String>>()

        try {
            val parser = context.resources.getXml(R.xml.language_keywords)

            var eventType = parser.eventType
            var currentLanguage: String? = null
            var currentKeywords = mutableSetOf<String>()

            while (eventType != XmlResourceParser.END_DOCUMENT) {
                when (eventType) {
                    XmlResourceParser.START_TAG -> {
                        when (parser.name) {
                            "language" -> {
                                currentLanguage = parser.getAttributeValue(null, "name")
                                currentKeywords = mutableSetOf()
                            }
                            "keyword" -> {
                                val keyword = parser.nextText().trim()
                                if (keyword.isNotEmpty()) {
                                    currentKeywords.add(keyword)
                                }
                            }
                        }
                    }
                    XmlResourceParser.END_TAG -> {
                        if (parser.name == "language" && currentLanguage != null) {
                            keywordsMap[currentLanguage] = currentKeywords.toSet()
                            currentLanguage = null
                        }
                    }
                }
                eventType = parser.next()
            }
            parser.close()
        } catch (e: Exception) {
        }

        return keywordsMap
    }
}

class SyntaxRuleParser(private val context: Context) {

    data class SyntaxRule(
        val type: String,
        val pattern: Regex,
        val message: String,
        val severity: AdvancedSyntaxChecker.SyntaxError.Severity
    )

    private var cachedRules: Map<String, List<SyntaxRule>>? = null

    fun getSyntaxRules(): Map<String, List<SyntaxRule>> {
        if (cachedRules != null) return cachedRules!!

        val rulesMap = mutableMapOf<String, MutableList<SyntaxRule>>()

        try {
            val parser = context.resources.getXml(R.xml.syntax_rules)

            var eventType = parser.eventType
            var currentLanguage: String? = null

            while (eventType != XmlResourceParser.END_DOCUMENT) {
                when (eventType) {
                    XmlResourceParser.START_TAG -> {
                        when (parser.name) {
                            "language" -> {
                                currentLanguage = parser.getAttributeValue(null, "name")
                                if (currentLanguage != null && !rulesMap.containsKey(currentLanguage)) {
                                    rulesMap[currentLanguage] = mutableListOf()
                                }
                            }
                            "rule" -> {
                                if (currentLanguage != null) {
                                    val type = parser.getAttributeValue(null, "type") ?: ""
                                    val message = parser.getAttributeValue(null, "message") ?: "Syntax error"
                                    val severityStr = parser.getAttributeValue(null, "severity") ?: "error"

                                    val severity = when (severityStr.lowercase()) {
                                        "warning" -> AdvancedSyntaxChecker.SyntaxError.Severity.WARNING
                                        "info" -> AdvancedSyntaxChecker.SyntaxError.Severity.WARNING
                                        else -> AdvancedSyntaxChecker.SyntaxError.Severity.ERROR
                                    }

                                    // Read pattern from child <pattern> element
                                    var pattern = ""
                                    val depth = parser.depth

                                    while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                                        if (parser.eventType == XmlResourceParser.START_TAG && parser.name == "pattern") {
                                            pattern = parser.nextText().trim()
                                            break
                                        } else if (parser.eventType == XmlResourceParser.END_TAG &&
                                            parser.name == "rule" && parser.depth <= depth) {
                                            break
                                        }
                                    }

                                    if (pattern.isNotEmpty()) {
                                        try {
                                            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                                            val rule = SyntaxRule(type, regex, message, severity)
                                            rulesMap[currentLanguage]?.add(rule)
                                        } catch (e: Exception) {
                                            // Skip invalid regex patterns
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            parser.close()
        } catch (e: Exception) {
            // Fallback to hardcoded rules if XML parsing fails
            return getDefaultSyntaxRules()
        }

        cachedRules = rulesMap
        return rulesMap
    }

    private fun getDefaultSyntaxRules(): Map<String, List<SyntaxRule>> {
        return mapOf(
            "kotlin" to listOf(
                SyntaxRule("function_syntax", Regex("fun\\s+\\w+\\s*\\{"), "Function missing parentheses", AdvancedSyntaxChecker.SyntaxError.Severity.ERROR),
                SyntaxRule("class_brace", Regex("class\\s+\\w+[^{]*$"), "Class missing opening brace", AdvancedSyntaxChecker.SyntaxError.Severity.ERROR)
            ),
            "java" to listOf(
                SyntaxRule("method_semicolon", Regex("System\\.out\\.println[^;]*[^}]\\s*$"), "Missing semicolon", AdvancedSyntaxChecker.SyntaxError.Severity.ERROR)
            ),
            "python" to listOf(
                SyntaxRule("function_colon", Regex("def\\s+\\w+\\([^)]*\\)[^:]*$"), "Function missing colon", AdvancedSyntaxChecker.SyntaxError.Severity.ERROR)
            )
        )
    }
}

// Advanced Syntax Checker with XML-based rules and context analysis
class AdvancedSyntaxChecker(context: Context) {

    data class SyntaxError(
        val line: Int,
        val column: Int,
        val message: String,
        val severity: Severity,
        val type: String = "general",
        val suggestion: String? = null
    ) {
        enum class Severity { ERROR, WARNING, INFO }
    }

    private val ruleParser = SyntaxRuleParser(context)
    private var lastErrors: List<SyntaxError> = emptyList()

    fun getLastErrors(): List<SyntaxError> = lastErrors

    fun checkSyntax(code: String, language: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val lines = code.lines()
        val rules = ruleParser.getSyntaxRules()[language] ?: emptyList()

        // Context-aware analysis
        val codeContext = analyzeCodeContext(code)

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmedLine = line.trim()

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("#")) {
                return@forEachIndexed
            }

            if (language == "c" || language == "cpp") {
                if (!code.contains(Regex("#include\\s*<stdio\\.h>"))) {
                    errors.add(
                        SyntaxError(
                            1, 0,
                            "Missing #include <stdio.h>",
                            SyntaxError.Severity.ERROR,
                            "missing_include",
                            "Add: #include <stdio.h>"
                        )
                    )
                }
            }


            // Apply XML-defined rules
            rules.forEach { rule ->
                val match = rule.pattern.find(line)
                if (match != null) {
                    val column = match.range.first
                    val suggestion = generateSuggestion(rule.type)

                    errors.add(
                        SyntaxError(
                            line = lineNumber,
                            column = column,
                            message = rule.message,
                            severity = rule.severity,
                            type = rule.type,
                            suggestion = suggestion
                        )
                    )
                }
            }

            // Advanced context-based checks
            errors.addAll(performContextualAnalysis(line, lineNumber, codeContext, language))
        }

        // 🔥 Whole-file analyzers (multi-line aware)
        errors.addAll(analyzeBrackets(code))
        errors.addAll(analyzeStrings(code))
        errors.addAll(analyzeMultiLineStructures(code, language))

        // Remove duplicates and sort by line number
        lastErrors = errors.distinctBy { "${it.line}-${it.column}-${it.type}" }
            .sortedWith(compareBy({ it.line }, { it.severity }))

        return lastErrors
    }

    private fun analyzeCodeContext(code: String): Map<String, Any> {
        return mapOf(
            "hasMain" to (code.contains("fun main") || code.contains("public static void main")),
            "hasClasses" to code.contains(Regex("class\\s+\\w+")),
            "hasFunctions" to (code.contains("fun ") || code.contains("def ") || code.contains("void ")),
            "indentationLevel" to calculateIndentationLevel(code),
            "bracketDepth" to calculateBracketDepth(code)
        )
    }

    private fun performContextualAnalysis(
        line: String,
        lineNumber: Int,
        context: Map<String, Any>,
        language: String
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val trimmed = line.trim()

        when (language) {
            "kotlin" -> {
                if (line.contains("fun ") && !line.contains("(")) {
                    errors.add(
                        SyntaxError(
                            lineNumber, line.indexOf("fun"),
                            "Function declaration missing parentheses",
                            SyntaxError.Severity.ERROR,
                            "context_hint",
                            "Add parentheses: fun functionName()"
                        )
                    )
                }

                if (line.contains("val ") && !line.contains("=")) {
                    errors.add(
                        SyntaxError(
                            lineNumber, line.indexOf("val"),
                            "Val declaration without initialization",
                            SyntaxError.Severity.WARNING,
                            "kotlin_val",
                            "Initialize with: val name = value"
                        )
                    )
                }
            }

            "java", "cpp", "c" -> {
                if (
                    trimmed.isNotEmpty() &&
                    !trimmed.startsWith("//") &&
                    !trimmed.startsWith("/*") &&
                    !trimmed.startsWith("*") &&
                    !trimmed.startsWith("class") &&
                    !trimmed.startsWith("public class") &&
                    !trimmed.startsWith("if") &&
                    !trimmed.startsWith("for") &&
                    !trimmed.startsWith("while") &&
                    !trimmed.endsWith(";") &&
                    !trimmed.endsWith("{") &&
                    !trimmed.endsWith("}") &&
                    !trimmed.endsWith(":")
                ) {
                    errors.add(
                        SyntaxError(
                            lineNumber, line.length,
                            "Missing semicolon",
                            SyntaxError.Severity.ERROR,
                            "missing_semicolon",
                            "Add semicolon at end of line"
                        )
                    )
                }

                if (language == "java" && line.contains("public class") && !(context["hasMain"] as Boolean)) {
                    errors.add(
                        SyntaxError(
                            lineNumber, 0,
                            "Class without main method - consider adding main",
                            SyntaxError.Severity.INFO,
                            "java_structure"
                        )
                    )
                }
            }

            "python" -> {
                val actualIndent = line.takeWhile { it == ' ' || it == '\t' }.length

                if (line.startsWith("def ") || line.startsWith("class ") || line.startsWith("if ")) {
                    if (actualIndent % 4 != 0) {
                        errors.add(
                            SyntaxError(
                                lineNumber, 0,
                                "Inconsistent indentation - use 4 spaces",
                                SyntaxError.Severity.WARNING,
                                "python_indent",
                                "Use 4-space indentation"
                            )
                        )
                    }
                }
            }
        }

        return errors
    }

    // 🔥 Multi-line bracket analysis
    private fun analyzeBrackets(code: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val stack = mutableListOf<Triple<Char, Int, Int>>() // char, line, col

        code.lines().forEachIndexed { lineIndex, line ->
            line.forEachIndexed { col, char ->
                when (char) {
                    '(', '[', '{' -> stack.add(Triple(char, lineIndex + 1, col))
                    ')', ']', '}' -> {
                        if (stack.isEmpty()) {
                            errors.add(
                                SyntaxError(
                                    lineIndex + 1, col,
                                    "Unmatched closing bracket",
                                    SyntaxError.Severity.ERROR,
                                    "bracket_mismatch",
                                    "Remove or match with opening bracket"
                                )
                            )
                        } else {
                            val lastIndex = stack.size - 1
                            val (open, _, _) = stack[lastIndex]
                            stack.removeAt(lastIndex)

                            val expected = when (open) {
                                '(' -> ')'
                                '[' -> ']'
                                '{' -> '}'
                                else -> char
                            }
                            if (char != expected) {
                                errors.add(
                                    SyntaxError(
                                        lineIndex + 1, col,
                                        "Mismatched bracket - expected '$expected'",
                                        SyntaxError.Severity.ERROR,
                                        "bracket_mismatch",
                                        "Change to '$expected'"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        stack.forEach { (bracket, line, col) ->
            errors.add(
                SyntaxError(
                    line, col,
                    "Unclosed bracket '$bracket'",
                    SyntaxError.Severity.ERROR,
                    "unclosed_bracket",
                    "Add closing bracket"
                )
            )
        }

        return errors
    }

    private fun analyzeStrings(code: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        var inString: Char? = null
        var stringStartLine = 0
        var stringStartCol = 0
        var escapeNext = false

        code.lines().forEachIndexed { lineIndex, line ->
            var col = 0
            while (col < line.length) {
                val char = line[col]

                when {
                    escapeNext -> escapeNext = false
                    char == '\\' -> escapeNext = true
                    char == '"' || char == '\'' -> {
                        if (inString == null) {
                            // Look ahead to see if this string closes on the same line
                            var closeFound = false
                            var i = col + 1
                            var escape = false
                            while (i < line.length) {
                                val c = line[i]
                                if (escape) {
                                    escape = false
                                } else if (c == '\\') {
                                    escape = true
                                } else if (c == char) {
                                    closeFound = true
                                    break
                                }
                                i++
                            }

                            if (closeFound) {
                                // properly closed on same line → skip
                                col = i
                            } else {
                                // multi-line string start
                                inString = char
                                stringStartLine = lineIndex + 1
                                stringStartCol = col
                            }
                        } else if (inString == char) {
                            // closing multi-line string
                            inString = null
                        }
                    }
                }

                col++
            }

            escapeNext = false
        }

        if (inString != null) {
            errors.add(
                SyntaxError(
                    stringStartLine, stringStartCol,
                    "Unclosed string literal",
                    SyntaxError.Severity.ERROR,
                    "string_literal",
                    "Add closing quote"
                )
            )
        }

        return errors
    }


    private fun analyzeMultiLineStructures(code: String, language: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val lines = code.lines()

        var inMultiLineComment = false
        var commentStart = 0

        lines.forEachIndexed { index, line ->
            when (language) {
                "kotlin", "java", "cpp", "c" -> {
                    if (line.contains("/*") && !line.contains("*/")) {
                        inMultiLineComment = true
                        commentStart = index + 1
                    } else if (line.contains("*/") && inMultiLineComment) {
                        inMultiLineComment = false
                    }
                }
            }
        }

        if (inMultiLineComment) {
            errors.add(
                SyntaxError(
                    commentStart, 0,
                    "Unclosed multi-line comment",
                    SyntaxError.Severity.ERROR,
                    "multiline_comment",
                    "Add closing */"
                )
            )
        }

        return errors
    }

    private fun generateSuggestion(ruleType: String): String? {
        return when (ruleType) {
            "function_syntax" -> "Add parentheses after function name"
            "missing_semicolon" -> "Add semicolon at end of line"
            "function_colon" -> "Add colon after function parameters"
            "class_brace" -> "Add opening brace after class declaration"
            "bracket_mismatch" -> "Check bracket pairing"
            else -> null
        }
    }

    private fun calculateIndentationLevel(code: String): Int {
        val lines = code.lines().filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return 0

        val indents = lines.map { line ->
            line.takeWhile { it == ' ' || it == '\t' }.length
        }

        return indents.filter { it > 0 }.minOrNull() ?: 0
    }

    private fun calculateBracketDepth(code: String): Int {
        var depth = 0
        var maxDepth = 0

        code.forEach { char ->
            when (char) {
                '(', '[', '{' -> {
                    depth++
                    maxDepth = maxOf(maxDepth, depth)
                }
                ')', ']', '}' -> depth--
            }
        }

        return maxDepth
    }
}


// Language detection and syntax highlighting
class EnhancedSyntaxHighlighter(private val context: Context) {
    private val languageKeywords by lazy {
        val parser = LanguageKeywordsParser(context)
        parser.parseKeywordsFromXml()
    }

    private val advancedSyntaxChecker = AdvancedSyntaxChecker(context)

    fun getSyntaxErrors(): List<AdvancedSyntaxChecker.SyntaxError> = advancedSyntaxChecker.getLastErrors()

    fun detectLanguage(filename: String, content: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "cpp", "cc", "cxx", "c++" -> "cpp"
            "c", "h" -> "c"
            else -> {
                when {
                    content.contains("fun main") || content.contains("class") && content.contains("kotlin") -> "kotlin"
                    content.contains("public static void main") || content.contains("System.out.println") -> "java"
                    content.contains("def ") || content.contains("import ") && content.contains("python") -> "python"
                    content.contains("#include <iostream>") || content.contains("std::") -> "cpp"
                    content.contains("#include <stdio.h>") || content.contains("printf") -> "c"
                    else -> "kotlin"
                }
            }
        }
    }

    fun highlightSyntax(text: String, language: String): AnnotatedString {
        // Run advanced syntax checking
        advancedSyntaxChecker.checkSyntax(text, language)
        val errors = advancedSyntaxChecker.getLastErrors()

        return buildAnnotatedString {
            val keywords = languageKeywords[language] ?: languageKeywords["kotlin"] ?: emptySet()

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

            // Add advanced error annotations with different styling based on severity
            errors.forEach { error ->
                val lines = text.lines()
                if (error.line <= lines.size) {
                    val lineStart = lines.take(error.line - 1).sumOf { it.length + 1 }
                    val lineEnd = lineStart + (lines.getOrNull(error.line - 1)?.length ?: 0)

                    if (lineStart < lineEnd) {
                        val backgroundColor = when (error.severity) {
                            AdvancedSyntaxChecker.SyntaxError.Severity.ERROR -> Color(0x60FF0000)
                            AdvancedSyntaxChecker.SyntaxError.Severity.WARNING -> Color(0x60FFA500)
                            AdvancedSyntaxChecker.SyntaxError.Severity.INFO -> Color(0x600080FF)
                        }

                        addStyle(
                            style = SpanStyle(background = backgroundColor),
                            start = lineStart,
                            end = lineEnd
                        )
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
                text[i] == '\\' && i + 1 < text.length -> i += 2
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
fun AdvancedSyntaxHighlightedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    historyManager: AdvancedHistoryManager,
    language: String,
    syntaxHighlighter: EnhancedSyntaxHighlighter,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle()
) {
    LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Debounced auto-save state
    var autoSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Create highlighted text
    val highlightedText = remember(value.text, language) {
        syntaxHighlighter.highlightSyntax(value.text, language)
    }

    val handleTextChange = { newValue: TextFieldValue ->
        onValueChange(newValue)

        // Cancel previous auto-save job
        autoSaveJob?.cancel()

        // Start new debounced auto-save
        autoSaveJob = scope.launch {
            delay(500)
            val state = EditorState(
                text = newValue.text,
                selection = newValue.selection
            )
            historyManager.addState(state)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        BasicTextField(
            value = value,
            onValueChange = handleTextChange,
            textStyle = textStyle.copy(color = Color.Transparent),
            cursorBrush = SolidColor(SyntaxColors.normal),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // Render highlighted text
                    if (value.text.isNotEmpty()) {
                        Text(
                            text = highlightedText,
                            style = textStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Enter your code here...",
                            color = Color(0xFFE8E8E8),
                            fontSize = textStyle.fontSize,
                            fontFamily = textStyle.fontFamily
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        innerTextField()
                    }
                }
            }
        )
    }
}

@Composable
fun FileListItem(file: File, onClick: () -> Unit) {
    val icon = if (file.isDirectory) Icons.Default.ExitToApp else Icons.Default.List
    val iconColor = if (file.isDirectory) Color(0xFF4CAF50) else Color(0xFF2196F3)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (file.isDirectory) "Folder" else "File",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal
                )
                if (file.isFile) {
                    Text(
                        text = "${file.length()} bytes",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun SymbolButton(
    char: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3A3A3A),
            contentColor = Color.White
        ),
        modifier = modifier
            .height(36.dp)
            .width(36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(
            text = char,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SyntaxErrorPanel(
    errors: List<AdvancedSyntaxChecker.SyntaxError>,
    onErrorClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (errors.isNotEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x40FF0000))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    "Syntax Errors (${errors.size})",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                    items(errors) { error ->
                        SyntaxErrorItem(error, onErrorClick)
                    }
                }
            }
        }
    }
}

@Composable
fun SyntaxErrorItem(
    error: AdvancedSyntaxChecker.SyntaxError,
    onClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick(error.line) },
        colors = CardDefaults.cardColors(
            containerColor = when (error.severity) {
                AdvancedSyntaxChecker.SyntaxError.Severity.ERROR -> Color(0x40FF0000)
                AdvancedSyntaxChecker.SyntaxError.Severity.WARNING -> Color(0x40FFA500)
                AdvancedSyntaxChecker.SyntaxError.Severity.INFO -> Color(0x400080FF)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (error.severity) {
                    AdvancedSyntaxChecker.SyntaxError.Severity.ERROR -> Icons.Default.Clear
                    AdvancedSyntaxChecker.SyntaxError.Severity.WARNING -> Icons.Default.Warning
                    AdvancedSyntaxChecker.SyntaxError.Severity.INFO -> Icons.Default.Build

                },
                contentDescription = null,
                tint = when (error.severity) {
                    AdvancedSyntaxChecker.SyntaxError.Severity.ERROR -> Color.Red
                    AdvancedSyntaxChecker.SyntaxError.Severity.WARNING -> Color.Yellow
                    AdvancedSyntaxChecker.SyntaxError.Severity.INFO -> Color.Blue
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "Line ${error.line}: ${error.message}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun TextEditorApp() {
    var codeFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var output by remember { mutableStateOf("") }
    var currentFileName by remember { mutableStateOf("untitled.kt") }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showFileNameDialog by remember { mutableStateOf(false) }
    var tempFileName by remember { mutableStateOf("") }
    var pendingCompile by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showSyntaxErrors by remember { mutableStateOf(true) }

    val historyManager = remember { AdvancedHistoryManager() }

    val context = LocalContext.current
    val syntaxHighlighter = remember { EnhancedSyntaxHighlighter(context) }

    // Detect current language based on filename and content
    val currentLanguage = remember(currentFileName, codeFieldValue.text) {
        syntaxHighlighter.detectLanguage(currentFileName, codeFieldValue.text)
    }

    val syntaxErrors by remember(codeFieldValue.text, currentLanguage) {
        derivedStateOf {
            syntaxHighlighter.highlightSyntax(codeFieldValue.text, currentLanguage)
            syntaxHighlighter.getSyntaxErrors()
        }
    }


    var showFileBrowser by remember { mutableStateOf(false) }
    var currentDirectory by remember {
        mutableStateOf(
            Environment.getExternalStorageDirectory().absolutePath +
                    "/Android/data/com.example.kotlintexteditor/files/coded/"
        )
    }
    var fileList by remember { mutableStateOf<List<File>>(emptyList()) }

    fun getFilesInDirectory(path: String): List<File> {
        return try {
            val directory = File(path)
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    // Refresh file list when directory changes
    LaunchedEffect(currentDirectory) {
        fileList = getFilesInDirectory(currentDirectory)
    }

    // Function to scroll to error line
    fun scrollToLine(lineNumber: Int) {
        val lines = codeFieldValue.text.lines()
        val position = lines.take(lineNumber - 1).sumOf { it.length + 1 }
        codeFieldValue = codeFieldValue.copy(selection = TextRange(position, position))
    }

    // ADVANCED: Force save state for major operations
    fun forceSaveCurrentState() {
        val state = EditorState(
            text = codeFieldValue.text,
            selection = codeFieldValue.selection
        )
        historyManager.forceSaveState(state)
    }

    // ADVANCED: Perform undo with cursor restoration
    fun performUndo() {
        val previousState = historyManager.undo()
        previousState?.let { state ->
            codeFieldValue = TextFieldValue(
                text = state.text,
                selection = state.selection
            )
        }
    }

    // ADVANCED: Perform redo with cursor restoration
    fun performRedo() {
        val nextState = historyManager.redo()
        nextState?.let { state ->
            codeFieldValue = TextFieldValue(
                text = state.text,
                selection = state.selection
            )
        }
    }


    fun openFileFromStorage(file: File, context: Context): Boolean {
        return try {
            val content = file.readText()
            codeFieldValue = TextFieldValue(content)
            currentFileName = file.name
            currentFilePath = file.absolutePath
            showFileBrowser = false
            Toast.makeText(context, "File opened: ${file.name}", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun saveFileDirectly(fileName: String): Boolean {
        // Force save current state before saving file
        forceSaveCurrentState()

        return try {
            val codedDir = File(context.getExternalFilesDir(null), "coded")
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
                !writeSuccess -> "❌ Error: Failed to send the compilation request!"
                codeFieldValue.text.contains("fun main") -> {
                    "✅ Compilation request sent!\nPC watcher will pull and compile automatically."
                }
                codeFieldValue.text.contains("class") && codeFieldValue.text.contains("{") -> {
                    "✅ Class compilation requested!\nPC watcher will pull and compile automatically"
                }
                codeFieldValue.text.contains("println") || codeFieldValue.text.contains("print") -> {
                    "✅ Code compilation requested!\nPC watcher will pull and compile automatically"
                }
                else -> {
                    "⚠️ Warning: Code may have syntax issues\nAnyway, PC watcher will pull and attempt compilation."
                }
            }

            val outputFile = File(codedDir, fileName.substringBeforeLast('.') + ".txt")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            onResult(result)
            Toast.makeText(context, "PC watcher will handle request. Please wait!.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            onResult("💥 Error during compilation: ${e.message}")
            Toast.makeText(context, "Compilation error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Custom colors matching the dark theme
    val backgroundColor = Color(0xFF1A1A1A)
    val editorBackground = Color(0xFF2D2D2D)
    val buttonBackground = Color(0xFFE8E8E8)
    val buttonTextColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFFE0E0E0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CODEE",
                color = textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = 0.dp, vertical = 5.dp)
                    .padding(WindowInsets.statusBars.asPaddingValues())
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
                    modifier = Modifier
                        .height(32.dp)
                ) {
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        showFileBrowser = true
                        // Set directory to the coded folder
                        currentDirectory = Environment.getExternalStorageDirectory().absolutePath +
                                "/Android/data/com.example.kotlintexteditor/files/coded/"
                        fileList = getFilesInDirectory(currentDirectory)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTextColor
                    ),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Open",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ADVANCED: Enhanced file info with history statistics
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

            // File info and find button
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = "Lines: ${codeFieldValue.text.count { it == '\n' } + 1}",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )

                // Find button - UPDATED to be visible and properly sized
                Button(
                    onClick = { showFindReplaceDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTextColor
                    ),
                    modifier = Modifier
                        .height(32.dp)
                        .width(120.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Find/Replace",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Quick Insert:",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // First row of symbols
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("(", ")", "{", "}", "[", "]").forEach { char ->
                        SymbolButton(
                            char = char,
                            onClick = {
                                val currentText = codeFieldValue.text
                                val selection = codeFieldValue.selection
                                val newText = currentText.substring(0, selection.start) +
                                        char +
                                        currentText.substring(selection.end)
                                val newSelection = TextRange(selection.start + char.length)

                                codeFieldValue = TextFieldValue(newText, newSelection)
                            }
                        )
                    }
                }

                // Second row of symbols
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(";", "\"", "'", "=", "<", ">").forEach { char ->
                        SymbolButton(
                            char = char,
                            onClick = {
                                val currentText = codeFieldValue.text
                                val selection = codeFieldValue.selection
                                val newText = currentText.substring(0, selection.start) +
                                        char +
                                        currentText.substring(selection.end)
                                val newSelection = TextRange(selection.start + char.length)

                                codeFieldValue = TextFieldValue(newText, newSelection)
                            }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = editorBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            AdvancedSyntaxHighlightedTextField(
                value = codeFieldValue,
                onValueChange = { codeFieldValue = it },
                historyManager = historyManager,
                language = currentLanguage,
                syntaxHighlighter = syntaxHighlighter, // Pass the shared instance
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

        if (syntaxErrors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            // Error toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Syntax Issues: ${syntaxErrors.size}",
                    color = when {
                        syntaxErrors.any { it.severity == AdvancedSyntaxChecker.SyntaxError.Severity.ERROR } -> Color.Red
                        else -> Color.Yellow
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showSyntaxErrors = !showSyntaxErrors },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTextColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (showSyntaxErrors) "Hide Errors" else "Show Errors",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Show/hide the error panel based on toggle
            if (showSyntaxErrors) {
                Spacer(modifier = Modifier.height(4.dp))
                SyntaxErrorPanel(
                    errors = syntaxErrors,
                    onErrorClick = { lineNumber -> scrollToLine(lineNumber) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First row: Undo | Reset | Redo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { performUndo() },
                        enabled = historyManager.canUndo(),
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (historyManager.canUndo()) buttonBackground else buttonBackground.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(25.dp)
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Undo",
                            tint = if (historyManager.canUndo()) buttonTextColor else buttonTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (historyManager.getUndoCount() > 0) {
                        Card(
                            modifier = Modifier
                                .size(18.dp)
                                .offset(x = 12.dp, y = (-6).dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (historyManager.canUndo()) Color(0xFF4CAF50) else Color.Gray
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${historyManager.getUndoCount()}",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        // Force save current state before reset
                        forceSaveCurrentState()

                        // Reset everything
                        codeFieldValue = TextFieldValue("")
                        output = ""
                        currentFileName = "untitled.kt"
                        currentFilePath = null
                        historyManager.clear()
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            color = buttonBackground,
                            shape = RoundedCornerShape(25.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Reset",
                        tint = buttonTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { performRedo() },
                        enabled = historyManager.canRedo(),
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (historyManager.canRedo()) buttonBackground else buttonBackground.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(25.dp)
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Redo",
                            tint = if (historyManager.canRedo()) buttonTextColor else buttonTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (historyManager.getRedoCount() > 0) {
                        Card(
                            modifier = Modifier
                                .size(18.dp)
                                .offset(x = 12.dp, y = (-6).dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (historyManager.canRedo()) Color(0xFF2196F3) else Color.Gray
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${historyManager.getRedoCount()}",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Second row: Compile | Check Output | Check Syntax
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ADVANCED: Enhanced Compile button
                Button(
                    onClick = {
                        if (codeFieldValue.text.trim().isEmpty()) {
                            output = "❌ Error: No code to compile"
                            return@Button
                        }

                        // Force save current state before compiling
                        forceSaveCurrentState()

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
                        .height(40.dp)
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        "Compile",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                // Check Output button
                Button(
                    onClick = {
                        val codedDir = File(context.getExternalFilesDir(null), "coded")
                        val outputFile = File(codedDir, currentFileName.substringBeforeLast('.') + ".txt")

                        if (outputFile.exists()) {
                            val executionOutput = outputFile.readText()
                            output = "Latest Output from PC:\n$executionOutput"
                            Toast.makeText(context, "Output loaded!", Toast.LENGTH_SHORT).show()
                        } else {
                            output = "⚠️ No output file found yet.\nPC may still be processing or compilation failed."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTextColor
                    ),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        "Check Output",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Output section
        if (output.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .heightIn(min = 160.dp),
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
                            output.contains("warning") || output.contains("⚠️") -> Color(0xFFFF9800)
                            output.contains(currentFileName) -> Color(0xFFFF0000)
                            else -> Color(0xFF4CAF50)
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
                                        showFileNameDialog = false
                                        tempFileName = ""
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter a valid filename with extension", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Save")
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

        // File Browser Dialog
        if (showFileBrowser) {
            AlertDialog(
                onDismissRequest = { showFileBrowser = false },
                title = {
                    Text("Browse Files: ${File(currentDirectory).name}")
                },
                text = {
                    Column(modifier = Modifier.height(400.dp)) {
                        // Current path
                        Text(
                            text = "Path: $currentDirectory",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // File list - only show files from the coded directory
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(fileList.sortedBy { !it.isDirectory }.sortedBy { it.name }) { file ->
                                FileListItem(
                                    file = file,
                                    onClick = {
                                        // Only allow selecting files, not navigating to other directories
                                        if (file.isFile && file.canRead()) {
                                            openFileFromStorage(file, context)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showFileBrowser = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Find/Replace Dialog
        // Find next occurrence
        fun findNext() {
            if (findText.isEmpty()) return

            val currentText = codeFieldValue.text
            val currentPosition = codeFieldValue.selection.end
            val foundIndex = currentText.indexOf(findText, currentPosition, ignoreCase = true)

            if (foundIndex != -1) {
                codeFieldValue = codeFieldValue.copy(
                    selection = TextRange(foundIndex, foundIndex + findText.length)
                )
            } else {
                // Wrap around to beginning
                val wrappedIndex = currentText.indexOf(findText, 0, ignoreCase = true)
                if (wrappedIndex != -1) {
                    codeFieldValue = codeFieldValue.copy(
                        selection = TextRange(wrappedIndex, wrappedIndex + findText.length)
                    )
                    Toast.makeText(context, "Search wrapped to start", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Text not found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Replace next occurrence
        fun replaceNext() {
            if (findText.isEmpty() || replaceText.isEmpty()) return

            val currentText = codeFieldValue.text
            val currentPosition = codeFieldValue.selection.end
            val foundIndex = currentText.indexOf(findText, currentPosition, ignoreCase = true)

            if (foundIndex != -1) {
                val newText = currentText.replaceRange(foundIndex, foundIndex + findText.length, replaceText)
                codeFieldValue = TextFieldValue(
                    text = newText,
                    selection = TextRange(foundIndex, foundIndex + replaceText.length)
                )
            } else {
                // Try from beginning
                val wrappedIndex = currentText.indexOf(findText, 0, ignoreCase = true)
                if (wrappedIndex != -1) {
                    val newText = currentText.replaceRange(wrappedIndex, wrappedIndex + findText.length, replaceText)
                    codeFieldValue = TextFieldValue(
                        text = newText,
                        selection = TextRange(wrappedIndex, wrappedIndex + replaceText.length)
                    )
                    Toast.makeText(context, "Replacement wrapped to start", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Text not found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Replace all occurrences
        fun replaceAll() {
            if (findText.isEmpty() || replaceText.isEmpty()) return

            val newText = codeFieldValue.text.replace(findText, replaceText, ignoreCase = true)
            codeFieldValue = TextFieldValue(newText)

            val count = (codeFieldValue.text.length - newText.length) / (findText.length - replaceText.length)
            Toast.makeText(context, "Replaced $count occurrences", Toast.LENGTH_SHORT).show()
        }

        if (showFindReplaceDialog) {
            AlertDialog(
                onDismissRequest = { showFindReplaceDialog = false },
                title = {
                    Text(
                        "Find & Replace",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            placeholder = { Text("Enter text to find") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            placeholder = { Text("Replace with") },
                            leadingIcon = {
                                Icon(Icons.Default.Create, contentDescription = null, tint = Color.Black)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { if (findText.isNotEmpty()) findNext() },
                            modifier = Modifier.weight(1f),
                            enabled = findText.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Find")
                        }
                        Button(
                            onClick = { if (findText.isNotEmpty() && replaceText.isNotEmpty()) replaceNext() },
                            modifier = Modifier.weight(1f),
                            enabled = findText.isNotEmpty() && replaceText.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Replace")
                        }
                        Button(
                            onClick = { if (findText.isNotEmpty() && replaceText.isNotEmpty()) replaceAll() },
                            modifier = Modifier.weight(1f),
                            enabled = findText.isNotEmpty() && replaceText.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Replace All")
                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showFindReplaceDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Close")
                    }
                },
                containerColor = Color.White,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(16.dp)
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