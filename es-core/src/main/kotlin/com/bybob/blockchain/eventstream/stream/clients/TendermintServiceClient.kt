package com.bybob.blockchain.evenstream.stream.clients

import com.bybob.blockchain.evenstream.stream.models.ABCIInfoResponse
import com.bybob.blockchain.evenstream.stream.models.BlockResponse
import com.bybob.blockchain.evenstream.stream.models.BlockResultsResponse
import com.bybob.blockchain.evenstream.stream.models.BlockchainResponse
import com.bybob.blockchain.evenstream.stream.models.GenesisResponse

/**
 * A client designed to interact with the Tendermint RPC API.
 */
interface TendermintServiceClient {
    suspend fun abciInfo(): ABCIInfoResponse
    suspend fun genesis(): GenesisResponse
    suspend fun block(height: Long?): BlockResponse
    suspend fun blockResults(height: Long?): BlockResultsResponse
    suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse
}
