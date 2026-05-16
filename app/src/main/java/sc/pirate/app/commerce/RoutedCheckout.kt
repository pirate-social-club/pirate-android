package sc.pirate.app.commerce

import java.math.BigInteger
import sc.pirate.app.api.model.CommunityMoneyAssetRef
import sc.pirate.app.api.model.CommunityMoneyChainRef
import sc.pirate.app.api.model.CommunityPurchaseQuote
import sc.pirate.app.api.model.CommunityPurchaseQuoteRequest

private const val BASE_MAINNET_CHAIN_ID = 8453
private const val BASE_SEPOLIA_CHAIN_ID = 84532
private const val BASE_MAINNET_USDC = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"
private const val BASE_SEPOLIA_USDC = "0x036cbd53842c5426634e7929541ec2318f3dcf7e"

data class CheckoutRouteChain(
    val chainId: Int,
    val walletChainId: String,
    val displayName: String,
    val usdcTokenAddress: String,
)

data class StoryCheckoutTransferInput(
    val walletChainId: String,
    val tokenAddress: String,
    val recipientAddress: String,
    val amountAtomic: BigInteger,
)

fun buildStoryCheckoutQuoteRequest(
    listingId: String,
    selectedWalletChainId: String?,
): CommunityPurchaseQuoteRequest {
    val chain = resolveCheckoutRouteChain(selectedWalletChainId)
    return CommunityPurchaseQuoteRequest(
        listing = listingId,
        fundingAsset = CommunityMoneyAssetRef(
            assetSymbol = "USDC",
            chainNamespace = "eip155",
            chainId = chain.chainId,
            displayName = "USDC on ${chain.displayName}",
        ),
        sourceChain = CommunityMoneyChainRef(
            chainNamespace = "eip155",
            chainId = chain.chainId,
            displayName = chain.displayName,
        ),
        routeProvider = "pirate_checkout",
        clientEstimatedSlippageBps = 0,
        clientEstimatedHopCount = 1,
    )
}

fun resolveStoryCheckoutTransferInput(quote: CommunityPurchaseQuote): StoryCheckoutTransferInput {
    if (quote.routeProvider != "pirate_checkout") {
        error("This quote requires an unsupported checkout route.")
    }
    if (quote.fundingAsset?.assetSymbol != "USDC") {
        error("This quote requires USDC checkout.")
    }

    val chain = resolveCheckoutRouteChain(requireQuoteWalletChainId(quote))
    val recipientAddress = quote.fundingDestinationAddress
        ?.trim()
        ?.takeIf(::looksLikeEvmAddress)
        ?: error("This quote is missing its checkout destination.")
    if (quote.finalPriceCents <= 0) {
        error("This quote has an invalid checkout amount.")
    }

    return StoryCheckoutTransferInput(
        walletChainId = chain.walletChainId,
        tokenAddress = chain.usdcTokenAddress,
        recipientAddress = recipientAddress,
        amountAtomic = BigInteger.valueOf(quote.finalPriceCents.toLong()).multiply(BigInteger.valueOf(10_000L)),
    )
}

private fun requireQuoteWalletChainId(quote: CommunityPurchaseQuote): String {
    val sourceChain = quote.sourceChain ?: error("This quote requires an unsupported checkout chain.")
    if (sourceChain.chainNamespace != "eip155") {
        error("This quote requires an unsupported checkout chain.")
    }
    val chainId = sourceChain.chainId ?: error("This quote has an invalid checkout chain.")
    return "eip155:$chainId"
}

private fun resolveCheckoutRouteChain(selectedWalletChainId: String?): CheckoutRouteChain {
    val chainId = parseEip155ChainId(selectedWalletChainId) ?: BASE_MAINNET_CHAIN_ID
    return when (chainId) {
        BASE_MAINNET_CHAIN_ID -> CheckoutRouteChain(
            chainId = BASE_MAINNET_CHAIN_ID,
            walletChainId = "eip155:$BASE_MAINNET_CHAIN_ID",
            displayName = "Base Mainnet",
            usdcTokenAddress = BASE_MAINNET_USDC,
        )
        BASE_SEPOLIA_CHAIN_ID -> CheckoutRouteChain(
            chainId = BASE_SEPOLIA_CHAIN_ID,
            walletChainId = "eip155:$BASE_SEPOLIA_CHAIN_ID",
            displayName = "Base Sepolia",
            usdcTokenAddress = BASE_SEPOLIA_USDC,
        )
        else -> error("Unsupported checkout chain ($chainId).")
    }
}

private fun parseEip155ChainId(value: String?): Int? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val raw = trimmed.substringAfter("eip155:", trimmed)
    return raw.toIntOrNull()?.takeIf { it > 0 }
}

private fun looksLikeEvmAddress(value: String): Boolean =
    Regex("^0x[0-9a-fA-F]{40}$").matches(value)
