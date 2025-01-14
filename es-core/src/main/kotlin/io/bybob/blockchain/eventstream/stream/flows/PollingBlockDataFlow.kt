package io.bybob.blockchain.eventstream.stream.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import io.bybob.blockchain.eventstream.common.flows.pollingDataFlow
import io.bybob.blockchain.eventstream.net.NetAdapter
import io.bybob.blockchain.eventstream.stream.clients.BlockData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Create a polling [Flow] of [BlockData] from a node that looks "live".
 *
 * Avoids all the nonsense of websockets while achieving the same end result.
 *
 * @param netAdapter The [NetAdapter] to use to connect to the node.
 * @param from The height to start the flow of [BlockData] at.
 */
fun pollingBlockDataFlow(netAdapter: NetAdapter, pollInterval: Duration = 10.seconds, from: Long? = null): Flow<BlockData> =
    pollingDataFlow(
        { netAdapter.rpcAdapter.getCurrentHeight()!! },
        pollInterval,
        from,
        { historicalBlockDataFlow(netAdapter, it.first(), it.last()).toList() },
    )
