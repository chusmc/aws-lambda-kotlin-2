import spock.lang.Specification

class EnvPartitionKeyMapFactorySpec extends Specification {

    def "when a json map is set in the environment variable PARTITION_KEY_CONFIG, then it produces a map of values" () {
        given:
        def environment = ['PARTITION_KEY_CONFIG': '{"payloadInfo": "$.jsonpath"}']

        when:
        def map = new EnvPartitionKeyMapFactory(environment).invoke()

        then:
        map['payloadInfo'] == '$.jsonpath'
    }
}
