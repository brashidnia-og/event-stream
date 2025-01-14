package io.bybob.blockchain.eventstream.stream.flows

import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.retry.BackoffStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import io.bybob.blockchain.eventstream.common.flows.contiguous
import io.bybob.blockchain.eventstream.decoder.DecoderAdapter
import io.bybob.blockchain.eventstream.defaultBackoffStrategy
import io.bybob.blockchain.eventstream.defaultLifecycle
import io.bybob.blockchain.eventstream.defaultWebSocketChannel
import io.bybob.blockchain.eventstream.net.NetAdapter
import io.bybob.blockchain.eventstream.stream.WebSocketChannel
import io.bybob.blockchain.eventstream.stream.WebSocketService
import io.bybob.blockchain.eventstream.stream.models.BlockHeader
import io.bybob.blockchain.eventstream.stream.models.BlockMeta
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.stream.withLifecycle
import kotlin.time.Duration

/**
 * Convert a [Flow] of type [MessageType.NewBlockHeader] into a flow of [BlockHeader]
 */
fun Flow<MessageType.NewBlockHeader>.mapLiveBlockHeader(): Flow<BlockHeader> = map { it.header.data.value!!.header!! }

/**
 * Create a [Flow] of historical [BlockMeta] from a node.
 *
 * Convenience wrapper around [nodeEventStream] and [mapLiveBlockHeader]
 *
 * @param netAdapter The [NetAdapter] to use to connect to the node.
 * @param decoderAdapter The [DecoderAdapter] to use to convert from json.
 * @param throttle The web socket throttle duration.
 * @param lifecycle The [LifecycleRegistry] instance used to manage startup and shutdown.
 * @param channel The [WebSocketChannel] used to receive incoming websocket events.
 * @param wss The [WebSocketService] used to manage the channel.
 */
fun wsBlockHeaderFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    backoffStrategy: BackoffStrategy = defaultBackoffStrategy(),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle, backoffStrategy),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
    currentHeight: Long? = null,
): Flow<BlockHeader> {
    val fetcher: suspend (List<Long>) -> Flow<BlockHeader> = {
        it.chunked(20).flatMap { range ->
            netAdapter.rpcAdapter.getBlocksMeta(range.first(), range.last()).orEmpty().sortedBy { it.header!!.height }
        }.asFlow().mapHistoricalHeaderData()
    }

    return nodeEventStream<MessageType.NewBlockHeader>(netAdapter, decoderAdapter, throttle, lifecycle, backoffStrategy, channel, wss)
        .mapLiveBlockHeader()
        .contiguous(fallback = fetcher, current = currentHeight) { it.height }
}
