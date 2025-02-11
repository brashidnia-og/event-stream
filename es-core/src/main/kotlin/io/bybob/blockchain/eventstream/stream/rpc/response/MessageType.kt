package io.bybob.blockchain.eventstream.stream.rpc.response

import io.bybob.blockchain.eventstream.stream.decoder.Decoder as TDecoder
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderDataException
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import io.bybob.blockchain.eventstream.stream.NewBlockResult
import io.bybob.blockchain.eventstream.stream.models.NewBlockHeaderResult
import mu.KotlinLogging
import kotlin.reflect.full.primaryConstructor

/**
 * A sealed class family which defines the results of decoding a Tendermint websocket/RPC API response.
 */
sealed interface MessageType {
    /**
     * Decode the supplied input into one of the variants of [MessageType].
     */
    class Decoder(private val engine: DecoderEngine) {
        private val log = KotlinLogging.logger {}

        // Decoders are attempted according to their assigned priority in descending order:
        private val decoders =
            TDecoder::class.sealedSubclasses.mapNotNull { clazz -> clazz.primaryConstructor?.call(engine) }
                .sortedByDescending { it.priority }

        fun decode(input: String): MessageType {
            for (decoder in decoders) {
                try {
                    val message = decoder.decode(input)
                    if (message != null) {
                        return message
                    }
                } catch (e: DecoderDataException) {
                    log.trace("failed to decode as ${decoder.javaClass.simpleName}: ${e.message}")
                }
            }
            return Unknown(input)
        }
    }

    /**
     * An unknown message was received.
     */
    data class Unknown(val type: String) : MessageType

    /**
     * An empty message was received.
     *
     * An example of an empty message:
     *
     * ```
     * {
     *   "jsonrpc": "2.0",
     *   "id": "0",
     *   "result": {}
     * }
     * ```
     */
    object Empty : MessageType

    /**
     * An error was received from the RPC API.
     */
    data class Error(val error: RpcError) : MessageType

    /**
     * A panic message was received from the RPC API.
     */
    data class Panic(val error: RpcError) : MessageType

    /**
     * A message indicating a new block was created.
     */
    data class NewBlock(val block: NewBlockResult) : MessageType

    /**
     *
     */
    data class NewBlockHeader(val header: NewBlockHeaderResult) : MessageType
}
