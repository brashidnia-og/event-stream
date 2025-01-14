package io.bybob.blockchain.eventstream.stream.consumers

import io.bybob.blockchain.eventstream.stream.EventStream
import io.bybob.blockchain.eventstream.stream.models.StreamBlock
import io.bybob.blockchain.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import mu.KotlinLogging
import io.bybob.blockchain.eventstream.BlockStreamFactory
import io.bybob.blockchain.eventstream.BlockStreamOptions

/**
 * An event stream consumer that displays blocks from the provided event stream.
 *
 * @param eventStream The event stream which provides blocks to this consumer.
 * @param options Options used to configure this consumer.
 */
@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStreamViewer(
    private val eventStream: EventStream,
    private val options: BlockStreamOptions,
) {
    constructor(
        eventStreamFactory: BlockStreamFactory,
        options: BlockStreamOptions,
    ) : this(eventStreamFactory.createSource(options) as EventStream, options)

    private val log = KotlinLogging.logger { }

    private fun onError(error: Throwable) {
        log.error("$error")
    }

    suspend fun consume(error: (Throwable) -> Unit = ::onError, ok: (block: StreamBlock) -> Unit) {
        consume(error) { b, _ -> ok(b) }
    }

    suspend fun consume(
        error: (Throwable) -> Unit = ::onError,
        ok: (block: StreamBlock, serialize: (StreamBlockImpl) -> String) -> Unit,
    ) {
        eventStream.streamBlocks()
            .buffer()
            .catch { error(it) }
            .collect { ok(it, eventStream.serializer) }
    }
}
