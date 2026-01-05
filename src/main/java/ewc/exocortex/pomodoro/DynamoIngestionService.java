package ewc.exocortex.pomodoro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for ingesting completed session data into DynamoDB.
 * Acts as a buffer/queue for the MCP server.
 */
public class DynamoIngestionService {

    private static final String TABLE_NAME = "PomodoroIngestion";
    private static final String PK = "id";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final DynamoDbClient dynamoDb;

    public DynamoIngestionService(final DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    public static DynamoIngestionService create() {
        return new DynamoIngestionService(DynamoDbClient.create());
    }

    public void ingestSession(final IngestionPayload payload) {
        final Map<String, AttributeValue> item = new HashMap<>();

        // 1. Generate unique ID
        item.put(PK, AttributeValue.builder().s(UUID.randomUUID().toString()).build());

        // 2. Serialize full session payload to JSON
        try {
            String jsonEntry = MAPPER.writeValueAsString(payload);
            item.put("payload", AttributeValue.builder().s(jsonEntry).build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize session data", e);
        }

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
    }
}
