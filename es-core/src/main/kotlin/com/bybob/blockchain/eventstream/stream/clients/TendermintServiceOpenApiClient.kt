package com.bybob.blockchain.evenstream.stream.clients

import okhttp3.OkHttpClient
import com.bybob.blockchain.evenstream.stream.apis.ABCIApi
import com.bybob.blockchain.evenstream.stream.apis.InfoApi
import com.bybob.blockchain.evenstream.stream.infrastructure.ApiClient
import com.bybob.blockchain.evenstream.stream.models.ABCIInfoResponse
import com.bybob.blockchain.evenstream.stream.models.BlockResponse
import com.bybob.blockchain.evenstream.stream.models.BlockResultsResponse
import com.bybob.blockchain.evenstream.stream.models.BlockchainResponse
import com.bybob.blockchain.evenstream.stream.models.GenesisResponse

/**
 * An OpenAPI generated client designed to interact with the Tendermint RPC API.
 *
 * All requests and responses are HTTP+JSON.
 *
 * @param rpcUrlBase The base URL of the Tendermint RPC API to use when making requests.
 * @param configureBuilderFn Builder lambda to configure the underlying [OkHttpClient]
 */
class TendermintServiceOpenApiClient(
    rpcUrlBase: String,
    configureBuilderFn: OkHttpClient.Builder.() -> OkHttpClient.Builder = { this },
) : TendermintServiceClient {
    init {
        ApiClient.builder.apply { configureBuilderFn() }
    }

    private val abciApi = ABCIApi(rpcUrlBase)
    private val infoApi = InfoApi(rpcUrlBase)

    override suspend fun abciInfo(): ABCIInfoResponse = abciApi.abciInfo()

    override suspend fun genesis(): GenesisResponse = infoApi.genesis()

    override suspend fun block(height: Long?): BlockResponse = infoApi.block(height)

    override suspend fun blockResults(height: Long?): BlockResultsResponse = infoApi.blockResults(height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)
}
