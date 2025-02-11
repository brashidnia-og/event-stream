package io.bybob.blockchain.eventstream.config

import com.sksamuel.hoplite.ConfigAlias
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY

// Data classes in this file are intended to be instantiated by the hoplite configuration library

data class WebsocketStreamConfig(
    val uri: String,
    @ConfigAlias("throttle_duration_ms") val throttleDurationMs: Long = 0,
)

data class RpcStreamConfig(val uri: String)

data class StreamEventsFilterConfig(
    @ConfigAlias("tx_events") val txEvents: Set<String> = emptySet(),
    @ConfigAlias("block_events") val blockEvents: Set<String> = emptySet(),
) {
    companion object {
        val empty: StreamEventsFilterConfig get() = StreamEventsFilterConfig()
    }
}

data class BatchConfig(
    val size: Int,
    @ConfigAlias("timeout_ms") val timeoutMillis: Long?,
)

@OptIn(FlowPreview::class)
data class EventStreamConfig(
    val websocket: WebsocketStreamConfig,
    val rpc: RpcStreamConfig,
    val batch: BatchConfig,
    val filter: StreamEventsFilterConfig = StreamEventsFilterConfig(),
    val height: HeightConfig = HeightConfig(),
    val concurrency: Int = DEFAULT_CONCURRENCY,
    val ordered: Boolean?,
    @ConfigAlias("skip_empty_blocks") val skipEmptyBlocks: Boolean?,
)

data class HeightConfig(
    val from: Long = 1,
    val to: Long? = null,
)

data class UploadConfig(
    val extractors: List<String> = emptyList(),
) {
    companion object {
        val empty: UploadConfig get() = UploadConfig()
    }
}

data class Config(
    val verbose: Boolean = false,
    @ConfigAlias("event-stream") val eventStream: EventStreamConfig,
    val upload: UploadConfig = UploadConfig(),
    val node: String,
    val from: Long? = null,
    val to: Long? = null,
    val ordered: Boolean = false,
)
