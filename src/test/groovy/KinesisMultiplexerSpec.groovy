import com.amazonaws.services.kinesis.AmazonKinesis
import com.amazonaws.services.kinesis.model.PutRecordsRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.jayway.jsonpath.JsonPath
import groovy.json.JsonOutput
import org.apache.commons.jexl3.JexlContext
import org.apache.commons.jexl3.JexlExpression
import spock.lang.Specification

import java.nio.ByteBuffer

class KinesisMultiplexerSpec extends Specification {
    def context
    def logger

    def setup() {
        context = Mock(Context)
        logger = Mock(LambdaLogger)
        logger.log(_ as String) >> { s -> System.out.println(s) }
        context.getLogger() >> logger
    }

    def "When input is null then a null pointer is thrown"() {
        given:
        def expressionsMap = { [:] }
        def kinesisClient = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }

        def lambdaClass = new KinesisMultiplexer(expressionsMap, kinesisClient, partitionKeyMap)

        when:
        lambdaClass.handleRequest(null, context)

        then:
        thrown NullPointerException
    }

    def "Given the expressions map is empty, and a record is processed, then the messages is ignored"() {
        given:
        def expressionsMap = { [:] }
        def kinesisClient = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }

        def lambdaClass = new KinesisMultiplexer(expressionsMap, kinesisClient, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([newRecord('1', '', '')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        0 * logger.log(_ as String)
    }

    def "when a record is processed, but all the expressions in the expressionMap return false, then the message is ignored"() {
        given:
        def expression = Mock(JexlExpression)
        expression.evaluate(_ as JexlContext) >> false
        def expressionsMap = { [(expression): ""] }
        def kinesisClient = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }

        def lambdaClass = new KinesisMultiplexer(expressionsMap, kinesisClient, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('1', JsonOutput.toJson([payloadInfo: 'testPayloadInfo', payload:[payloadId: 'value'], tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        0 * logger.log(_ as String)
    }

    def "when a record is processed, and an expressions matches the criteria for a stream, then the message is outputed"() {
        given:
        def expression = Mock(JexlExpression)
        expression.evaluate(_ as JexlContext) >> true
        def expressionsMap = { [(expression): ['testOutputStream']] }
        def kinesisClient = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }

        def lambdaClass = new KinesisMultiplexer(expressionsMap, kinesisClient, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('1', JsonOutput.toJson([payloadInfo: 'testPayloadInfo', payload:[payloadId:'value'], tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        2 * logger.log(_ as String) >> {
            s -> System.out.println(s)
        }
        1 * kinesisClient.putRecords(_ as PutRecordsRequest)
    }

    def "when a record is processed, and an expressions matches the criteria for more than one streams , then the message is outputed to all"() {
        given:
        def expression = Mock(JexlExpression)
        expression.evaluate(_ as JexlContext) >> true
        def expressionsMap = {[(expression): ['testOutputStream1', 'testOutputStream2', 'testOutputStream3', 'testOutputStream4']]}
        def kinesisClient = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }

        def lambdaClass = new KinesisMultiplexer(expressionsMap, kinesisClient, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('1', JsonOutput.toJson([payloadInfo: 'testPayloadInfo', payload:[payloadId: 'value'], tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        true

        8 * logger.log(_ as String) >> {
            s -> System.out.println(s)
        }

        1 * kinesisClient.putRecords({PutRecordsRequest r -> r.streamName == 'testOutputStream1'}) >> { sleep(100) }
        1 * kinesisClient.putRecords({PutRecordsRequest r -> r.streamName == 'testOutputStream2'}) >> { sleep(100) }
        1 * kinesisClient.putRecords({PutRecordsRequest r -> r.streamName == 'testOutputStream3'}) >> { sleep(100) }
        1 * kinesisClient.putRecords({PutRecordsRequest r -> r.streamName == 'testOutputStream4'}) >> { sleep(100) }

    }

    def "when a record is processed, and an expressions matches the criteria for a stream, and a new partitionKey is mapped, then the message is outputed with the selected partitionKey"() {
        given:
        def expression = Mock(JexlExpression)
        expression.evaluate(_ as JexlContext) >> true
        def expressionsMap = {[(expression): ['testOutputStream']]}
        def kinesisClient = Mock(AmazonKinesis)
        def partitionKeyMap = { ['testOutputStream':['testPayloadInfo': JsonPath.compile('$.payload.payloadId')]] }

        def lambdaClass = new KinesisMultiplexer(expressionsMap, kinesisClient, partitionKeyMap)

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('1', JsonOutput.toJson([payloadInfo: 'testPayloadInfo', payload:[payloadId: 'value'], tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        2 * logger.log(_ as String) >> {
            s -> System.out.println(s)
        }
        1 * kinesisClient.putRecords({PutRecordsRequest r -> r.records[0].partitionKey == 'value'})
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
