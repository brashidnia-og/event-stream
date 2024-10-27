package io.bybob.blockchain.eventstream.stream

import io.bybob.blockchain.eventstream.stream.infrastructure.Serializer.moshi
import io.bybob.blockchain.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.kafka.common.errors.SerializationException

fun Flow<KafkaStreamBlock>.acking(block: (KafkaStreamBlock) -> Unit): Flow<AckedKafkaStreamBlock<ByteArray, ByteArray>> {
    return flow {
        collect {
            block(it)
            emit(AckedKafkaStreamBlock(it.ack()))
        }
    }
}

fun StreamBlockImpl.toByteArray(): ByteArray? {
    return try {
        moshi.adapter(StreamBlockImpl::class.java)
            .toJson(this)
            .toByteArray()
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}

fun ByteArray.toStreamBlock(): StreamBlockImpl? {
    return try {
        moshi.adapter(StreamBlockImpl::class.java)
            .fromJson(this.decodeToString())
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}
