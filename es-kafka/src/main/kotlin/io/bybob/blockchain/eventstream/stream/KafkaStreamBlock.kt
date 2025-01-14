package io.bybob.blockchain.eventstream.stream

import io.bybob.blockchain.eventstream.stream.models.Block
import io.bybob.blockchain.eventstream.stream.models.StreamBlock
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.bybob.blockchain.eventstream.stream.models.TxError
import io.bybob.blockchain.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord
import io.provenance.kafka.coroutine.UnAckedConsumerRecord
import io.bybob.blockchain.eventstream.stream.models.BlockEvent

class KafkaStreamBlock(
    private val record: UnAckedConsumerRecord<ByteArray, ByteArray>,
) : StreamBlock {
    private val streamBlock: StreamBlock by lazy { record.value.toStreamBlock()!! }
    override val block: Block by lazy { streamBlock.block }
    override val blockEvents: List<BlockEvent> by lazy { streamBlock.blockEvents }
    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
    override val historical: Boolean by lazy { streamBlock.historical }
    override val blockResult: List<BlockResultsResponseResultTxsResults>? by lazy { streamBlock.blockResult }
    override val txErrors: List<TxError> by lazy { streamBlock.txErrors }

    suspend fun ack(): AckedConsumerRecord<ByteArray, ByteArray> {
        return record.ack()
    }
}
