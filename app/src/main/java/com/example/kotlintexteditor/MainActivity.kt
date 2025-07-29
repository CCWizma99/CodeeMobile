package com.example.kotlintexteditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults.outlinedTextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.kotlintexteditor.ui.theme.KotlinTextEditorTheme

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

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorApp() {
    var code by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { code = "" }) {
                Text("New")
            }

            Button(onClick = { /* TODO: Open File */ }) {
                Text("Open")
            }

            Button(onClick = { /* TODO: Save File */ }) {
                Text("Save")
            }

            Button(onClick = { output = "Compile success (or error...)" }) {
                Text("Compile")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            textStyle = TextStyle(color = Color.White),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = Color.DarkGray,       // background
                focusedTextColor = Color.White,        // text color when focused
                unfocusedTextColor = Color.White,      // text color when unfocused
                cursorColor = Color.White,
                focusedBorderColor = Color.LightGray,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White
            ),
            placeholder = { Text("Write your Kotlin code here...") },
            maxLines = Int.MAX_VALUE,
            singleLine = false
        )

        if (output.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = output,
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
