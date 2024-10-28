package io.bybob.blockchain.eventstream.stream.models

data class TxData(
    val txHash: String?,
    val fee: InnerCoin?,
    val note: String?,
)
