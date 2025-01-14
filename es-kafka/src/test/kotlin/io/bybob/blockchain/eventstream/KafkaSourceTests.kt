package io.bybob.blockchain.eventstream

import com.squareup.moshi.JsonAdapter
import io.bybob.blockchain.eventstream.stream.KafkaStreamBlock
import io.bybob.blockchain.eventstream.stream.acking
import io.bybob.blockchain.eventstream.stream.models.Block
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import io.bybob.blockchain.eventstream.stream.toByteArray
import io.bybob.blockchain.eventstream.stream.toStreamBlock
import io.bybob.blockchain.eventstream.test.base.TestBase
import io.provenance.kafka.coroutine.kafkaConsumerChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import io.bybob.blockchain.eventstream.stream.models.BlockEvent
import io.bybob.blockchain.eventstream.stream.models.BlockResponse
import io.bybob.blockchain.eventstream.stream.models.StreamBlockImpl
import io.bybob.blockchain.eventstream.base.utils.Defaults.moshi
import java.time.OffsetDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaSourceTests : TestBase() {

    private val consumerProps = mapOf(
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to 10000,
        ConsumerConfig.GROUP_ID_CONFIG to "test-group",
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
        CommonClientConfigs.CLIENT_ID_CONFIG to (UUID.randomUUID().toString()),
    )
    private var streamBlocks = mutableMapOf<String, StreamBlockImpl>()

    @BeforeAll
    override fun setup() {
        val blockResponses = mutableMapOf<String, BlockResponse>()
        val blockResultsResponses = mutableMapOf<String, BlockResultsResponse>()
        templates.readAll("block").forEach {
            val adapter: JsonAdapter<BlockResponse> = moshi.adapter(BlockResponse::class.java)
            val blockResponse = adapter.fromJson(it)
            blockResponses[blockResponse!!.result!!.block!!.header!!.height.toString()] = blockResponse
        }
        templates.readAll("block_results").forEach {
            val adapter: JsonAdapter<BlockResultsResponse> = moshi.adapter(BlockResultsResponse::class.java)
            val blockResultsResponse = adapter.fromJson(it)
            blockResultsResponses[blockResultsResponse!!.result.height.toString()] = blockResultsResponse
        }
        blockResultsResponses.forEach { k, v ->
            val blockEvents = v.result.beginBlockEvents!!.map {
                BlockEvent(
                    v.result.height,
                    OffsetDateTime.now(),
                    it.type!!,
                    it.attributes!!,
                )
            }
            streamBlocks[k] = StreamBlockImpl(blockResponses[k]!!.result!!.block!!, blockEvents, mutableListOf(), mutableListOf(), mutableListOf())
        }
    }

    @Test
    fun testStreamBlockByteArrayExtensions() {
        val streamBytes = streamBlocks["2270370"]!!.toByteArray()
        val streamBlockImpl = streamBytes!!.toStreamBlock()
        assert(streamBlockImpl!!.height == 2270370L)
    }

    @Test
    fun testStreamBlockByteArrayExtensionsEmpty() {
        val streamBytes = StreamBlockImpl(Block(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()).toByteArray()
        val streamBlockImpl = streamBytes!!.toStreamBlock()
        assert(streamBlockImpl!!.block.data == null)
    }

    @Test
    fun testStreamBlockByteArrayExtensionsIncompatibleValue() {
        val streamBytes = "failStrin".toByteArray()
        assertThrows<SerializationException> {
            streamBytes.toStreamBlock()
        }
    }

    @Test
    fun testKafkaConsumerMultipleRecords() {
        val mockConsumer = MockConsumer<ByteArray, ByteArray>(
            OffsetResetStrategy.EARLIEST,
        )
        val tp1 = TopicPartition("test-topic", 0)
        mockConsumer.schedulePollTask {
            mockConsumer.rebalance(listOf(tp1))
        }
        mockConsumer.schedulePollTask {
            mockConsumer.addRecord(
                ConsumerRecord(
                    "test-topic",
                    0,
                    0L,
                    "testing1".toByteArray(),
                    streamBlocks["2270370"]!!.toByteArray(),
                ),
            )
            mockConsumer.addRecord(
                ConsumerRecord(
                    "test-topic",
                    0,
                    1L,
                    "testing2".toByteArray(),
                    streamBlocks["2270371"]!!.toByteArray(),
                ),
            )
            mockConsumer.addRecord(
                ConsumerRecord(
                    "test-topic",
                    0,
                    3L,
                    "testing3".toByteArray(),
                    streamBlocks["2270372"]!!.toByteArray(),
                ),
            )
        }
        val startOffsets: HashMap<TopicPartition, Long> = HashMap()

        startOffsets[tp1] = 0L
        mockConsumer.updateBeginningOffsets(startOffsets)
        val results = mutableListOf<KafkaStreamBlock>()
        try {
            runBlocking {
                val kafkaChannel = kafkaConsumerChannel<ByteArray, ByteArray>(
                    consumerProps,
                    setOf("test-topic"),
                    consumer = mockConsumer,
                )
                mockConsumer.scheduleNopPollTask()
                mockConsumer.schedulePollTask {
                    kafkaChannel.cancel()
                }
                kafkaChannel.receiveAsFlow().map { KafkaStreamBlock(it) }
                    .onEach {
                        results.add(it)
                    }
                    .acking {}.collect {}
            }
        } catch (ex: Exception) {
            assert(results.size == 3)
            assert(results[0].height == 2270370L)
        }
    }

    @Test
    fun testKafkaConsumerEmptyPoll() {
        val mockConsumer = MockConsumer<ByteArray, ByteArray>(
            OffsetResetStrategy.EARLIEST,
        )
        val tp1 = TopicPartition("test-topic", 0)
        mockConsumer.schedulePollTask {
            mockConsumer.rebalance(listOf(tp1))
        }
        mockConsumer.scheduleNopPollTask()
        mockConsumer.schedulePollTask {
            mockConsumer.addRecord(
                ConsumerRecord(
                    "test-topic",
                    0,
                    0L,
                    "testing1".toByteArray(),
                    streamBlocks["2270370"]!!.toByteArray(),
                ),
            )
        }
        val startOffsets: HashMap<TopicPartition, Long> = HashMap()

        startOffsets[tp1] = 0L
        mockConsumer.updateBeginningOffsets(startOffsets)
        val results = mutableListOf<KafkaStreamBlock>()
        try {
            runBlocking {
                val kafkaChannel = kafkaConsumerChannel<ByteArray, ByteArray>(
                    consumerProps,
                    setOf("test-topic"),
                    consumer = mockConsumer,
                )
                mockConsumer.scheduleNopPollTask()
                mockConsumer.schedulePollTask {
                    mockConsumer.close()
                }
                kafkaChannel.receiveAsFlow().map { KafkaStreamBlock(it) }
                    .onEach {
                        results.add(it)
                    }
                    .acking {}.collect {}
            }
        } catch (ex: Exception) {
            assert(results.size == 1)
            assert(results[0].height == 2270370L)
        }
    }

    @Test
    fun testKafkaConsumerClosedError() {
        val mockConsumer = MockConsumer<ByteArray, ByteArray>(
            OffsetResetStrategy.EARLIEST,
        )
        val tp1 = TopicPartition("test-topic", 0)
        val startOffsets: HashMap<TopicPartition, Long> = HashMap()

        startOffsets[tp1] = 0L
        mockConsumer.updateBeginningOffsets(startOffsets)
        val results = mutableListOf<KafkaStreamBlock>()
        assertThrows<IllegalStateException> {
            runBlocking {
                val kafkaChannel = kafkaConsumerChannel<ByteArray, ByteArray>(
                    consumerProps,
                    setOf("test-topic"),
                    consumer = mockConsumer,
                )
                mockConsumer.schedulePollTask {
                    mockConsumer.close()
                }
                kafkaChannel.receiveAsFlow().map { KafkaStreamBlock(it) }
                    .onEach {
                        results.add(it)
                    }
                    .acking {}.collect {}
            }
        }
    }

    @Test
    fun testKafkaConsumerWrongBytes() {
        val mockConsumer = MockConsumer<ByteArray, ByteArray>(
            OffsetResetStrategy.EARLIEST,
        )
        val tp1 = TopicPartition("test-topic", 0)
        mockConsumer.schedulePollTask {
            mockConsumer.rebalance(listOf(tp1))
        }
        mockConsumer.schedulePollTask {
            mockConsumer.addRecord(
                ConsumerRecord(
                    "test-topic",
                    0,
                    0L,
                    "testing1".toByteArray(),
                    "Wrong value for testing".toByteArray(),
                ),
            )
        }
        val startOffsets: HashMap<TopicPartition, Long> = HashMap()

        startOffsets[tp1] = 0L
        mockConsumer.updateBeginningOffsets(startOffsets)
        val results = mutableListOf<KafkaStreamBlock>()
        assertThrows<java.lang.Exception> {
            runBlocking(Dispatchers.IO) {
                val kafkaChannel = kafkaConsumerChannel<ByteArray, ByteArray>(
                    consumerProps,
                    setOf("test-topic"),
                    consumer = mockConsumer,
                )
                mockConsumer.schedulePollTask {
                    mockConsumer.close()
                }
                kafkaChannel.receiveAsFlow().map { KafkaStreamBlock(it) }
                    .onEach {
                        results.add(it)
                    }
                    .acking {}.collect {}
            }
        }
    }
}
