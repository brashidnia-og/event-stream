package io.bybob.blockchain.eventstream.base.mocks

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.bybob.blockchain.eventstream.coroutines.DispatcherProvider
import io.bybob.blockchain.eventstream.stream.WebSocketService
import io.bybob.blockchain.eventstream.stream.rpc.request.Subscribe
import io.bybob.blockchain.eventstream.base.utils.Defaults
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

class MockEventStreamService private constructor(
    private val channel: Channel<WebSocket.Event>,
    private val responseCount: Long,
    private val dispatchers: DispatcherProvider,
) : WebSocketService {

    class Builder {
        private var dispatchers: DispatcherProvider? = null
        private var moshi: Moshi = Defaults.moshi
        private val payloads = mutableListOf<String>()

        /**
         * Sets the dispatchers used by this event stream.
         *
         * @param value The dispatcher provider to use.
         * @return this
         */
        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }

        /**
         * Sets the JSON serializer used by this event stream.
         *
         * @param value The Moshi serializer instance to use.
         * @return this
         */
        fun moshi(value: Moshi) = apply { moshi = value }

        /**
         * Add a response to be emitted by the event stream.
         *
         * @param jsonData A JSON formatted string response for the stream to produce upon collection.
         * @return this
         */
        fun response(vararg jsonData: String): Builder = apply {
            for (json in jsonData) {
                payloads.add(json)
            }
        }

        /**
         * Add a response to be emitted by the event stream.
         *
         * @param clazz The Class defining the type emitted by the event stream.
         * @param eventData The actual data to emit.
         * @return this
         */
        fun <T> response(clazz: Class<T>, vararg eventData: T): Builder = apply {
            val response: JsonAdapter<T> = moshi.adapter(clazz)
            for (datum in eventData) {
                payloads.add(response.toJson(datum))
            }
        }

        /**
         * Creates a new instance of the event stream.
         *
         * @return A mock event stream.
         */
        suspend fun build(): MockEventStreamService {
            val channel = Channel<WebSocket.Event>(payloads.size)
            for (payload in payloads) {
                channel.send(WebSocket.Event.OnMessageReceived(Message.Text(payload)))
            }
            return MockEventStreamService(
                channel = channel,
                responseCount = payloads.size.toLong(),
                dispatchers = dispatchers ?: error("dispatchers must be provided"),
            )
        }
    }

    /**
     * Returns the number of expected responses this event stream is supposed to produce.
     *
     * @return The number of expected responses.
     */
    fun expectedResponseCount(): Long = responseCount

    /**
     * A stubbed out channel that will stop the iterator and subsequent blocking when all the items in the channel
     * have been added via the `response()` builder method have been consumed.
     */
    override fun observeWebSocketEvent(): ReceiveChannel<WebSocket.Event> {
        val iterator = channel.iterator()
        var unconsumedMessageCount = AtomicLong(responseCount)

        return object : ReceiveChannel<WebSocket.Event> by channel {

            override fun iterator(): ChannelIterator<WebSocket.Event> {
                return object : ChannelIterator<WebSocket.Event> by iterator {

                    override fun next(): WebSocket.Event {
                        unconsumedMessageCount.decrementAndGet()
                        return iterator.next()
                    }

                    override suspend fun hasNext(): Boolean {
                        if (unconsumedMessageCount.get() <= 0) {
                            // All messages have been read. We're done:
                            channel.close()
                            stop()
                            return false
                        }
                        return iterator.hasNext()
                    }
                }
            }
        }
    }

    override fun subscribe(subscribe: Subscribe) {
        runBlocking(dispatchers.io()) {
            channel.send(WebSocket.Event.OnConnectionOpened(Unit))
        }
    }

    override fun start() {
        // no-op
    }

    override fun stop() {
        // no-op
    }
}
