package io.bybob.blockchain.eventstream.stream.clients

import okhttp3.OkHttpClient
import io.bybob.blockchain.eventstream.stream.apis.ABCIApi
import io.bybob.blockchain.eventstream.stream.apis.InfoApi
import io.bybob.blockchain.eventstream.stream.infrastructure.ApiClient
import io.bybob.blockchain.eventstream.stream.models.ABCIInfoResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import io.bybob.blockchain.eventstream.stream.models.BlockchainResponse
import io.bybob.blockchain.eventstream.stream.models.GenesisResponse

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
