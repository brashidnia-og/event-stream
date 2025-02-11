package io.bybob.blockchain.eventstream.stream

import io.bybob.blockchain.eventstream.stream.models.BlockMeta
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import io.bybob.blockchain.eventstream.BlockStreamOptions
import io.bybob.blockchain.eventstream.stream.clients.TendermintBlockFetcher
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class MetadataStream(
    val options: BlockStreamOptions,
    val fetcher: TendermintBlockFetcher,
) {

    private val log = KotlinLogging.logger { }

    fun streamBlocks(): Flow<BlockMeta> {
        return queryMetadata()
    }

    private fun queryMetadata() = flow {
        log.info("metadata::streaming blocks from $options.fromHeight to $options.toHeight")
        log.info("metadata::batch size = ${options.batchSize}")

        // We're only allowed to query  a block range of (highBlockHeight - lowBlockHeight) = `TENDERMINT_MAX_QUERY_RANGE`
        // max block heights in a single request. If `Options.batchSize` is greater than this value, then we need to
        // make N calls to tendermint to the Tendermint API to have enough blocks to meet batchSize.
        val limit1 = EventStream.TENDERMINT_MAX_QUERY_RANGE.toDouble()
        val limit2 = options.batchSize.toDouble()
        val numChunks: Int = floor(max(limit1, limit2) / min(limit1, limit2)).toInt()
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        val startHeight: Long = getStartingHeight() ?: run {
            log.warn("No starting height provided; defaulting to 0")
            0
        }

        emitAll(getBlockHeightQueryRanges(startHeight, endHeight).chunked(numChunks).asFlow())
    }.flatMapConcat { heightPairChunk: List<Pair<Long, Long>> ->
        val availableBlocks: List<BlockMeta> = coroutineScope {
            heightPairChunk.map { (minHeight, maxHeight) -> async { getMetadataInRange(minHeight, maxHeight) } }
                .awaitAll().flatten()
        }
        log.info("metadata::${availableBlocks.size} block(s) in [${heightPairChunk.minOf { it.first }}..${heightPairChunk.maxOf { it.second }}]")
        availableBlocks.asFlow()
    }

    /**
     * Returns a sequence of block height pairs [[low, high]], representing a range to query when searching for blocks.
     */
    fun getBlockHeightQueryRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
        if (minHeight > maxHeight) {
            return emptySequence()
        }
        val step = EventStream.TENDERMINT_MAX_QUERY_RANGE
        return sequence {
            var i = minHeight
            var j = i + step - 1
            while (j <= maxHeight) {
                yield(Pair(i, j))
                i = j + 1
                j = i + step - 1
            }
            // If there's a gap between the last range and `maxHeight`, yield one last pair to fill it:
            if (i <= maxHeight) {
                yield(Pair(i, maxHeight))
            }
        }
    }

    /**
     * Computes and returns the ending height (if it can be determined) tobe used when streaming historical blocks.
     *
     * @return Long? The ending block height to use, if it exists.
     */
    private suspend fun getEndingHeight(): Long? =
        options.toHeight ?: fetcher.getCurrentHeight()

    /**
     * Computes and returns the starting height (if it can be determined) to be used when streaming historical blocks.
     *
     * @return Long? The starting block height to use, if it exists.
     */
    private fun getStartingHeight(): Long? = options.fromHeight

    /**
     * Returns the metadata of all existing blocks in a height range [[low, high]], subject to certain conditions.
     *
     * - If [Options.skipIfEmpty] is true, only blocks which contain 1 or more transactions will be returned.
     *
     * @return A list of block metadata
     */
    private suspend fun getMetadataInRange(minHeight: Long, maxHeight: Long): List<BlockMeta> {
        if (minHeight > maxHeight) {
            return emptyList()
        }

        // invariant
        assert((maxHeight - minHeight) <= EventStream.TENDERMINT_MAX_QUERY_RANGE) {
            "Difference between (minHeight, maxHeight) can be at maximum ${EventStream.TENDERMINT_MAX_QUERY_RANGE}"
        }

        val blocks = fetcher.getBlocksMeta(minHeight, maxHeight).let {
            if (options.skipEmptyBlocks) {
                it?.filter { it.numTxs ?: 0 > 0 }
            } else {
                it
            }
        }?.mapNotNull { it } ?: emptyList()

        return blocks.sortedBy { it.header?.height }
    }
}
