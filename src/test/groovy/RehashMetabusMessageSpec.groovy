import com.amazonaws.services.kinesis.AmazonKinesis
import com.amazonaws.services.kinesis.model.PutRecordsRequest
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import groovy.json.JsonOutput
import spock.lang.Specification

import java.nio.ByteBuffer

class RehashMetabusMessageSpec extends Specification {

    def "When input is null then a null pointer is thrown"() {
        given:
        def kinesis = Mock(AmazonKinesis)
        def partitionKeyMap = { s -> [:] }
        def lambdaClass = new RehashMetabusMessage(kinesis, partitionKeyMap)

        when:
        lambdaClass.handleRequest(null, null)

        then:
        thrown NullPointerException
    }

    def "When input contains a Kinesis record, that can't be deserialized to a valid Message, then the message is ignored and error is logged"() {
        given:
        def kinesis = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }
        def lambdaClass = new RehashMetabusMessage(kinesis, partitionKeyMap)

        and:
        def context = Mock(Context)
        def logger = Mock(LambdaLogger)
        context.getLogger() >> logger

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([newRecord('noMessageData', 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        0 * kinesis.putRecords(_ as PutRecordsRequest)
        1 * logger.log(_ as String) >> { string ->
            System.out.println(string)
        }
    }

    def "When input contains two Kinesis records, one valid and one that can't be deserialized to a valid Message, then one message is ignored and error is logged and another message is rewritten to the stream"() {
        given:
        def kinesis = Mock(AmazonKinesis)
        def partitionKeyMap = { [:] }
        def lambdaClass = new RehashMetabusMessage(kinesis, partitionKeyMap)

        and:
        def context = Mock(Context)
        def logger = Mock(LambdaLogger)
        context.getLogger() >> logger

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord('noMessageData', 'noPartitionValue'),
                newRecord(JsonOutput.toJson([messageId:'messageId', timestamp:'2007-12-03T10:15:30+01:00', payloadInfo: 'payloadInfo', payload:'{}', tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        1 * kinesis.putRecords( { PutRecordsRequest request -> request.records[0].partitionKey == 'messageId' })
        1 * logger.log(_ as String) >> { string ->
            System.out.println(string)
        }
    }

    def "When input contains a Kinesis record, that is a valid Message, and the partitionKey is mapped for the payloadInfo, then another message is rewritten to the stream with the new partition key"() {
        given:
        def kinesis = Mock(AmazonKinesis)
        def partitionKeyMap = { ['payloadInfo': '$.payloadId'] }
        def lambdaClass = new RehashMetabusMessage(kinesis, partitionKeyMap)

        and:
        def context = Mock(Context)
        def logger = Mock(LambdaLogger)
        context.getLogger() >> logger

        and:
        def inputEvent = new KinesisEvent()
        inputEvent.setRecords([
                newRecord(JsonOutput.toJson([messageId:'messageId', timestamp:'2007-12-03T10:15:30+01:00', payloadInfo: 'payloadInfo', payload:'{"payloadId": "value"}', tags:['tags']]), 'messageId')])

        when:
        lambdaClass.handleRequest(inputEvent, context)

        then:
        1 * kinesis.putRecords( { PutRecordsRequest request -> request.records[0].partitionKey == 'value' })
        0 * logger.log(_ as String)
    }

    private def newRecord(String data, String partitionKey) {
        def kinesisRecord = new KinesisEvent.Record()
        kinesisRecord.setData(ByteBuffer.wrap(data.bytes))
        kinesisRecord.setPartitionKey(partitionKey)

        def kinesisEventRecord = new  KinesisEvent.KinesisEventRecord()
        kinesisEventRecord.setKinesis(kinesisRecord)

        return kinesisEventRecord
    }

}
