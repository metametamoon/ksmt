package io.ksmt.expr.printer

import io.ksmt.KContext
import io.ksmt.utils.getValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LetBindingSelectionTest {
    @Test
    fun bindsOnlyLongSharedExpressions() = with(KContext()) {
        val x by mkBvSort(256u)
        val y by mkBvSort(256u)
        val small = mkBvAddExpr(x, y)
        val big = (1..8).fold(small) { acc, i -> mkBvMulExpr(acc, mkBvAddExpr(x, mkBv(i, 256u))) }
        val root = mkAnd(small eq big, big eq mkBvAddExpr(small, big))
        val printed = root.toString()
        assertEquals(1, Regex("e!\\d+").findAll(printed).map { it.value }.toSet().size)
        assertFalse(printed.contains("(e!1 (bvadd x y))"))
        assertTrue(printed.startsWith("(let (\n(e!1 (bvmul"))
    }

    @Test
    fun numbersBindingsInDependencyOrder() = with(KContext(printerParams = PrinterParams(minLetBindingLength = 1))) {
        val x by mkBvSort(256u)
        val inner = mkBvAddExpr(x, x)
        val outer = mkBvMulExpr(inner, inner)
        val printed = mkBvSubExpr(outer, outer).toString()
        assertEquals("(let (\n(e!1 (bvadd x x))\n(e!2 (bvmul e!1 e!1))\n)\n(bvadd e!2 (bvneg e!2)))", printed)
    }
}
