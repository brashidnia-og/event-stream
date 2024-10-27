package com.bybob.blockchain.eventstream.stream

import com.squareup.moshi.JsonClass
import com.bybob.blockchain.evenstream.stream.models.Block
import com.bybob.blockchain.evenstream.stream.models.BlockResultsResponseResultTxsResultsEvents
import com.bybob.blockchain.evenstream.stream.models.ConsensusParamsBlock
import com.bybob.blockchain.evenstream.stream.models.ConsensusParamsEvidence
import com.bybob.blockchain.evenstream.stream.models.ConsensusParamsValidator

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockResult(
    val query: String?,
    val data: NewBlockEventResultData,
    val events: Map<String, List<String>>?,
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultData(
    val type: String,
    val value: NewBlockEventResultValue,
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultBeginBlock(
    val events: List<BlockResultsResponseResultTxsResultsEvents>,
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultValue(
    val block: Block,
    val result_begin_block: NewBlockEventResultBeginBlock,
    val result_end_block: NewBlockEventResultEndBlock?,
)

@JsonClass(generateAdapter = true)
data class ConsensusParamsUpdates(
    val block: ConsensusParamsBlock?,
    val evidence: ConsensusParamsEvidence?,
    val validator: ConsensusParamsValidator?,
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultEndBlock(
    val consensus_param_updates: ConsensusParamsBlock?,
    val events: List<BlockResultsResponseResultTxsResultsEvents>,
)
