package io.bybob.blockchain.eventstream.stream.flows

import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.retry.BackoffStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.bybob.blockchain.eventstream.common.flows.contiguous
import io.bybob.blockchain.eventstream.decoder.DecoderAdapter
import io.bybob.blockchain.eventstream.defaultBackoffStrategy
import io.bybob.blockchain.eventstream.defaultLifecycle
import io.bybob.blockchain.eventstream.defaultWebSocketChannel
import io.bybob.blockchain.eventstream.net.NetAdapter
import io.bybob.blockchain.eventstream.stream.NewBlockResult
import io.bybob.blockchain.eventstream.stream.WebSocketChannel
import io.bybob.blockchain.eventstream.stream.WebSocketService
import io.bybob.blockchain.eventstream.stream.clients.BlockData
import io.bybob.blockchain.eventstream.stream.models.Block
import io.bybob.blockchain.eventstream.stream.models.BlockHeader
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.stream.withLifecycle
import kotlin.time.Duration

/**
 * Convert a [Flow] of type [MessageType.NewBlockHeader] into a flow of [BlockHeader]
 */
fun Flow<MessageType.NewBlock>.mapLiveBlockResult(): Flow<NewBlockResult> = map { it.block }

/**
 * Convert a [Flow] of type [MessageType.NewBlock] into a [Flow] of [Block].
 *
 * Mimic the behavior of the [LiveMetaDataStream] using [nodeEventStream] as a source.
 */
fun Flow<MessageType.NewBlock>.mapLiveBlock(): Flow<Block> =
    map { it.block.data.value.block }

/**
 * Create a [Flow] of live [BlockData] from a node.
 *
 * Convenience wrapper around [nodeEventStream] and [mapBlockData]
 *
 * @param netAdapter The [NetAdapter] to use to connect to the node.
 * @param decoderAdapter The [DecoderAdapter] to use to convert from json.
 * @param throttle The web socket throttle duration.
 * @param lifecycle The [LifecycleRegistry] instance used to manage startup and shutdown.
 * @param channel The [WebSocketChannel] used to receive incoming websocket events.
 * @param wss The [WebSocketService] used to manage the channel.
 */
fun wsBlockDataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    backoffStrategy: BackoffStrategy = defaultBackoffStrategy(),
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
    currentHeight: Long? = null,
): Flow<BlockData> {
    return nodeEventStream<MessageType.NewBlock>(netAdapter, decoderAdapter, throttle, lifecycle, backoffStrategy, channel, wss)
        .mapBlockData(netAdapter)
        .contiguous(fallback = netAdapter.rpcAdapter::getBlocks, current = currentHeight) { it.height }
}

/**
 * Convert a list of heights into a [Flow] of [BlockData].
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @return The [Flow] of [BlockData]
 */
fun Flow<MessageType.NewBlock>.mapBlockData(netAdapter: NetAdapter): Flow<BlockData> {
    val fetcher = netAdapter.rpcAdapter
    return map { fetcher.getBlock(it.block.data.value.block.header!!.height) }
}
