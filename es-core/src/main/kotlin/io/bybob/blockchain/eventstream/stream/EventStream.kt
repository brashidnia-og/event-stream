package io.bybob.blockchain.eventstream.stream

import com.squareup.moshi.JsonDataException
import io.bybob.blockchain.eventstream.api.BlockSource
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import io.bybob.blockchain.eventstream.coroutines.DefaultDispatcherProvider
import io.bybob.blockchain.eventstream.coroutines.DispatcherProvider
import io.bybob.blockchain.eventstream.stream.clients.TendermintBlockFetcher
import io.bybob.blockchain.eventstream.stream.models.Block
import io.bybob.blockchain.eventstream.stream.models.BlockMeta
import io.bybob.blockchain.eventstream.stream.models.EncodedBlockchainEvent
import io.bybob.blockchain.eventstream.stream.models.StreamBlock
import io.bybob.blockchain.eventstream.stream.models.StreamBlockImpl
import io.bybob.blockchain.eventstream.stream.models.blockEvents
import io.bybob.blockchain.eventstream.stream.models.txEvents
import io.bybob.blockchain.eventstream.utils.backoff
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import mu.KotlinLogging
import io.bybob.blockchain.eventstream.BlockStreamOptions
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: WebSocketService,
    private val fetcher: TendermintBlockFetcher,
    private val decoder: DecoderEngine,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val checkpoint: Checkpoint = FileCheckpoint(),
    private val options: BlockStreamOptions = BlockStreamOptions(),
) : BlockSource<StreamBlockImpl> {
    companion object {
        /**
         * The default number of blocks that will be contained in a batch.
         */
        const val DEFAULT_BATCH_SIZE = 128

        /**
         * The maximum size of the query range for block heights allowed by the Tendermint API.
         * This means, for a given block height `H`, we can ask for blocks in the range [`H`, `H` + `TENDERMINT_MAX_QUERY_RANGE`].
         * Requesting a larger range will result in the API emitting an error.
         */
        const val TENDERMINT_MAX_QUERY_RANGE = 20
    }

    private val log = KotlinLogging.logger { }

    /**
     * A serializer function that converts a [StreamBlockImpl] instance to a JSON string.
     *
     * @return (StreamBlock) -> String
     */
    val serializer: (StreamBlock) -> String =
        { block: StreamBlock -> decoder.adapter(StreamBlock::class).toJson(block) }

    /***
     * Query a collections of blocks by their heights.
     *
     * Note: it is assumed the specified blocks already exists. No check will be performed to verify existence!
     *
     * @param blockHeights The heights of the blocks to query, along with optional metadata to attach to the fetched
     *  block data.
     * @return A Flow of found historical blocks along with events associated with each block, if any.
     */
    private suspend fun queryBlocks(blockHeights: List<Long>): Flow<StreamBlock> =
        fetcher.getBlocks(blockHeights).map { it.toStreamBlock() }

    fun streamLiveBlocks(): Flow<StreamBlock> {
        return streamLiveMetaBlocks()
            .toLiveStream()
    }

    suspend fun streamHistoricalBlocks(startingHeight: Long): Flow<StreamBlock> {
        val endingHeight = getEndingHeight() ?: error("Could not find ending height")
        return streamMetaBlocks().toHistoricalStream(startingHeight, endingHeight)
    }

    suspend fun streamHistoricalBlocks(startingHeight: Long, endingHeight: Long): Flow<StreamBlock> {
        return streamMetaBlocks()
            .toHistoricalStream(startingHeight, endingHeight)
    }

    fun streamLiveMetaBlocks(): Flow<Block> {
        return LiveMetaDataStream(eventStreamService, decoder).streamBlocks()
    }

    fun streamMetaBlocks(): Flow<BlockMeta> {
        return MetadataStream(options, fetcher).streamBlocks()
    }

    private suspend fun <T, R> Flow<T>.doFlatmap(transform: suspend (value: T) -> Flow<R>): Flow<R> {
        return if (options.ordered) {
            flatMapConcat { transform(it) }
        } else {
            flatMapMerge(options.concurrency) { transform(it) }
        }
    }

    private fun Flow<StreamBlock>.filterNonEmptyIfSet(): Flow<StreamBlock> =
        filter { !(options.skipEmptyBlocks && it.isEmpty()) }

    private fun Flow<StreamBlock>.filterByEvents(): Flow<StreamBlock> =
        filter { keepBlock(it.txEvents + it.blockEvents) }

    private fun <T : EncodedBlockchainEvent> keepBlock(events: List<T>): Boolean {
        if (options.txEvents.isEmpty() && options.blockEvents.isEmpty()) {
            return true
        }

        if (options.txEvents.isNotEmpty() && events.any { it.eventType in options.txEvents }) {
            return true
        }

        if (options.blockEvents.isNotEmpty() && events.any { it.eventType in options.blockEvents }) {
            return true
        }

        return false
    }

    private suspend fun Flow<BlockMeta>.toHistoricalStream(startingHeight: Long, endingHeight: Long): Flow<StreamBlock> =
        (startingHeight..endingHeight)
            .chunked(options.batchSize)
            .asFlow()
            .doFlatmap {
                queryBlocks(it).map { b: StreamBlock ->
                    object : StreamBlock by b {
                        override val historical: Boolean = true
                    }
                }
            }
            .filterNonEmptyIfSet()
            .filterByEvents()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun Flow<Block>.toLiveStream(): Flow<StreamBlock> {
        return channelFlow {
            this@toLiveStream
                .flowOn(dispatchers.io())
                .onStart { log.info("live::starting") }
                .mapNotNull { block: Block ->
                    fetcher.getBlock(block.header?.height!!).toStreamBlock().also {
                        log.debug("live::got block #${it.height}")
                    }
                }.onCompletion {
                    log.info("live::stopping event stream")
                    eventStreamService.stop()
                }.retryWhen { cause: Throwable, attempt: Long ->
                    log.warn("live::error; recovering Flow (attempt ${attempt + 1})")
                    when (cause) {
                        is JsonDataException -> {
                            log.error("streamLiveBlocks::parse error, skipping: $cause")
                            true
                        }
                        else -> false
                    }
                }
                .collect { this@channelFlow.send(it) }
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
     * Constructs a Flow of live and historical blocks, plus associated event data.
     *
     * If a starting height is provided, historical blocks will be included in the Flow from the starting height, up
     * to the latest block height determined at the start of the collection of the Flow.
     *
     * @return A Flow of live and historical blocks, plus associated event data.
     */
    override fun streamBlocks(): Flow<StreamBlock> = flow {
        val startingHeight: Long? = options.fromHeight
        emitAll(
            if (startingHeight != null) {
                log.info("Listening for live and historical blocks from height $startingHeight")
                merge(
                    streamHistoricalBlocks(startingHeight),
                    streamLiveBlocks().filterByEvents(),
                )
            } else {
                log.info("Listening for live blocks only")
                streamLiveBlocks().filterByEvents()
            },
        )
    }.cancellable().retryWhen { cause: Throwable, attempt: Long ->
        log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})")
        when (cause) {
            is EOFException, is CompletionException, is ConnectException, is SocketTimeoutException, is SocketException -> {
                val duration = backoff(attempt, jitter = false)
                log.error("Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                delay(duration)
                true
            }
            else -> false
        }
    }

    /**
     * @return A Flow of live and historical blocks, plus associated event data.
     */
    override suspend fun streamBlocks(from: Long?, toInclusive: Long?): Flow<StreamBlock> = channelFlow {
        val liveChannel = Channel<StreamBlock>(720)
        val liveJob = async {
            streamLiveBlocks()
                .buffer()
                .onCompletion { liveChannel.close(it) }
                .collect { liveChannel.send(it) }
        }

        val currentHeight = fetcher.getCurrentHeight()!!
        val needHistory = from != null && from <= currentHeight
        val needLive = toInclusive == null || toInclusive > currentHeight
        if (!needLive) {
            liveJob.cancel()
            log.trace("streamblocks::live cancelled: not needed")
        }

        val historyChannel = Channel<StreamBlock>()
        val historyJob = async(start = CoroutineStart.LAZY) {
            val calculatedFrom = checkpoint.lastCheckpoint() ?: (from ?: currentHeight)
            val calculatedTo = toInclusive ?: currentHeight

            log.info("hist::calculated-from:$calculatedFrom calculated-to:$calculatedTo need-history:$needHistory need-live:$needLive")
            streamHistoricalBlocks(calculatedFrom, calculatedTo)
                .buffer()
                .onCompletion { historyChannel.close(it) }
                .collect { historyChannel.send(it) }
        }

        if (needHistory) {
            historyJob.start()
            historyChannel.consumeAsFlow().collect { send(it) }
        }

        if (needLive) {
            // Make sure we pull anything between the last history and the first live
            // TODO
            // val liveStart = liveChannel.consumeAsFlow().peek().height
            liveChannel.consumeAsFlow().collect { send(it) }
        }
    }
        .buffer()
        .onEach {
            if (it.height!! % checkpoint.checkEvery == 0L) {
                checkpoint.checkpoint(it.height!!)
            }
        }
        .retryWhen { cause: Throwable, attempt: Long ->
            log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})", cause)
            when (cause) {
                is EOFException,
                is CompletionException,
                is ConnectException,
                is SocketTimeoutException,
                is SocketException,
                -> {
                    val duration = backoff(attempt, jitter = false)
                    log.error("streamblocks::Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                    delay(duration)
                    true
                }
                else -> {
                    // temporary need better exit conditions
                    log.error("unexpected error:  $cause")
                    throw error(cause)
                }
            }
        }
}
