package ewc.exocortex.pomodoro;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DynamoDB implementation of SessionRepository.
 */
public final class DynamoSessionRepository implements SessionRepository {

    private static final String TABLE_NAME = "PomodoroBotState";
    private static final String PK = "chatId";

    private final DynamoDbClient dynamoDb;

    public DynamoSessionRepository(final DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    /**
     * Creates repository using default DynamoDB client.
     */
    public static DynamoSessionRepository create() {
        return new DynamoSessionRepository(DynamoDbClient.create());
    }

    @Override
    public SessionData getSession(final long chatId) {
        final GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(PK, AttributeValue.builder().n(String.valueOf(chatId)).build()))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return SessionData.idle(chatId);
        }

        final Map<String, AttributeValue> item = response.item();
        return new SessionData(
                chatId,
                SessionState.valueOf(getStringOrNull(item, "status")),
                getStringOrNull(item, "sessionTitle"),
                getIntegerOrNull(item, "duration"),
                getInstantOrNull(item, "startTime"),
                getStringOrNull(item, "scheduleName"));
    }

    @Override
    public void saveSession(final SessionData session) {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put(PK, AttributeValue.builder().n(String.valueOf(session.chatId())).build());
        item.put("status", AttributeValue.builder().s(session.status().name()).build());

        if (session.sessionTitle() != null) {
            item.put("sessionTitle", AttributeValue.builder().s(session.sessionTitle()).build());
        }
        if (session.duration() != null) {
            item.put("duration", AttributeValue.builder().n(String.valueOf(session.duration())).build());
        }
        if (session.startTime() != null) {
            item.put("startTime", AttributeValue.builder().s(session.startTime().toString()).build());
        }
        if (session.scheduleName() != null) {
            item.put("scheduleName", AttributeValue.builder().s(session.scheduleName()).build());
        }

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
    }

    @Override
    public void deleteSession(final long chatId) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(PK, AttributeValue.builder().n(String.valueOf(chatId)).build()))
                .build());
    }

    private String getStringOrNull(final Map<String, AttributeValue> item, final String key) {
        final AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : null;
    }

    private Integer getIntegerOrNull(final Map<String, AttributeValue> item, final String key) {
        final AttributeValue value = item.get(key);
        return value != null && value.n() != null ? Integer.parseInt(value.n()) : null;
    }

    private Instant getInstantOrNull(final Map<String, AttributeValue> item, final String key) {
        final AttributeValue value = item.get(key);
        return value != null && value.s() != null ? Instant.parse(value.s()) : null;
    }
}
