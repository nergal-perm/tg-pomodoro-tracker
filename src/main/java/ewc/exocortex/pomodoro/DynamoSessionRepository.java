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
                getIntegerOrNull(item, "duration"),
                getStringOrNull(item, "scheduleName"),
                getStringOrNull(item, "task"),
                getStringOrNull(item, "role"),
                getStringOrNull(item, "productType"),
                getInstantOrNull(item, "startTime"),
                getStringOrNull(item, "outcome"));
    }

    @Override
    public void saveSession(final SessionData session) {
        final Map<String, AttributeValue> item = new HashMap<>();
        item.put(PK, AttributeValue.builder().n(String.valueOf(session.chatId())).build());
        item.put("status", AttributeValue.builder().s(session.status().name()).build());

        putIfNotNull(item, "duration", session.duration() != null ? String.valueOf(session.duration()) : null, true);
        putIfNotNull(item, "scheduleName", session.scheduleName(), false);

        putIfNotNull(item, "task", session.task(), false);
        putIfNotNull(item, "role", session.role(), false);
        putIfNotNull(item, "productType", session.productType(), false);

        putIfNotNull(item, "startTime", session.startTime() != null ? session.startTime().toString() : null, false);

        putIfNotNull(item, "outcome", session.outcome(), false);

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

    private void putIfNotNull(final Map<String, AttributeValue> item, final String key, final String value,
            final boolean isNumber) {
        if (value != null) {
            if (isNumber) {
                item.put(key, AttributeValue.builder().n(value).build());
            } else {
                item.put(key, AttributeValue.builder().s(value).build());
            }
        }
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
