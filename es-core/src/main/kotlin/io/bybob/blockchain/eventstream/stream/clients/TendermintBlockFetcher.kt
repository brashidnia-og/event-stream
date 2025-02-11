package io.bybob.blockchain.eventstream.stream.clients

import io.bybob.blockchain.eventstream.stream.models.BlockMeta
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class TendermintBlockFetcher(
    private val tendermintServiceClient: TendermintServiceClient,
) : BlockFetcher {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>? {
        return tendermintServiceClient.blockchain(min, max).result?.blockMetas
    }

    override suspend fun getCurrentHeight(): Long? {
        return tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight
            ?: throw BlockFetchException("failed to fetch current block height")
    }

    override suspend fun getInitialHeight(): Long {
        return tendermintServiceClient.genesis().result.genesis.initialHeight
    }

    override suspend fun getBlock(height: Long): BlockData {
        log.trace("getBlock($height)")
        val block = tendermintServiceClient.block(height).result?.block
            ?: throw BlockFetchException("failed to fetch height:$height")
        log.trace("getBlock($height) complete")

        log.trace("get block result($height)")
        val blockResult = tendermintServiceClient.blockResults(height).result
        log.trace("get block result($height) complete")
        return BlockData(block, blockResult)
    }
    override suspend fun getBlockResults(height: Long): BlockResultsResponse? {
        return tendermintServiceClient.blockResults(height)
    }

    @OptIn(FlowPreview::class)
    override suspend fun getBlocks(heights: List<Long>, concurrency: Int, context: CoroutineContext): Flow<BlockData> =
        withContext(context) {
            heights.chunked(concurrency).asFlow().transform { chunkOfHeights: List<Long> ->
                emitAll(
                    coroutineScope {
                        // Concurrently process <concurrency> blocks at a time:
                        chunkOfHeights.map { height -> async { getBlock(height) } }.awaitAll()
                    }.asFlow(),
                )
            }
        }
}
