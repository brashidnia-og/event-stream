package io.bybob.blockchain.eventstream.stream.decoder

import io.bybob.blockchain.eventstream.adapter.json.decoder.Adapter
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import io.bybob.blockchain.eventstream.stream.NewBlockResult
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.stream.rpc.response.RpcResponse

class NewBlockDecoder(decoderEngine: DecoderEngine) : Decoder(decoderEngine) {
    override val priority: Int = 100

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: Adapter<RpcResponse<NewBlockResult>> = decoderEngine.adapter(
        decoderEngine.parameterizedType(RpcResponse::class.java, NewBlockResult::class.java),
    )

    override fun decode(input: String): MessageType.NewBlock? {
        return adapter.fromJson(input)?.let { it.result?.let { block -> MessageType.NewBlock(block) } }
    }
}
