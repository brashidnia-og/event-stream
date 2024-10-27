package com.bybob.blockchain.eventstream.api

import com.bybob.blockchain.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow

interface BlockSource<T : StreamBlock> {
    fun streamBlocks(): Flow<StreamBlock>
    suspend fun streamBlocks(from: Long?, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
}
