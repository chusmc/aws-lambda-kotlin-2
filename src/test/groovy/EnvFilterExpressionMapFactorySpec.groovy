import com.jayway.jsonpath.JsonPath
import org.apache.commons.jexl3.MapContext
import spock.lang.Specification

class EnvFilterExpressionMapFactorySpec extends Specification {

    def "given a json map is set in the environment variable FILTER_MAP_CONFIG, when the key values are valid boolean expressions, it produces an expression map" () {
        given:
        def environment = ['FILTER_MAP_CONFIG': '{"tags.contains(\\"mpm\\") && agent.equals(\\"blue\\")": ["outputstream1", "outputstream2"]}']

        when:
        def map = new EnvFilterExpressionMap(environment).invoke()

        then:
        map.entrySet().iterator().next().key.evaluate(new MapContext([tags:['mpm'], agent:'blue'])) == Boolean.valueOf(true)


    }
}
