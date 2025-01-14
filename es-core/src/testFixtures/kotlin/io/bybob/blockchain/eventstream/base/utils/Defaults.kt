package io.bybob.blockchain.eventstream.base.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.bybob.blockchain.eventstream.adapter.json.JSONObjectAdapter
import io.bybob.blockchain.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.bybob.blockchain.eventstream.stream.infrastructure.Serializer
import io.bybob.blockchain.eventstream.stream.models.BlockResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import io.bybob.blockchain.eventstream.stream.models.BlockchainResponse

object Defaults {

    val moshi: Moshi = newMoshi()

    private fun newMoshi(): Moshi = Serializer.moshiBuilder
        .addLast(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()

    fun decoderEngine() = MoshiDecoderEngine(moshi)

    val templates = newTemplate()

    fun newTemplate(): Template = Template(moshi)

    fun blockResponses(): Array<BlockResponse> =
        heights
            .map { templates.unsafeReadAs(BlockResponse::class.java, "block/$it.json") }
            .toTypedArray()

    fun blockResultsResponses(): Array<BlockResultsResponse> =
        heights
            .map { templates.unsafeReadAs(BlockResultsResponse::class.java, "block_results/$it.json") }
            .toTypedArray()

    fun blockchainResponses(): Array<BlockchainResponse> =
        heightChunks
            .map { (minHeight, maxHeight) ->
                templates.unsafeReadAs(
                    BlockchainResponse::class.java,
                    "blockchain/$minHeight-$maxHeight.json",
                )
            }
            .toTypedArray()
}
