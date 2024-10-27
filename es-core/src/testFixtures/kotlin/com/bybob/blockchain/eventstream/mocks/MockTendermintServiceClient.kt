package com.bybob.blockchain.eventstream.mocks

import com.bybob.blockchain.evenstream.stream.models.BlockResponse
import com.bybob.blockchain.evenstream.stream.models.BlockResultsResponse
import com.bybob.blockchain.evenstream.stream.models.BlockchainResponse
import com.bybob.blockchain.evenstream.stream.clients.TendermintServiceClient
import com.bybob.blockchain.evenstream.stream.models.ABCIInfoResponse
import com.bybob.blockchain.evenstream.stream.models.GenesisResponse

class MockTendermintServiceClient(mocker: ServiceMock) : TendermintServiceClient, ServiceMock by mocker {

    override suspend fun abciInfo() =
        respondWith<ABCIInfoResponse>("abciInfo")

    override suspend fun genesis() = respondWith<GenesisResponse>("genesis")

    override suspend fun block(height: Long?) =
        respondWith<BlockResponse>("block", height)

    override suspend fun blockResults(height: Long?) =
        respondWith<BlockResultsResponse>("blockResults", height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?) =
        respondWith<BlockchainResponse>("blockchain", minHeight, maxHeight)
}
