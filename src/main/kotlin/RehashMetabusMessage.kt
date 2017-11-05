import com.amazonaws.services.kinesis.AmazonKinesis
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder
import com.amazonaws.services.kinesis.model.PutRecordsRequest
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.jayway.jsonpath.JsonPath
import reactor.core.publisher.Flux
import reactor.core.publisher.SynchronousSink
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject


class RehashMetabusMessage @Inject constructor(
    val kinesisClient: AmazonKinesis,
    val partitionKeyMapFactory: () -> Map<String, String>): RequestHandler<KinesisEvent, String> {

    constructor() : this(AmazonKinesisClientBuilder.defaultClient(), EnvPartitionKeyMapFactory(System.getenv()))

    val partitionKeyMap: Map<String, String> by lazy {
        partitionKeyMapFactory()
    }

    companion object {
        @JvmStatic
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    }

    override fun handleRequest(input: KinesisEvent?, context: Context?): String {

        Flux.fromIterable(input!!.records).handle { record, sink: SynchronousSink<PutRecordsRequestEntry> ->
            try {
                val message = objectMapper.readValue(record.kinesis.data.array(), Message::class.java)
                val partitionKey = if (partitionKeyMap[message.payloadInfo] != null) JsonPath.read(message.payload, partitionKeyMap[message.payloadInfo]) else record.kinesis.partitionKey
                sink.next(PutRecordsRequestEntry().withData(record.kinesis.data).withPartitionKey(partitionKey))
            } catch (e: Exception) {
                context!!.logger.log(e.toString())
            }
        }.bufferTimeout(20, Duration.of(1, ChronoUnit.SECONDS)).subscribe { putRecords ->
            kinesisClient.putRecords(PutRecordsRequest().withRecords(putRecords))
        }
        return "OK"
    }

}

class EnvPartitionKeyMapFactory constructor(val environment: Map<String, String>) : () -> Map<String, String> {
    operator override fun invoke(): Map<String, String> {
        return jacksonObjectMapper().readValue<Map<String, String>>(
                environment["PARTITION_KEY_CONFIG"], jacksonTypeRef<Map<String, String>>())
    }
}




