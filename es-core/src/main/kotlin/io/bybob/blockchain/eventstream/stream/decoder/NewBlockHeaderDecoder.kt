package io.bybob.blockchain.eventstream.stream.decoder

import io.bybob.blockchain.eventstream.adapter.json.decoder.Adapter
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import io.bybob.blockchain.eventstream.stream.models.NewBlockHeaderResult
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.stream.rpc.response.RpcResponse

class NewBlockHeaderDecoder(decoderEngine: DecoderEngine) : Decoder(decoderEngine) {
    override val priority: Int = 100

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: Adapter<RpcResponse<NewBlockHeaderResult>> = decoderEngine.adapter(
        decoderEngine.parameterizedType(RpcResponse::class.java, NewBlockHeaderResult::class.java),
    )

    override fun decode(input: String): MessageType.NewBlockHeader? {
        return adapter.fromJson(input)?.let { it.result?.let { block -> MessageType.NewBlockHeader(block) } }
    }
}
