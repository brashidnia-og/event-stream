package com.bybob.blockchain.evenstream.stream.decoder

import com.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import com.bybob.blockchain.eventstream.adapter.json.decoder.Adapter as JsonDecoder
import com.bybob.blockchain.evenstream.stream.rpc.response.MessageType
import com.bybob.blockchain.evenstream.stream.rpc.response.RpcResponse
import org.json.JSONObject

class EmptyMessageDecoder(decoderEngine: DecoderEngine) : Decoder(decoderEngine) {
    override val priority: Int = 1

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: JsonDecoder<RpcResponse<JSONObject>> = decoderEngine.adapter(
        decoderEngine.parameterizedType(RpcResponse::class.java, JSONObject::class.java),
    )

    override fun decode(input: String): MessageType? {
        val result = adapter.fromJson(input)?.result ?: return null
        return if (result.isEmpty) MessageType.Empty else null
    }
}
