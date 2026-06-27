package com.calculadora

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class MainActivity : AppCompatActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView

    private var expression = StringBuilder()
    private var lastInputWasOperator = false
    private var justEvaluated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)

        setupButtons()
    }

    private fun setupButtons() {
        val numberIds = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        numberIds.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener { onDigit(digit) }
        }

        val operatorIds = mapOf(
            R.id.btnAdd to "+",
            R.id.btnSubtract to "−",
            R.id.btnMultiply to "×",
            R.id.btnDivide to "÷"
        )

        operatorIds.forEach { (id, op) ->
            findViewById<Button>(id).setOnClickListener { onOperator(op) }
        }

        findViewById<Button>(R.id.btnDot).setOnClickListener { onDot() }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { onEquals() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { onClear() }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener { onBackspace() }
        findViewById<Button>(R.id.btnPlusMinus).setOnClickListener { onPlusMinus() }
        findViewById<Button>(R.id.btnPercent).setOnClickListener { onPercent() }
    }

    private fun onDigit(digit: String) {
        if (justEvaluated) {
            expression.clear()
            justEvaluated = false
        }
        expression.append(digit)
        lastInputWasOperator = false
        updateDisplay()
    }

    private fun onOperator(op: String) {
        if (expression.isEmpty()) {
            if (op == "−") {
                expression.append(op)
                tvExpression.text = expression
            }
            return
        }

        if (justEvaluated) {
            justEvaluated = false
        }

        if (lastInputWasOperator) {
            expression.deleteCharAt(expression.length - 1)
        }

        expression.append(op)
        lastInputWasOperator = true
        updateDisplay()
    }

    private fun onDot() {
        if (justEvaluated) {
            expression.clear()
            expression.append("0")
            justEvaluated = false
        }

        if (expression.isEmpty()) {
            expression.append("0")
        }

        val lastNumber = getLastNumber()
        if (!lastNumber.contains(".")) {
            expression.append(".")
            lastInputWasOperator = false
            updateDisplay()
        }
    }

    private fun onEquals() {
        if (lastInputWasOperator) return
        if (expression.isEmpty()) return

        val expr = expression.toString()
        val result = evaluate(expr)

        if (result != null) {
            tvExpression.text = "$expr ="
            tvResult.text = formatResult(result)
            expression.clear()
            expression.append(formatResult(result))
            justEvaluated = true
            lastInputWasOperator = false
        } else {
            tvResult.text = "Erro"
        }
    }

    private fun onClear() {
        expression.clear()
        lastInputWasOperator = false
        justEvaluated = false
        tvExpression.text = ""
        tvResult.text = "0"
    }

    private fun onBackspace() {
        if (justEvaluated) {
            onClear()
            return
        }
        if (expression.isNotEmpty()) {
            expression.deleteCharAt(expression.length - 1)
            lastInputWasOperator = expression.isNotEmpty() &&
                    expression.last() in listOf('+', '−', '×', '÷')
            updateDisplay()
        }
    }

    private fun onPlusMinus() {
        if (expression.isEmpty()) return

        val lastNum = getLastNumber()
        if (lastNum.isEmpty() || lastNum == "0") return

        val startIdx = expression.length - lastNum.length
        if (startIdx > 0 && expression[startIdx - 1] == '−' &&
            (startIdx - 2 < 0 || expression[startIdx - 2] in listOf('+', '−', '×', '÷'))
        ) {
            expression.deleteCharAt(startIdx - 1)
        } else {
            expression.insert(startIdx, '−')
        }
        updateDisplay()
    }

    private fun onPercent() {
        if (expression.isEmpty()) return
        val lastNum = getLastNumber()
        if (lastNum.isEmpty()) return

        try {
            val num = toBigDecimal(lastNum)
            val startIdx = expression.length - lastNum.length
            val percentVal = num.divide(BigDecimal("100"), MathContext.DECIMAL64)
            expression.replace(startIdx, expression.length, formatResult(percentVal))
            updateDisplay()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun evaluate(expr: String): BigDecimal? {
        return try {
            val normalized = expr
                .replace("−", "-")
                .replace("×", "*")
                .replace("÷", "/")
            parseExpression(normalized)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseExpression(expr: String): BigDecimal {
        val tokens = tokenize(expr)
        return parseAddSub(tokens, intArrayOf(0))
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            when {
                expr[i].isDigit() || expr[i] == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                expr[i] == '-' && (tokens.isEmpty() || tokens.last() in listOf("+", "-", "*", "/")) -> {
                    val start = i
                    i++
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                expr[i] in listOf('+', '-', '*', '/') -> {
                    tokens.add(expr[i].toString())
                    i++
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun parseAddSub(tokens: List<String>, pos: intArrayOf): BigDecimal {
        var left = parseMulDiv(tokens, pos)
        while (pos[0] < tokens.size && tokens[pos[0]] in listOf("+", "-")) {
            val op = tokens[pos[0]++]
            val right = parseMulDiv(tokens, pos)
            left = if (op == "+") left.add(right) else left.subtract(right)
        }
        return left
    }

    private fun parseMulDiv(tokens: List<String>, pos: intArrayOf): BigDecimal {
        var left = parseNumber(tokens, pos)
        while (pos[0] < tokens.size && tokens[pos[0]] in listOf("*", "/")) {
            val op = tokens[pos[0]++]
            val right = parseNumber(tokens, pos)
            left = if (op == "*") {
                left.multiply(right, MathContext.DECIMAL64)
            } else {
                if (right.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("Division by zero")
                left.divide(right, 10, RoundingMode.HALF_UP).stripTrailingZeros()
            }
        }
        return left
    }

    private fun parseNumber(tokens: List<String>, pos: intArrayOf): BigDecimal {
        if (pos[0] >= tokens.size) throw IllegalArgumentException("Unexpected end")
        return toBigDecimal(tokens[pos[0]++])
    }

    private fun toBigDecimal(s: String): BigDecimal = BigDecimal(s.replace(",", "."))

    private fun getLastNumber(): String {
        val str = expression.toString()
        var i = str.length - 1
        while (i >= 0 && (str[i].isDigit() || str[i] == '.')) i--
        if (i >= 0 && str[i] == '−' && (i == 0 || str[i - 1] in listOf('+', '−', '×', '÷'))) i--
        return str.substring(i + 1)
    }

    private fun formatResult(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigIntegerExact().toString()
        } else {
            stripped.toPlainString()
        }
    }

    private fun updateDisplay() {
        if (expression.isEmpty()) {
            tvExpression.text = ""
            tvResult.text = "0"
            return
        }

        tvExpression.text = expression

        if (!lastInputWasOperator) {
            val result = evaluate(expression.toString())
            if (result != null && formatResult(result) != expression.toString()) {
                tvResult.text = formatResult(result)
            } else if (result == null) {
                tvResult.text = ""
            } else {
                tvResult.text = ""
            }
        } else {
            tvResult.text = ""
        }
    }
}
