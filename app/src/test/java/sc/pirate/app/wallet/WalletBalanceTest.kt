package sc.pirate.app.wallet

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        assertFailsWith<IllegalArgumentException> { nativeAmountToAtomic("0") }
        assertFailsWith<IllegalArgumentException> { nativeAmountToAtomic("-1") }
        assertFailsWith<IllegalArgumentException> { nativeAmountToAtomic("0.0000000000000000001") }
    }
}
