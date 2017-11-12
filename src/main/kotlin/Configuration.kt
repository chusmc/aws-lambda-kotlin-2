import com.amazonaws.services.kinesis.AmazonKinesis
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClientBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.jayway.jsonpath.JsonPath
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.JexlExpression
import org.apache.commons.jexl3.MapContext
import java.util.stream.Collectors


object EnvironmentVariables {
    const val PARTITION_KEY_CONFIG = "PARTITION_KEY_CONFIG"
    const val FILTER_MAP_CONFIG = "FILTER_MAP_CONFIG"
}

internal class EnvFilterExpressionMap constructor(val environment: Map<String, String>) : () -> Map<JexlExpression, List<String>> {
    operator override fun invoke(): Map<JexlExpression, List<String>> {
        val expressionFilterMap: Map<String, List<String>> = jacksonObjectMapper().readValue(
                environment[EnvironmentVariables.FILTER_MAP_CONFIG]?: "[]", jacksonTypeRef<Map<String, List<String>>>())

        return expressionFilterMap.entries.stream().collect(Collectors.toMap(
                {e: Map.Entry<String, List<String>> -> messageFilterExpression(e.key)},
                {e: Map.Entry<String, List<String>> -> e.value }
        ))
    }
}

internal class EnvPartitionKeyMapFactory constructor(val environment: Map<String, String>) : () -> Map<String, Map<String, JsonPath>> {
    operator override fun invoke(): Map<String, Map<String, JsonPath>> {
        val partitionKeyMap: Map<String, Map<String, String>> = jacksonObjectMapper().readValue(
                environment[EnvironmentVariables.PARTITION_KEY_CONFIG]?: "[]", jacksonTypeRef<Map<String, Map<String, String>>>())

        return partitionKeyMap.entries.stream().collect(Collectors.toMap(
                {e1: Map.Entry<String, Map<String, String>> -> e1.key},
                {e1: Map.Entry<String, Map<String, String>> ->
                    e1.value.entries.stream().collect(Collectors.toMap(
                            { e2:Map.Entry<String, String> -> e2.key},
                            { e2:Map.Entry<String, String> -> JsonPath.compile(e2.value)}))}))

    }
}

internal fun kinesisStreamClient(): AmazonKinesis =
    AmazonKinesisAsyncClientBuilder.defaultClient()


private fun messageFilterExpression(expression: String): JexlExpression {
    val jexlExpression = JexlBuilder().create().createExpression(expression)
    if (jexlExpression.evaluate(MapContext(mapOf("tags" to emptyList<String>(), "agent" to ""))) !is Boolean)
        throw IllegalArgumentException("tagFilterExpression is not a valid boolean expression")

    return jexlExpression
}