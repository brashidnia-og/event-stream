package io.bybob.blockchain.eventstream.stream.flows

import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import mu.KotlinLogging
import io.bybob.blockchain.eventstream.adapter.json.decoder.MessageDecoder
import io.bybob.blockchain.eventstream.common.debug.toJsonString
import io.bybob.blockchain.eventstream.decoder.DecoderAdapter
import io.bybob.blockchain.eventstream.defaultLifecycle
import io.bybob.blockchain.eventstream.defaultWebSocketChannel
import io.bybob.blockchain.eventstream.net.NetAdapter
import io.bybob.blockchain.eventstream.stream.WebSocketChannel
import io.bybob.blockchain.eventstream.stream.WebSocketService
import io.bybob.blockchain.eventstream.stream.rpc.request.Subscribe
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.stream.withLifecycle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val DEFAULT_THROTTLE_PERIOD = 1.seconds

/**
 * Decode the flow of [Message] into a flow of [MessageType]
 *
 * @param decoder The decoder function used to decode the [Message]s from the websocket.
 * @return A [Flow] of decoded websocket messages of type [MessageType]
 * @throws [CancellationException] When [MessageType.Panic] is encountered in the source flow.
 */
@Suppress("unchecked_cast")
fun <T : MessageType> Flow<Message>.decodeMessages(decoder: MessageDecoder): Flow<T> =
    map {
        if (it is Message.Text && it.value.isBlank()) {
            MessageType.Empty
        } else {
            decoder(it)
        }
    }.transform {
        val log = KotlinLogging.logger {}
        log.info("transform it.toString()")
        log.info(it.toJsonString())
        when (it) {
            is MessageType.Panic -> {
                throw CancellationException("RPC endpoint panic: ${it.error}")
            }

            is MessageType.Error -> log.error { "failed to handle ws message:${it.error}" }
            is MessageType.Unknown -> log.info { "unknown message type:${it.type}" }
            is MessageType.Empty -> log.debug { "received empty message type" }

            else -> emit(it as T)
        }
    }

/**
 * Creates a new websocket client to listen to events and messages from a tendermint node.
 *
 * @param subscription The tendermint websocket subscription to connect with.
 * @return A [Flow] of websocket Messages that can be processed downstream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun webSocketClient(
    subscription: Subscribe,
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
): Flow<Message> = channelFlow {
    val log = KotlinLogging.logger {}

    invokeOnClose {
        try { wss.stop() } catch (e: Throwable) { /* Ignored */ }
    }

    // Toggle the Lifecycle register start state
    log.debug { "starting web socket client" }
    wss.start()

    log.debug { "listening for web events" }
    for (event in wss.observeWebSocketEvent()) {
        log.trace { "got event: $event" }
        when (event) {
            is WebSocket.Event.OnConnectionOpened<*> -> {
                log.debug { "connection established, initializing subscription:$subscription" }
                wss.subscribe(subscription)
            }

            is WebSocket.Event.OnMessageReceived -> {
                log.trace { "message received" }
                send(event.message)
            }

            is WebSocket.Event.OnConnectionClosing -> {
                log.info { "connection closing" }
                close(CancellationException("connection closed"))
            }

            is WebSocket.Event.OnConnectionFailed -> {
                log.info("connection failed", event.throwable)
                /* no-op: let the scarlet retry takeover from here */
            }

            else -> {
                log.warn { "unexpected event:$event" }
                throw CancellationException("unexpected event:$event")
            }
        }
    }
    log.debug { "stopping web socket client" }
}
