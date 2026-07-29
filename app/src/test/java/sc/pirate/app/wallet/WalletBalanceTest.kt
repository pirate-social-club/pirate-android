package sc.pirate.app.wallet

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WalletBalanceTest {
    @Test
    fun `formats wei without overstating precision`() {
        assertEquals("1.234567", formatNativeBalance(BigInteger("1234567890123456789")))
        assertEquals("0", formatNativeBalance(BigInteger.ZERO))
        assertEquals("0.000001", formatNativeBalance(BigInteger("1000000000000")))
    }

    @Test
    fun `maps common CAIP chains to native symbols`() {
        assertEquals("ETH", nativeSymbol("eip155:1"))
        assertEquals("ETH", nativeSymbol("eip155:8453"))
        assertEquals("POL", nativeSymbol("eip155:137"))
        assertEquals("BNB", nativeSymbol("eip155:56"))
        assertEquals("AVAX", nativeSymbol("eip155:43114"))
    }

    @Test
    fun `converts native amount to atomic units exactly`() {
        assertEquals(BigInteger("1000000000000000000"), nativeAmountToAtomic("1"))
        assertEquals(BigInteger.ONE, nativeAmountToAtomic("0.000000000000000001"))
        assertThrows(IllegalArgumentException::class.java) { nativeAmountToAtomic("0") }
        assertThrows(IllegalArgumentException::class.java) { nativeAmountToAtomic("-1") }
        assertThrows(IllegalArgumentException::class.java) { nativeAmountToAtomic("0.0000000000000000001") }
    }

    @Test
    fun `validates exact EVM address shape`() {
        assertEquals(true, isValidEvmAddress("0x000000000000000000000000000000000000dEaD"))
        assertEquals(false, isValidEvmAddress("000000000000000000000000000000000000dEaD"))
        assertEquals(false, isValidEvmAddress("0x000000000000000000000000000000000000xyz0"))
    }
}
