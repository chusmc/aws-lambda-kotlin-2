import com.amazonaws.services.kinesis.AmazonKinesis
import com.amazonaws.services.kinesis.model.PutRecordsRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import groovy.json.JsonOutput
import org.apache.commons.jexl3.JexlContext
import org.apache.commons.jexl3.JexlExpression
import spock.lang.Shared
import spock.lang.Specification

import java.nio.ByteBuffer

class RehashMetabusMessageSpec extends Specification {
    def context
    def logger

    def setup() {
        context = Mock(Context)
        logger = Mock(LambdaLogger)
        logger.log(_ as String) >> { s -> System.out.println(s) }
    }

    def "When input is null then a null pointer is thrown"() {
        given:
        def expressionsMap = [:]
        def kinesisStreamsMap = { [:] }
        def partitionKeyMap = { [:] }
        def lambdaClass = new RehashMetabusMessage(expressionsMap, kinesisStreamsMap, partitionKeyMap)

        when:
        lambdaClass.handleRequest(null, context)

        then:
        thrown NullPointerException
    }

    def "Given the expressions map is empty, and a record is processed, then the messages is ignored"() {
        given:
        def expressionsMap = [:]
        def kinesisStreamsMap = { [:] }
        def partitionKeyMap = { [:] }

        def lambdaClass = new RehashMetabusMessage(expressionsMap, kinesisStreamsMap, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([newRecord('1', '', '')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        0 * logger.log(_ as String)
    }

    def "when a record is processed, but all the expressions in the expressionMap return false, then the messages is ignored"() {
        given:
        def expression = Mock(JexlExpression)
        expression.evaluate(_ as JexlContext) >> false
        def expressionsMap = [(expression): ""]
        def kinesisStreamsMap = { [:] }
        def partitionKeyMap = { [:] }

        def lambdaClass = new RehashMetabusMessage(expressionsMap, kinesisStreamsMap, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('1', JsonOutput.toJson([payloadInfo: 'payloadInfo', payload:'{"payloadId": "value"}', tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        0 * logger.log(_ as String)
    }



    def "when a record is processed, and an expressions in the expressionMap return false, then the messages is ignored"() {
        given:
        def expression = Mock(JexlExpression)
        expression.evaluate(_ as JexlContext) >> false
        def expressionsMap = [(expression): ""]
        def kinesisStreamsMap = { [:] }
        def partitionKeyMap = { [:] }

        def lambdaClass = new RehashMetabusMessage(expressionsMap, kinesisStreamsMap, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('1', JsonOutput.toJson([payloadInfo: 'payloadInfo', payload:'{"payloadId": "value"}', tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        0 * logger.log(_ as String)
    }

    private def newRecord(String sequenceNumber, String data, String partitionKey) {
        def kinesisRecord = new KinesisEvent.Record()
        kinesisRecord.setData(ByteBuffer.wrap(data.bytes))
        kinesisRecord.setSequenceNumber(sequenceNumber)
        kinesisRecord.setPartitionKey(partitionKey)

        def kinesisEventRecord = new  KinesisEvent.KinesisEventRecord()
        kinesisEventRecord.setKinesis(kinesisRecord)

        return kinesisEventRecord
    }

}
