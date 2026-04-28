package sc.pirate.app.ui

fun shortAddress(address: String): String {
    if (address.length <= 12) return address
    return "${address.take(6)}...${address.takeLast(4)}"
}
