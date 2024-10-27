package io.bybob.blockchain.eventstream.stream.decoder

import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType

sealed class Decoder(val decoder: DecoderEngine) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}
