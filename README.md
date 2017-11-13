# kinesis-stream-multiplexer

The Kinesis stream multiplexer lambda, its a serverless function that filters and rehashes Kinesis events to other Kinesis streams, based on some configuration

![Screenshot](diagram.png)

### Record data format
In order to be able to filter the events, the event data should be a json that contain either a "tags" property with a list of strings, and or an "agent" property.
Also, to be able to map the partitionKey value, a payloadInfo property is expected

```json
{

   "tags": ["tag1", "tag2"],
   "agent" : "agentValue",
   "payloadInfo" : "payloadInfoValue"

}
```

### Filter configuration:
To filter events to different output kinesis streams, you need to provide a Json configuration in an environment variable called FILTER_MAP_CONFIG.
If the message doesn't pass any of the filter expressions, then it won't be written to any stream

For example to get all the messages that contain the tags 'dm' and the agent is 'mpm', to be outputed to streams ouputstream1 and outputstream2:
```bash
FILTER_MAP_CONFIG =
'{
    "tags.contains(\"dm\") && agent.equals(\"mpm\")": ["outputstream1","outputstream2"]
}'
```

For example to get all the messages that contain the tags 'dm' to output stream1, and the tags 'comms' to outputstream2:
```
FILTER_MAP_CONFIG =
'{
    "tags.contains(\"dm\")" : ["outputstream1"]
    "tags.contains(\"comms\")" : ["outputstream2"]
}'
```

### Partition key configuration
To alter the partitionkey we need to provide another configuration map, in an environment variable called PARTITION_KEY_CONFIG
The mapping is based on the streamName and the payloadInfo, and it needs a valid jsonPath expression that access a string value within the record data

For example to use a property called payload.object.id as the partitionKey for outputstream1 when the payloadInfo is com.payload.MyType, we can do:

```
PARTITION_KEY_CONFIG =
'{
    "outputStream1": {"com.payload.MyType": "$.payload.object.id"}
}'
```
If not entry is found for a particular outpustream and payloadInfo, then the message is just written with the same partition key value that it had in the source stream.

### Lambda stack
The lambda stack is defined in infrastructure/lambda.yml.

The environment variables FILTER_MAP_CONFIG and/or PARTITION_KEY_CONFIG should be defined in the Environment section of the LambdaFunction


## Build and Deployment
Build uses gradle build scripts
The AWS pipeline that will build and deploy the lambda, is a created by a cloudformation stack defined in infrastructure/pipeline.yml

### Build with gradle
To build and test the code, you need to run
```bash
$> ./gradlew clean build
```

### Deploy the pipeline using AWS cli
```bash
$> cd infrastructure
$> aws cloudformation create-stack --stack-name midas-lambda-project-pipeline --template-body file://pipeline.yml --capabilities CAPABILITY_IAM --parameters ParameterKey=GitHubUser,ParameterValue=$GITHUB_USER ParameterKey=GitHubOAuthToken,ParameterValue=$GITHUB_OATUTH
```
