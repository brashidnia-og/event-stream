package io.bybob.blockchain.eventstream.stream.flows

import io.bybob.blockchain.eventstream.net.NetAdapter
import io.bybob.blockchain.eventstream.stream.EventStream
import io.bybob.blockchain.eventstream.stream.models.BlockHeader
import io.bybob.blockchain.eventstream.stream.models.BlockMeta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Convert a [Flow] of [BlockMeta] into a [Flow] of [BlockHeader].
 */
fun Flow<BlockMeta>.mapHistoricalHeaderData(): Flow<BlockHeader> = map { it.header!! }

/**
 * Create a [Flow] of historical [BlockHeader] from a node.
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @param from The `from` height to begin the stream with.
 * @param to The `to` height to fetch until. If omitted, use the current height.
 * @param concurrency The coroutine concurrency setting for async parallel fetches.
 * @param context The coroutine context to execute the async parallel fetches.
 * @return The [Flow] of [BlockHeader]
 */
fun historicalBlockHeaderFlow(
    netAdapter: NetAdapter,
    from: Long = 1,
    to: Long? = null,
    concurrency: Int = DEFAULT_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
    currentHeight: Long? = null,
): Flow<BlockHeader> =
    historicalBlockMetaFlow(netAdapter, from, to, concurrency, context, currentHeight = currentHeight).mapHistoricalHeaderData()

/**
 * Create a [Flow] of historical [BlockMeta] from a node.
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @param from The `from` height to begin the stream with.
 * @param to The `to` height to fetch until. If omitted, use the current height.
 * @param concurrency The coroutine concurrency setting for async parallel fetches.
 * @param context The coroutine context to execute the async parallel fetches.
 * @return The [Flow] of [BlockMeta]
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun historicalBlockMetaFlow(
    netAdapter: NetAdapter,
    from: Long = 1,
    to: Long? = null,
    concurrency: Int = DEFAULT_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
    currentHeight: Long? = null,
): Flow<BlockMeta> = flow {
    suspend fun currentHeight() =
        netAdapter.rpcAdapter.getCurrentHeight() ?: throw RuntimeException("cannot fetch current height")

    val realTo = currentHeight ?: (to ?: currentHeight())
    require(from <= realTo) { "from:$from must be less than to:$realTo" }
    // Max out efficiency of downstream processing by creating ranges that can be shoved through the fetcher properly
    // that also leverage the concurrency
    val heightRangeSize = concurrency.times(EventStream.TENDERMINT_MAX_QUERY_RANGE).toLong()
    // Chunk up the ranges by the concurrency amount
    (from..realTo step heightRangeSize)
        // Convert to sequence to avoid holding too many values in memory
        .asSequence()
        .map { rangeStart -> rangeStart..(rangeStart + heightRangeSize - 1).coerceAtMost(realTo) }
        .map { range -> range.toList() }
        .forEach { heights -> emitAll(heights.toBlockMeta(netAdapter, concurrency, context)) }
}

/**
 * Convert a list of heights into a [Flow] of [BlockMeta].
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @param concurrency The coroutine concurrency setting for async parallel fetches.
 * @param context The coroutine context to execute the async parallel fetches.
 * @return The [Flow] of [BlockMeta]
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal fun List<Long>.toBlockMeta(
    netAdapter: NetAdapter,
    concurrency: Int = DEFAULT_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<BlockMeta> {
    val fetcher = netAdapter.rpcAdapter
    val log = KotlinLogging.logger {}
    return channelFlow {
        chunked(EventStream.TENDERMINT_MAX_QUERY_RANGE).map { LongRange(it.first(), it.last()) }
            .chunked(concurrency).map { chunks ->
                withContext(context) {
                    log.debug { "processing chunks:$chunks" }
                    chunks.toList().map {
                        async {
                            fetcher.getBlocksMeta(it.first, it.last)
                                ?.sortedBy { it.header?.height }
                                ?: throw RuntimeException("failed to fetch for range:[${it.first} .. ${it.last}]")
                        }
                    }.awaitAll().flatten()
                }.forEach { send(it) }
            }
    }
}
