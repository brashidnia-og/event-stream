package tech.figure.eventstream.common.debug

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

private val defaultMapper = ObjectMapper().findAndRegisterModules()
//    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
//    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
//    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

fun Any.toJsonString(): String = defaultMapper.writer()
    .withDefaultPrettyPrinter()
    .writeValueAsString(this)

fun Any.toJsonStringMinify(): String = defaultMapper.writer()
    .writeValueAsString(this)
