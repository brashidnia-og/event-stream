package io.bybob.blockchain.eventstream.stream.observers

import io.bybob.blockchain.eventstream.api.BlockSink
import io.bybob.blockchain.eventstream.stream.models.Event
import mu.KotlinLogging
import io.bybob.blockchain.eventstream.decodeBase64
import io.bybob.blockchain.eventstream.isAsciiPrintable
import io.bybob.blockchain.eventstream.stream.models.BlockEvent
import io.bybob.blockchain.eventstream.stream.models.StreamBlock
import io.bybob.blockchain.eventstream.stream.models.StreamBlockImpl
import io.bybob.blockchain.eventstream.stream.models.TxEvent
import io.bybob.blockchain.eventstream.stream.models.dateTime

fun consoleOutput(verbose: Boolean, nth: Int = 100): ConsoleOutput = ConsoleOutput(verbose, nth)

class ConsoleOutput(private val verbose: Boolean, private val nth: Int) : BlockSink {
    private val log = KotlinLogging.logger {}

    private val logAttribute: (Event) -> Unit = {
        log.info { "    ${it.key?.repeatDecodeBase64()}: ${it.value?.repeatDecodeBase64()}" }
    }

    private val logBlockTxEvent: (TxEvent) -> Unit = {
        log.info { "  Tx-Event: ${it.eventType}" }
        it.attributes.forEach(logAttribute)
    }

    private val logBlockEvent: (BlockEvent) -> Unit = {
        log.info { "  Block-Event: ${it.eventType}" }
        it.attributes.forEach(logAttribute)
    }

    private val logBlockInfo: StreamBlockImpl.() -> Unit = {
        val height = block.header?.height ?: "--"
        val date = block.header?.dateTime()?.toLocalDate()
        val hash = block.header?.lastBlockId?.hash
        val size = txEvents.size
        log.info { "Block: $height: $date $hash; $size tx event(s)" }
    }

    override suspend fun invoke(block: StreamBlock) {
        if (block.height!! % nth != 0L) {
            return
        }

        (block as StreamBlockImpl).logBlockInfo()
        if (verbose) {
            block.txEvents.forEach(logBlockTxEvent)
            block.blockEvents.forEach(logBlockEvent)
        }
    }
}

/**
 * Decodes a string repeatedly base64 encoded, terminating when:
 *
 * - the decoded string stops changing or
 * - the maximum number of iterations is reached
 * - or the decoded string is no longer ASCII printable
 *
 * In the event of failure, the last successfully decoded string is returned.
 */
private fun String.repeatDecodeBase64(): String {
    var s: String = this.toString() // copy
    var t: String = s.decodeBase64().stripQuotes()
    repeat(10) {
        if (s == t || !t.isAsciiPrintable()) {
            return s
        }
        s = t
        t = t.decodeBase64().stripQuotes()
    }
    return s
}

/**
 * Remove surrounding quotation marks from a string.
 */
private fun String.stripQuotes(): String = this.removeSurrounding("\"")
