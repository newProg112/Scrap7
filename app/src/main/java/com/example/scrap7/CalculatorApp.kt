package com.example.scrap7

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/*
fun main() {

    var number1: Int = readln().toInt()
    var number2: Int = readln().toInt()

    add(number1, number2)
    subtract(number1, number2)
}

 */

//var number1: Int by remember { mutableStateOf(0) }
//var number2: Int by remember { mutableStateOf(0) }



fun add(a: Int, b: Int): Int {
    var a = readln().toInt()

    println(a + b)
    return a + b
    a = a + b
}

fun subtract(a: Int, b: Int): Int {
    println(a - b)
    return a - b
}

fun multiply(a: Int, b: Int): Int {
    println(a * b)
    return a * b
}

fun division(a: Int, b: Int): Int {
    println(a / b)
    return a / b
}

@Composable
fun CalculatorApp() {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    fun calculate() {
        try {
            result = /*eval*/(input).toString()
        } catch (e: Exception) {
            result = "Error"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = input, fontSize = 28.sp)
        Text(text = result, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)

        val buttons = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "=", "+"),
        )

        for (row in buttons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (label in row) {
                    Button(
                        onClick = {
                            when (label) {
                                "=" -> calculate()
                                else -> input += label
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { input = ""; result = "" },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { input = input.dropLast(1) },
                modifier = Modifier.weight(1f)
            ) {
                Text("⌫")
            }
        }
    }
}