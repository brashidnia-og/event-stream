package com.bybob.blockchain.evenstream.api

import com.bybob.blockchain.eventstream.stream.models.StreamBlock

interface BlockSink {
    suspend operator fun invoke(block: StreamBlock)
}
