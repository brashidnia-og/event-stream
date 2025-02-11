package io.bybob.blockchain.eventstream.base.utils

import com.squareup.moshi.Moshi
import io.bybob.blockchain.eventstream.base.mocks.ServiceMocker
import io.bybob.blockchain.eventstream.stream.models.ABCIInfoResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import io.bybob.blockchain.eventstream.stream.models.BlockchainResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.bybob.blockchain.eventstream.BlockStreamOptions
import io.bybob.blockchain.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.bybob.blockchain.eventstream.coroutines.DispatcherProvider
import io.bybob.blockchain.eventstream.base.mocks.MockEventStreamService
import io.bybob.blockchain.eventstream.base.mocks.MockTendermintServiceClient
import io.bybob.blockchain.eventstream.stream.EventStream
import io.bybob.blockchain.eventstream.stream.WebSocketService
import io.bybob.blockchain.eventstream.stream.clients.TendermintBlockFetcher
import io.bybob.blockchain.eventstream.stream.clients.TendermintServiceClient

@ExperimentalCoroutinesApi
object Builders {

    /**
     * Create a mock of the Tendermint service API exposed on Provenance.
     */
    fun tendermintService(): ServiceMocker.Builder = ServiceMocker.Builder()
        .doFor("abciInfo") {
            Defaults.templates.readAs(
                ABCIInfoResponse::class.java,
                "abci_info/success.json",
                mapOf("last_block_height" to MAX_HISTORICAL_BLOCK_HEIGHT),
            )
        }
        .doFor("block") { Defaults.templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
        .doFor("blockResults") {
            Defaults.templates.readAs(
                BlockResultsResponse::class.java,
                "block_results/${it[0]}.json",
            )
        }
        .doFor("blockchain") {
            Defaults.templates.readAs(
                BlockchainResponse::class.java,
                "blockchain/${it[0]}-${it[1]}.json",
            )
        }

    /**
     * Create a mock of the Tendermint RPC event stream exposed on Provenance.
     */
    fun eventStreamService(includeLiveBlocks: Boolean = true): MockEventStreamService.Builder {
        val serviceBuilder = MockEventStreamService.Builder()
        if (includeLiveBlocks) {
            for (liveBlockResponse in Defaults.templates.readAll("live")) {
                serviceBuilder.response(liveBlockResponse)
            }
        }
        return serviceBuilder
    }

    /**
     * Create a mock of the Provenance block event stream.
     */
    data class EventStreamBuilder(val builders: Builders) {
        var dispatchers: DispatcherProvider? = null
        var eventStreamService: WebSocketService? = null
        var tendermintServiceClient: TendermintServiceClient? = null
        var moshi: Moshi? = null
        var options: BlockStreamOptions = BlockStreamOptions()
        var includeLiveBlocks: Boolean = true

        fun <T : WebSocketService> eventStreamService(value: T) = apply { eventStreamService = value }
        fun <T : TendermintServiceClient> tendermintService(value: T) = apply { tendermintServiceClient = value }
        fun moshi(value: Moshi) = apply { moshi = value }
        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }
        fun options(value: BlockStreamOptions) = apply { options = value }
        fun includeLiveBlocks(value: Boolean) = apply { includeLiveBlocks = value }

        // shortcuts for options:
        fun batchSize(value: Int) = apply { options = options.copy(batchSize = value) }
        fun fromHeight(value: Long) = apply { options = options.copy(fromHeight = value) }
        fun toHeight(value: Long) = apply { options = options.copy(toHeight = value) }
        fun skipEmptyBlocks(value: Boolean) = apply { options = options.copy(skipEmptyBlocks = value) }
        fun matchBlockEvents(events: Set<String>) = apply { options = options.copy(blockEvents = events) }
        fun matchTxEvents(events: Set<String>) = apply { options = options.copy(txEvents = events) }

        suspend fun build(): EventStream {
            val dispatchers = dispatchers ?: error("dispatchers must be provided")
            return EventStream(
                eventStreamService = eventStreamService
                    ?: eventStreamService(includeLiveBlocks = includeLiveBlocks)
                        .dispatchers(dispatchers)
                        .build(),
                fetcher = TendermintBlockFetcher(
                    tendermintServiceClient
                        ?: tendermintService().build(MockTendermintServiceClient::class.java),
                ),
                decoder = if (moshi != null) MoshiDecoderEngine(moshi!!) else Defaults.decoderEngine(),
                dispatchers = dispatchers,
                options = options,
            )
        }
    }

    fun eventStream(): EventStreamBuilder = EventStreamBuilder(this)
}
