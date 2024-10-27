package com.bybob.blockchain.evenstream.api

import com.bybob.blockchain.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow

interface BlockSource<T : StreamBlock> {
    fun streamBlocks(): Flow<StreamBlock>
    suspend fun streamBlocks(from: Long?, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
}
