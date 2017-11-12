import com.jayway.jsonpath.JsonPath
import spock.lang.Specification

class EnvPartitionKeyMapFactorySpec extends Specification {

    def "when a json map is set in the environment variable PARTITION_KEY_CONFIG, then it produces a map of values" () {
        given:
        def environment = ['PARTITION_KEY_CONFIG': '{"streamName": {"payloadInfo": "\$.jsonpath"}}']

        when:
        def map = new EnvPartitionKeyMapFactory(environment).invoke()

        then:
        map['streamName']['payloadInfo'].path == JsonPath.compile("\$.jsonpath").path
    }
}
