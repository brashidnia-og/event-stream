package io.bybob.blockchain.eventstream.stream.decoder

import io.bybob.blockchain.eventstream.adapter.json.decoder.Adapter
import io.bybob.blockchain.eventstream.adapter.json.decoder.DecoderEngine
import io.bybob.blockchain.eventstream.stream.rpc.response.MessageType
import io.bybob.blockchain.eventstream.stream.rpc.response.RpcError
import io.bybob.blockchain.eventstream.stream.rpc.response.RpcResponse
import org.json.JSONObject

class ErrorMessageDecoder(decoderEngine: DecoderEngine) : Decoder(decoderEngine) {

    override val priority: Int = 99

    // The response will come come wrapped in a "response" { } property in the event of an error.

    private val adapter: Adapter<RpcResponse<JSONObject>> = decoderEngine.adapter(
        decoderEngine.parameterizedType(RpcResponse::class.java, JSONObject::class.java),
    )

    private fun toError(obj: JSONObject): RpcError? {
        if (!obj.has("code")) {
            return null
        }
        val code = obj.getInt("code")
        val message: String? = if (obj.has("message")) {
            obj.getString("message")
        } else {
            null
        }
        val log: String? = if (obj.has("log")) {
            obj.getString("log")
        } else {
            null
        }
        return RpcError(code = code, log = log, message = message)
    }

    override fun decode(input: String): MessageType? {
        val response: RpcResponse<JSONObject> = adapter.fromJson(input) ?: return null
        val json: JSONObject = response.result ?: return null
        val error: RpcError? = if (json.has("response")) {
            toError(json.getJSONObject("response"))
        } else {
            toError(json)
        }
        return error?.let { e ->
            if (e.isPanic()) {
                MessageType.Panic(e)
            } else {
                MessageType.Error(e)
            }
        }
    }
}
