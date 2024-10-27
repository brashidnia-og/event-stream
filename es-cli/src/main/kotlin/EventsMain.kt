import com.bybob.blockchain.eventstream.decoder.moshiDecoderAdapter
import com.bybob.blockchain.eventstream.net.okHttpNetAdapter
import com.bybob.blockchain.eventstream.stream.flows.blockDataFlow
import com.bybob.blockchain.eventstream.stream.flows.blockHeaderFlow
import com.bybob.blockchain.eventstream.stream.flows.historicalBlockDataFlow
import com.bybob.blockchain.eventstream.stream.flows.historicalBlockHeaderFlow
import com.bybob.blockchain.eventstream.stream.flows.nodeEventStream
import com.bybob.blockchain.eventstream.stream.flows.pollingBlockDataFlow
import com.bybob.blockchain.eventstream.stream.flows.pollingBlockHeaderFlow
import com.bybob.blockchain.eventstream.stream.flows.wsBlockDataFlow
import com.bybob.blockchain.eventstream.stream.flows.wsBlockHeaderFlow
import com.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main() = runBlocking {
    val log = KotlinLogging.logger {}
    val host = "https://rpc.provenance.io"
    val netAdapter = okHttpNetAdapter(host)
    val decoderAdapter = moshiDecoderAdapter()

    // Example is not collected.
    historicalBlockHeaderFlow(netAdapter, 1, 100)
        .onEach { if (it.height % 1500 == 0L) { log.info { "oldHeader: ${it.height}" } } }

    // Example is not collected.
    historicalBlockDataFlow(netAdapter, 1, 100)
        .onEach { if (it.height % 1500 == 0L) { log.info { "oldBlock: ${it.height}" } } }

    // Example is not collected.
    wsBlockHeaderFlow(netAdapter, decoderAdapter)
        .onEach { println("liveHeader: ${it.height}") }

    // Example is not collected.
    wsBlockDataFlow(netAdapter, decoderAdapter)
        .onEach { println("liveBlock: $it") }

    // Example is not collected.
    nodeEventStream<MessageType.NewBlock>(netAdapter, decoderAdapter)
        .onEach { println("liveBlock: $it") }

    // Use metadataFlow to fetch from:(current - 10000) to:(current).
    // This will combine the historical flow and live flow to create an ordered stream of BlockHeaders.
    // Example is not collected.
    val current = netAdapter.rpcAdapter.getCurrentHeight()!!
    blockHeaderFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
        .onEach { println("recv:${it.height}") }

    // Use metadataFlow to fetch from:(current - 10000) to:(current).
    // This will combine the historical flow and live flow to create an ordered stream of BlockHeaders.
    // Example is not collected.
    blockDataFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
        .onEach { println("recv:$it") }

    // Example is not collected.
    pollingBlockDataFlow(netAdapter, from = current - 10)
        .onEach { println("revc:${it.height}") }

    // Example is not collected.
    pollingBlockHeaderFlow(netAdapter)
        .onEach { println("revc:${it.height}") }

    netAdapter.shutdown()
}
