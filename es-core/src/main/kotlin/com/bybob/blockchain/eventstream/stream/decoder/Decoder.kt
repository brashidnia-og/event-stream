package com.bybob.blockchain.eventstream.stream.decoder

import com.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import com.bybob.blockchain.eventstream.stream.rpc.response.MessageType

sealed class Decoder(val decoder: DecoderEngine) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}
