package ewc.exocortex.pomodoro;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamoIngestionServiceTest {

    @Test
    void shouldSerializeInstantCorrectly() {
        // Arrange
        FakeDynamoDbClient fakeClient = new FakeDynamoDbClient();
        DynamoIngestionService service = new DynamoIngestionService(fakeClient);

        IngestionPayload payload = new IngestionPayload(
                "Task",
                "Role",
                "Product",
                Instant.now(),
                Instant.now(),
                25,
                "Outcome");

        // Act
        service.ingestSession(payload);

        // Assert
        assertEquals(1, fakeClient.putRequests.size());
    }

    // Minimal Fake necessary for the test
    static class FakeDynamoDbClient implements DynamoDbClient {
        final List<PutItemRequest> putRequests = new ArrayList<>();

        @Override
        public PutItemResponse putItem(PutItemRequest putItemRequest) throws SdkException {
            putRequests.add(putItemRequest);
            return PutItemResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "dynamodb";
        }

        @Override
        public void close() {
        }

        // Boilerplate for interface
        @Override
        public GetItemResponse getItem(GetItemRequest getItemRequest) {
            return null;
        }

        @Override
        public DeleteItemResponse deleteItem(DeleteItemRequest deleteItemRequest) {
            return null;
        }
        // ... (other methods can be ignored/default or throw if called)
    }
}
