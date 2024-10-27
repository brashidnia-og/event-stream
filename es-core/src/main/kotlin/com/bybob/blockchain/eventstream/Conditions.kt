package com.bybob.blockchain.eventstream

inline fun <reified T : Any> requireType(item: Any, block: () -> String): T {
    require(item is T) { block() }
    return item
}
