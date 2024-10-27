package com.bybob.blockchain.evenstream.stream.decoder

import com.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import com.bybob.blockchain.evenstream.stream.rpc.response.MessageType

sealed class Decoder(val decoder: DecoderEngine) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}
