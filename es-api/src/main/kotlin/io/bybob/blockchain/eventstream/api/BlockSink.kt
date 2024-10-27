package io.bybob.blockchain.eventstream.api

import io.bybob.blockchain.eventstream.stream.models.StreamBlock

interface BlockSink {
    suspend operator fun invoke(block: StreamBlock)
}
