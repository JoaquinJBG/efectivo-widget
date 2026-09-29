package io.github.joaquinjbg.efectivo

import io.github.joaquinjbg.efectivo.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats spanish amounts`() {
        assertEquals("0,00 €", Money.format(0))
        assertEquals("2,50 €", Money.format(250))
        assertEquals("50,00 €", Money.format(5000))
        assertEquals("1.234,56 €", Money.format(123456))
        assertEquals("-12,05 €", Money.format(-1205))
    }

    @Test
    fun `short form drops zero decimals`() {
        assertEquals("2 €", Money.formatShort(200))
        assertEquals("2,50 €", Money.formatShort(250))
        assertEquals("50 €", Money.formatShort(5000))
    }

    @Test
    fun `parses keypad text`() {
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(1200L, Money.parse("12"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(5L, Money.parse("0,05"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
    }
}
