package io.bybob.blockchain.eventstream.base.mocks

import io.bybob.blockchain.eventstream.stream.models.BlockResponse
import io.bybob.blockchain.eventstream.stream.models.BlockResultsResponse
import io.bybob.blockchain.eventstream.stream.models.BlockchainResponse
import io.bybob.blockchain.eventstream.stream.clients.TendermintServiceClient
import io.bybob.blockchain.eventstream.stream.models.ABCIInfoResponse
import io.bybob.blockchain.eventstream.stream.models.GenesisResponse

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
