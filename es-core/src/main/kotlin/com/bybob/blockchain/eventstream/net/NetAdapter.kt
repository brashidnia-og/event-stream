package com.bybob.blockchain.eventstream.net

import com.bybob.blockchain.evenstream.WsAdapter
import com.bybob.blockchain.evenstream.stream.clients.BlockFetcher

/**
 * Create a generic [NetAdapter] to interface with the web socket channels.
 *
 * @param wsAdapter The [WsAdapter] used to interface with [com.tinder.scarlet.Scarlet]
 * @param rpcAdapter The [BlockFetcher] used to make rpc calls to the node.
 * @return The [NetAdapter] instance.
 */
fun netAdapter(wsAdapter: WsAdapter, rpcAdapter: BlockFetcher, shutdown: () -> Unit = {}): NetAdapter {
    return object : NetAdapter {
        override val wsAdapter: WsAdapter = wsAdapter
        override val rpcAdapter: BlockFetcher = rpcAdapter

        override fun shutdown() = shutdown()
    }
}

/**
 * Provide a common interface for an http framework to interface with the websocket and block fetcher functions.
 */
interface NetAdapter {
    val wsAdapter: WsAdapter
    val rpcAdapter: BlockFetcher

    fun shutdown()
}
