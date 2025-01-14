package io.bybob.blockchain.eventstream

import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEncodingException
import io.bybob.blockchain.eventstream.decoder.moshiDecoderAdapter
import io.bybob.blockchain.eventstream.stream.models.ABCIInfoResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResponse
import io.bybob.blockchain.eventstream.stream.models.Event
import io.bybob.blockchain.eventstream.stream.models.toDecodedMap
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import io.bybob.blockchain.eventstream.stream.models.BlockchainResponse
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.test.base.TestBase
import io.bybob.blockchain.eventstream.base.mocks.MockTendermintServiceClient
import io.bybob.blockchain.eventstream.base.mocks.ServiceMocker
import io.bybob.blockchain.eventstream.base.utils.EXPECTED_NONEMPTY_BLOCKS
import io.bybob.blockchain.eventstream.base.utils.EXPECTED_HISTORICAL_BLOCK_COUNT
import io.bybob.blockchain.eventstream.base.utils.MAX_HISTORICAL_BLOCK_HEIGHT
import io.bybob.blockchain.eventstream.base.utils.MIN_HISTORICAL_BLOCK_HEIGHT
import io.bybob.blockchain.eventstream.base.utils.Builders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import io.bybob.blockchain.eventstream.base.mocks.MockEventStreamService
import io.bybob.blockchain.eventstream.stream.clients.TendermintServiceClient
import io.bybob.blockchain.eventstream.stream.models.GenesisResponse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamTests : TestBase() {
    private val decoder = MessageType.Decoder(moshiDecoderAdapter().decoderEngine)

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @Nested
    @DisplayName("Tendermint RPC response message decoding")
    inner class RPCResponseDecoding {

        @Test
        fun testDecodeEmptyACKMessage() {
            val response: MessageType = decoder.decode(templates.read("rpc/responses/empty.json"))
            println(response)
            assert(response is MessageType.Empty)
        }

        @Test
        fun testDecodeUnknownMessage() {
            val response: MessageType = decoder.decode(templates.read("rpc/responses/unknown.json"))
            assert(response is MessageType.Unknown)
        }

        @Test
        fun testTryDecodeMalformedMessage() {
            assertThrows<DecoderEncodingException> {
                decoder.decode(templates.read("rpc/responses/malformed.json"))
            }
        }

        @Test
        fun testDecodeNewBlockMessage() {
            val response: MessageType = decoder.decode(templates.read("live/3126935.json"))
            assert(response is MessageType.NewBlock)
        }

        @Test
        fun testDecodeErrorUnwrappedMessage() {
            val response: MessageType = decoder.decode(templates.read("rpc/responses/error_unwrapped.json"))
            assert(response is MessageType.Error)
            assert((response as MessageType.Error).error.code == -1000)
            assert(response.error.text()?.contains("something bad happened") ?: false)
        }

        @Test
        fun testDecodeErrorWrappedMessage() {
            val response: MessageType = decoder.decode(templates.read("rpc/responses/error_wrapped.json"))
            assert(response is MessageType.Error)
            assert((response as MessageType.Error).error.code == -1000)
            assert(response.error.text()?.contains("something bad happened") ?: false)
        }

        @Test
        fun testDecodePanicMessage() {
            val response: MessageType = decoder.decode(templates.read("rpc/responses/panic.json"))
            assert(response is MessageType.Panic)
            assert((response as MessageType.Panic).error.text()?.contains("panic") ?: false)
        }
    }

    @Nested
    @DisplayName("Event attributes")
    inner class EventAttributes {

        @Test
        fun testDecodingIntoMap() {
            val recordAddrValue =
                "InJlY29yZDFxMm0zeGFneDc2dXl2ZzRrN3l2eGM3dWhudWdnOWc2bjBsY2Robm43YXM2YWQ4a3U4Z3ZmdXVnZjZ0aiI="
            val sessionAddrValue =
                "InNlc3Npb24xcXhtM3hhZ3g3NnV5dmc0azd5dnhjN3VobnVnMHpwdjl1cTNhdTMzMmsyNzY2NmplMGFxZ2o4Mmt3dWUi"
            val scopeAddrValue = "InNjb3BlMXF6bTN4YWd4NzZ1eXZnNGs3eXZ4Yzd1aG51Z3F6ZW1tbTci"
            val eventAttributes: List<Event> = listOf(
                Event(key = "cmVjb3JkX2FkZHI=", value = recordAddrValue, index = false),
                Event(key = "c2Vzc2lvbl9hZGRy", value = sessionAddrValue, index = false),
                Event(key = "c2NvcGVfYWRkcg==", value = scopeAddrValue, index = false),
            )
            val attributeMap = eventAttributes.toDecodedMap()
            assert("record_addr" in attributeMap && attributeMap["record_addr"] == recordAddrValue)
            assert("session_addr" in attributeMap && attributeMap["session_addr"] == sessionAddrValue)
            assert("scope_addr" in attributeMap && attributeMap["scope_addr"] == scopeAddrValue)
        }
    }

    @Nested
    @DisplayName("Tendermint API")
    inner class TendermintAPI {

        @Test
        fun testAbciInfoSerialization() {
            assert(templates.readAs(ABCIInfoResponse::class.java, "abci_info/success.json") != null)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testAbciInfoAPIResponse() {
            val expectBlockHeight = 9999L

            val tm = ServiceMocker.Builder()
                .doFor("abciInfo") {
                    templates.readAs(
                        ABCIInfoResponse::class.java,
                        "abci_info/success.json",
                        mapOf("last_block_height" to expectBlockHeight),
                    )
                }
                .build(MockTendermintServiceClient::class.java)

            dispatcherProvider.runBlockingTest {
                assert(tm.abciInfo().result?.response?.lastBlockHeight == expectBlockHeight)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testGenesisResponse() {
            val expectedInitialHeight = 10000L

            val tm = ServiceMocker.Builder()
                .doFor("genesis") {
                    templates.readAs(
                        GenesisResponse::class.java,
                        "genesis/success.json",
                        mapOf("initial_height" to expectedInitialHeight),
                    )
                }
                .build(MockTendermintServiceClient::class.java)

            dispatcherProvider.runBlockingTest {
                val initialHeight = tm.genesis().result.genesis.initialHeight
                assert(initialHeight == expectedInitialHeight) { "expected: $expectedInitialHeight received: $initialHeight" }
            }
        }

        // duped
        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testBlockResponse() {
            val tendermint: TendermintServiceClient = ServiceMocker.Builder()
                .doFor("block") { templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
                .build(MockTendermintServiceClient::class.java)

            val expectedHeight = MIN_HISTORICAL_BLOCK_HEIGHT

            // Expect success:
            dispatcherProvider.runBlockingTest {
                assert(tendermint.block(expectedHeight).result?.block?.header?.height == expectedHeight)
            }

            // Retrieving a non-existent block should fail (negative case):
            assertThrows<Throwable> {
                dispatcherProvider.runBlockingTest {
                    tendermint.block(-1)
                }
            }

            // Retrieving a non-existent block should fail (non-existent case):
            assertThrows<Throwable> {
                dispatcherProvider.runBlockingTest {
                    tendermint.block(999999999)
                }
            }
        }

        // duped
        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testBlockResultsResponse() {
            val tendermint: TendermintServiceClient = ServiceMocker.Builder()
                .doFor("blockResults") {
                    templates.readAs(
                        BlockResultsResponse::class.java,
                        "block_results/${it[0]}.json",
                    )
                }
                .build(MockTendermintServiceClient::class.java)

            val expectedHeight = MIN_HISTORICAL_BLOCK_HEIGHT

            // Expect success:
            dispatcherProvider.runBlockingTest {
                assert(tendermint.blockResults(expectedHeight).result.height == expectedHeight)
            }

            // Retrieving a non-existent block should fail (negative case):
            assertThrows<Throwable> {
                dispatcherProvider.runBlockingTest {
                    tendermint.blockResults(-1)
                }
            }

            // Retrieving a non-existent block should fail (non-existent case):
            assertThrows<Throwable> {
                dispatcherProvider.runBlockingTest {
                    tendermint.blockResults(999999999)
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testBlockchainResponse() {
            val tendermint: TendermintServiceClient = ServiceMocker.Builder()
                .doFor("blockchain") {
                    templates.readAs(
                        BlockchainResponse::class.java,
                        "blockchain/${it[0]}-${it[1]}.json",
                    )
                }
                .build(MockTendermintServiceClient::class.java)

            val expectedMinHeight: Long = MIN_HISTORICAL_BLOCK_HEIGHT
            val expectedMaxHeight: Long = expectedMinHeight + 20 - 1

            dispatcherProvider.runBlockingTest {
                val blockMetas = tendermint.blockchain(expectedMinHeight, expectedMaxHeight).result?.blockMetas

                assert(blockMetas?.size == 20)

                val expectedHeights: Set<Long> = (expectedMinHeight..expectedMaxHeight).toSet()
                val heights: Set<Long> = blockMetas?.mapNotNull { b -> b.header?.height }?.toSet() ?: setOf()

                assert((expectedHeights - heights).isEmpty())
            }

            assertThrows<Throwable> {
                dispatcherProvider.runBlockingTest {
                    tendermint.blockchain(-expectedMinHeight, expectedMaxHeight)
                }
            }
        }
    }

    @Nested
    @DisplayName("Streaming block data functionality")
    inner class Streaming {

        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testWebsocketReceiver() {
            dispatcherProvider.runBlockingTest {
                val heights: Set<Long> = setOf(
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MIN_HISTORICAL_BLOCK_HEIGHT + 1,
                    MIN_HISTORICAL_BLOCK_HEIGHT + 2,
                )
                val blocks: Array<BlockResponse> =
                    heights
                        .map { templates.unsafeReadAs(BlockResponse::class.java, "block/$it.json") }
                        .toTypedArray()

                // set up:
                val ess = MockEventStreamService
                    .Builder()
                    .dispatchers(dispatcherProvider)
                    .response(BlockResponse::class.java, *blocks)
                    .build()

                val receiver = ess.observeWebSocketEvent()

                // make sure we get the same stuff back out:
                dispatcherProvider.runBlockingTest {
                    // receive is a blocking call:
                    val event = receiver.receive()
                    assert(event is WebSocket.Event.OnMessageReceived)
                    val payload = ((event as WebSocket.Event.OnMessageReceived).message as Message.Text).value
                    val response: BlockResponse? = decoderEngine.adapter<BlockResponse>(BlockResponse::class.java).fromJson(payload)
                    assert(response?.result?.block?.header?.height in heights)
                }
            }
        }

        // done
        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testHistoricalBlockStreaming() {
            dispatcherProvider.runBlockingTest {
                // If not skipping empty blocks, we should get EXPECTED_TOTAL_BLOCKS:
                val collectedNoSkip = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .skipEmptyBlocks(false)
                    .build()
                    .streamHistoricalBlocks(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .toList()

                assert(collectedNoSkip.size.toLong() == EXPECTED_HISTORICAL_BLOCK_COUNT)
                assert(collectedNoSkip.all { it.historical })

                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .skipEmptyBlocks(true)
                    .build()
                    .streamHistoricalBlocks(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .toList()

                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
                assert(collectedSkip.all { it.historical })
            }
        }

        // done
        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testMetadataStreaming() {
            dispatcherProvider.runBlockingTest {
                // If not skipping empty blocks, we should get 100:
                val collectedNoSkip = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .toHeight(MAX_HISTORICAL_BLOCK_HEIGHT)
                    .skipEmptyBlocks(false)
                    .build()
                    .streamMetaBlocks()
                    .toList()

                assert(collectedNoSkip.size.toLong() == EXPECTED_HISTORICAL_BLOCK_COUNT)

                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .toHeight(MAX_HISTORICAL_BLOCK_HEIGHT)
                    .skipEmptyBlocks(true)
                    .build()
                    .streamMetaBlocks()
                    .toList()

                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
            }
        }

        //
        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun testLiveBlockStreaming() {
            dispatcherProvider.runBlockingTest {
                val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                    .dispatchers(dispatcherProvider)
                    .build()

                val tendermintService = Builders.tendermintService()
                    .build(MockTendermintServiceClient::class.java)

                val eventStream = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .eventStreamService(eventStreamService)
                    .tendermintService(tendermintService)
                    .skipEmptyBlocks(false)
                    .build()

                val collected = eventStream
                    .streamLiveBlocks()
                    .toList()

                assert(eventStreamService.expectedResponseCount() > 0)
                assert(collected.size == eventStreamService.expectedResponseCount().toInt())

                // All blocks should be marked as "live":
                assert(collected.all { !it.historical })
            }
        }

        //
        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testCombinedBlockStreaming() {
            dispatcherProvider.runBlockingTest {
                val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                    .dispatchers(dispatcherProvider)
                    .build()

                val tendermintService = Builders.tendermintService()
                    .build(MockTendermintServiceClient::class.java)

                val eventStream = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .eventStreamService(eventStreamService)
                    .tendermintService(tendermintService)
                    .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .toHeight(MAX_HISTORICAL_BLOCK_HEIGHT)
                    .skipEmptyBlocks(true)
                    .build()

                val expectTotal = EXPECTED_NONEMPTY_BLOCKS + eventStreamService.expectedResponseCount()

                val collected = eventStream
                    .streamBlocks()
                    .toList()

                assert(collected.size == expectTotal.toInt())
                assert(collected.filter { it.historical }.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
                assert(collected.filter { !it.historical }.size.toLong() == eventStreamService.expectedResponseCount())
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testCombinedBlockStreamingCancelledOnpanic() {
            assertThrows<CancellationException> {
                dispatcherProvider.runBlockingTest {
                    val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                        // Add a panic, which should cause the stream to terminate.
                        .response(templates.read("rpc/responses/panic.json"))
                        .dispatchers(dispatcherProvider)
                        .build()

                    val tendermintService = Builders.tendermintService()
                        .build(MockTendermintServiceClient::class.java)

                    val eventStream = Builders.eventStream()
                        .dispatchers(dispatcherProvider)
                        .eventStreamService(eventStreamService)
                        .tendermintService(tendermintService)
                        .skipEmptyBlocks(true)
                        .build()

                    eventStream.streamBlocks().toList()
                }
            }
        }
    }

    @Nested
    @DisplayName("Filtering blocks data from the stream")
    inner class StreamFiltering {
        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testTxEventFilteringByEventIfNotMatching() {
            dispatcherProvider.runBlockingTest {
                val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                    .dispatchers(dispatcherProvider)
                    .build()

                val tendermintService = Builders.tendermintService()
                    .build(MockTendermintServiceClient::class.java)

                val requireTxEvent = "DOES-NOT-EXIST"

                val eventStream = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .eventStreamService(eventStreamService)
                    .tendermintService(tendermintService)
                    .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .skipEmptyBlocks(true)
                    .matchTxEvents(setOf(requireTxEvent))
                    .build()

                val count = eventStream.streamBlocks().count()
                assert(count == 0)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
        @Test
        fun testTxEventFilteringByEventIfMatching() {
            dispatcherProvider.runBlockingTest {
                val eventStreamService = Builders.eventStreamService(includeLiveBlocks = true)
                    .dispatchers(dispatcherProvider)
                    .build()

                val tendermintService = Builders.tendermintService()
                    .build(MockTendermintServiceClient::class.java)

                val requireTxEvent = "provenance.metadata.v1.EventRecordCreated"

                val eventStream = Builders.eventStream()
                    .dispatchers(dispatcherProvider)
                    .eventStreamService(eventStreamService)
                    .tendermintService(tendermintService)
                    .fromHeight(MIN_HISTORICAL_BLOCK_HEIGHT)
                    .skipEmptyBlocks(true)
                    .matchTxEvents(setOf(requireTxEvent))
                    .build()

                val collected = eventStream
                    .streamBlocks()
                    .toList()

                assert(
                    collected.all {
                        requireTxEvent in it.txEvents.map { e -> e.eventType }
                    },
                )
            }
        }
    }
}
