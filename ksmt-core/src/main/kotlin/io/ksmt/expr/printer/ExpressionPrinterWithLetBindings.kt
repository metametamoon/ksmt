package io.ksmt.expr.printer

import io.ksmt.expr.KApp
import io.ksmt.expr.KExpr

/**
 * Print expressions non-recursively.
 * Use let-bindings for common subexpressions to minimize result string.
 * */
class ExpressionPrinterWithLetBindings(private val minBindingLength: Int = DEFAULT_MIN_BINDING_LENGTH) {
    fun print(expr: KExpr<*>, out: StringBuilder) {
        val printers = hashMapOf<KExpr<*>, SingleExpressionPrinter>()
        val hasMultipleOccurrences = hashSetOf<KExpr<*>>()

        val exprStack = arrayListOf(expr)
        while (exprStack.isNotEmpty()) {
            val e = exprStack.removeLast()

            if (e in printers) {
                hasMultipleOccurrences.add(e)
                continue
            }

            val printer = SingleExpressionPrinter(e).also { e.print(it) }
            printers[e] = printer
            exprStack.addAll(printer.dependency)
        }

        val resolvedExpressionPrinters = hashMapOf<KExpr<*>, SingleExpressionPrinter>()
        val letBindings = arrayListOf<Pair<String, SingleExpressionPrinter>>()
        for (printer in orderDependenciesFirst(expr, printers)) {
            printer.resolveDependencies(resolvedExpressionPrinters, minBindingLength)
            val e = printer.expr
            val isConstant = e is KApp<*, *> && e.args.isEmpty()
            if (e in hasMultipleOccurrences && !isConstant && printer.length >= minBindingLength) {
                val bindingName = "e!${letBindings.size + 1}"
                letBindings += bindingName to printer
                resolvedExpressionPrinters[e] = SingleExpressionPrinter(e).apply {
                    append(bindingName)
                    resolveDependencies(resolvedExpressionPrinters, minBindingLength)
                }
            } else {
                resolvedExpressionPrinters[e] = printer
            }
        }

        val exprPrinter = resolvedExpressionPrinters.getValue(expr)
        printToBuffer(out, letBindings, exprPrinter)
    }

    private fun orderDependenciesFirst(
        root: KExpr<*>,
        printers: Map<KExpr<*>, SingleExpressionPrinter>
    ): List<SingleExpressionPrinter> {
        val ordered = arrayListOf<SingleExpressionPrinter>()
        val visited = hashSetOf<KExpr<*>>()
        val stack = arrayListOf(root to false)
        while (stack.isNotEmpty()) {
            val (e, dependenciesDone) = stack.removeLast()
            val printer = printers.getValue(e)
            if (dependenciesDone) {
                ordered += printer
                continue
            }
            if (!visited.add(e)) continue
            stack += e to true
            printer.dependency.asReversed().forEach { if (it !in visited) stack += it to false }
        }
        return ordered
    }

    private fun printToBuffer(
        out: StringBuilder,
        letBindings: List<Pair<String, SingleExpressionPrinter>>,
        exprPrinter: SingleExpressionPrinter
    ) {
        if (letBindings.isNotEmpty()) {
            out.appendLine("(let (")
            for ((name, bindingPrinter) in letBindings) {
                out.append('(')
                out.append(name)
                out.append(' ')
                bindingPrinter.printToBuffer(out)
                out.appendLine(')')
            }
            out.appendLine(')')
        }

        exprPrinter.printToBuffer(out)

        if (letBindings.isNotEmpty()) {
            out.append(')')
        }
    }

    private class SingleExpressionPrinter(val expr: KExpr<*>) : ExpressionPrinter {
        /**
         * Actual type: String | KExpr<*>
         * Type after dependency resolution: String | SingleExpressionPrinter
         * */
        private val parts = arrayListOf<Any>()
        var hasDependency = false
            private set

        val dependency: List<KExpr<*>>
            get() = if (hasDependency) parts.filterIsInstance<KExpr<*>>() else emptyList()

        override fun append(str: String) {
            parts.add(str)
        }

        override fun append(expr: KExpr<*>) {
            parts.add(expr)
            hasDependency = true
        }

        var length = 0
            private set

        fun resolveDependencies(printedExpressions: Map<KExpr<*>, SingleExpressionPrinter>, lengthCap: Int) {
            if (hasDependency) {
                val currentParts = parts.toList()
                parts.clear()

                for (part in currentParts) {
                    if (part !is KExpr<*>) {
                        parts.add(part)
                        continue
                    }

                    val printer = printedExpressions[part] ?: error("Printer failed")
                    check(!printer.hasDependency) { "Printer has unresolved parts" }

                    parts.add(printer)
                }

                hasDependency = false
            }

            length = parts.fold(0) { total, part ->
                val partLength = when (part) {
                    is String -> part.length
                    is SingleExpressionPrinter -> part.length
                    else -> error("Unexpected printer part")
                }
                (total + partLength).coerceAtMost(lengthCap)
            }
        }

        fun printToBuffer(buffer: StringBuilder) {
            val printerStack = arrayListOf<Any>(this)
            while (printerStack.isNotEmpty()) {
                when (val element = printerStack.removeLast()) {
                    is String -> buffer.append(element)
                    is SingleExpressionPrinter -> {
                        check(!element.hasDependency) { "Printer has unresolved parts" }
                        printerStack.addAll(element.parts.asReversed())
                    }
                    else -> error("Unexpected printer part")
                }
            }
        }
    }

    companion object {
        const val DEFAULT_MIN_BINDING_LENGTH = 100
    }
}
