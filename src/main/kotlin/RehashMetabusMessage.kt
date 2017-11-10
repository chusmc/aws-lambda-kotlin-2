import com.amazonaws.services.kinesis.AmazonKinesis
import com.amazonaws.services.kinesis.model.PutRecordsRequest
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
import com.amazonaws.services.kinesis.model.Record
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.jayway.jsonpath.Configuration

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.JexlExpression
import org.apache.commons.jexl3.MapContext
import reactor.core.publisher.Flux
import reactor.core.publisher.GroupedFlux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.function.BiFunction
import java.util.stream.Collectors
import java.util.stream.StreamSupport
import javax.inject.Inject

class RehashMetabusMessage @Inject constructor(
        val messageFilterExpressionMap: Map<JexlExpression, List<String>>,
        val outputKinesisStreamsFactory: () -> Map<String, AmazonKinesis>,
        val partitionKeyMapFactory: () -> Map<String, Map<String, String>>): RequestHandler<KinesisEvent, String> {

    //constructor() : this(tagFilterExpressionMap(System.getenv()), EnvOutputKinesisStreamsFactory(System.getenv()), EnvPartitionKeyMapFactory(System.getenv()))

    companion object MessageProperties {
        const val TAGS_PROPERTY: String = "tags"
        const val AGENT_PROPERTY: String = "agent"
        const val PAYLOADINFO_PROPERTY: String = "payloadInfo"

        @JvmStatic
        val EXPRESSION_PROPERTIES_PATH = JsonPath.compile("$.['${TAGS_PROPERTY}','${AGENT_PROPERTY }']")
        @JvmStatic
        val PAYLOADINFO_PATH = JsonPath.compile("$.${PAYLOADINFO_PROPERTY}")
        @JvmStatic
        val JSONPATH_CONFIGURATION = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS)
    }

    val partitionKeyMap: Map<String, Map<String, String>> by lazy {
        partitionKeyMapFactory()
    }

    val outputKinesisStreamMap: Map<String, AmazonKinesis> by lazy {
        outputKinesisStreamsFactory()
    }

    override fun handleRequest(input: KinesisEvent?, context: Context): String {
        val messageToStreamFlux = Flux.fromIterable(input!!.records)
                .map { recordEvent -> recordEvent.kinesis }
                .flatMap { record ->
                    Flux.combineLatest(
                            Mono.just(record),
                            Flux.fromIterable(selectOutputStreams(record)),
                            BiFunction<Record, String, Tuple2<Record, String>> { r, s -> Tuples.of(r, s) })
                }

        val putRecordToStreamFlux = messageToStreamFlux.map { tuple ->
            val record = tuple.t1
            val streamName = tuple.t2
            val putRecordRequestEntry = createPutRecordRequestEntry(record, streamName)
            context.logger.log("Created PutRecordRequestEntry for record ${record.sequenceNumber} for stream ${streamName} with partitionKey = ${putRecordRequestEntry.partitionKey}")
            Tuples.of(putRecordRequestEntry, streamName)
        }

        val groupedByStreamFluxList = putRecordToStreamFlux
                .groupBy ({ tuple -> tuple.t2 }, { tuple ->tuple.t1 })
                .filter{ group -> outputKinesisStreamMap.containsKey(group.key() as String)}
                .reduce(mutableListOf<GroupedFlux<String, PutRecordsRequestEntry>>(),  { l,e -> l.add(e); l }).block()

        groupedByStreamFluxList!!.parallelStream().forEach { groupedFlux ->
            val kinesisStreamClient = outputKinesisStreamMap[groupedFlux.key() as String] as AmazonKinesis
            groupedFlux.bufferTimeout(50, Duration.of(1, ChronoUnit.SECONDS)).subscribe { putRecordEntries ->
                context.logger.log("${Thread.currentThread().name}:${System.currentTimeMillis()} -> Trying to stream to ${groupedFlux.key()}, ${putRecordEntries.size} records")
                kinesisStreamClient.putRecords(PutRecordsRequest().withRecords(putRecordEntries))
            }
        }

        return "OK"
    }

    private fun entryMatchesRecord(expression: JexlExpression, record: Record): Boolean =
            try {
                expression.evaluate(MapContext(
                    EXPRESSION_PROPERTIES_PATH.read(String(record.data.array()), JSONPATH_CONFIGURATION) as Map<String, String>)) as Boolean
            } catch (e: Exception) {
                false
            }

    private fun selectOutputStreams(record: Record): List<String> =
            messageFilterExpressionMap.entries.stream()
                    .filter { filterEntry -> entryMatchesRecord(filterEntry.key, record) }
                    .flatMap { filterEntry -> filterEntry.value.stream() }.collect(Collectors.toList<String>())

    private fun createPutRecordRequestEntry(record: Record, streamName: String): PutRecordsRequestEntry {
        val payloadInfo = PAYLOADINFO_PATH.read(String(record.data.array()), JSONPATH_CONFIGURATION) as String?

        val partionKeyJsonPath = partitionKeyMap[streamName]?.get(payloadInfo)
        val partitionKey =
                if (partionKeyJsonPath != null)
                    JsonPath.using(JSONPATH_CONFIGURATION).parse(String(record.data.array())).read(partionKeyJsonPath) ?: record.partitionKey
                else record.partitionKey

        return PutRecordsRequestEntry().withData(record.data).withPartitionKey(partitionKey)
    }
}

private fun messageFilterExpression(expression: String): JexlExpression {
    val jexlExpression = JexlBuilder().create().createExpression(expression)
    if (jexlExpression.evaluate(MapContext(mapOf("tags" to emptyList<String>(), "agent" to ""))) !is Boolean)
        throw IllegalArgumentException("tagFilterExpression is not a valid boolean expression")

    return jexlExpression
}


class EnvPartitionKeyMapFactory constructor(val environment: Map<String, String>) : () -> Map<String, Map<String, String>> {
    operator override fun invoke(): Map<String, Map<String, String>> {
        return jacksonObjectMapper().readValue(
                environment["PARTITION_KEY_CONFIG"]?: "[]", jacksonTypeRef<Map<String, Map<String, String>>>())
    }
}




