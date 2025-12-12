package ewc.exocortex.pomodoro;

import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * AWS EventBridge Scheduler implementation of TimerService.
 */
public final class SchedulerTimerService implements TimerService {

    private static final DateTimeFormatter AT_EXPRESSION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private final SchedulerClient scheduler;
    private final String targetArn;
    private final String roleArn;

    public SchedulerTimerService(final SchedulerClient scheduler,
            final String targetArn,
            final String roleArn) {
        this.scheduler = scheduler;
        this.targetArn = targetArn;
        this.roleArn = roleArn;
    }

    /**
     * Creates service using AWS-provided and custom environment variables.
     * Constructs Lambda ARN from AWS_LAMBDA_FUNCTION_NAME (provided by AWS)
     * and requires SCHEDULER_ROLE_ARN.
     */
    public static SchedulerTimerService fromEnvironment() {
        // AWS provides these automatically
        final String functionName = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        final String region = System.getenv("AWS_REGION");

        // We need to construct the ARN - get account ID from role ARN
        final String roleArn = System.getenv("SCHEDULER_ROLE_ARN");

        if (roleArn == null || roleArn.isBlank()) {
            throw new IllegalStateException("SCHEDULER_ROLE_ARN environment variable is not set");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalStateException("AWS_LAMBDA_FUNCTION_NAME environment variable is not set");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("AWS_REGION environment variable is not set");
        }

        // Extract account ID from role ARN: arn:aws:iam::<account>:role/<name>
        final String accountId = roleArn.split(":")[4];

        // Construct Lambda ARN: arn:aws:lambda:<region>:<account>:function:<name>
        final String lambdaArn = String.format(
                "arn:aws:lambda:%s:%s:function:%s", region, accountId, functionName);

        return new SchedulerTimerService(SchedulerClient.create(), lambdaArn, roleArn);
    }

    @Override
    public String createTimer(final long chatId, final int minutes) {
        final Instant triggerTime = Instant.now().plusSeconds(minutes * 60L);
        final String scheduleName = "pomodoro-" + chatId + "-" + System.currentTimeMillis();

        // Payload sent to Lambda when timer fires
        final String payload = String.format(
                "{\"action\":\"TIMER_DONE\",\"chatId\":%d}", chatId);

        final CreateScheduleRequest request = CreateScheduleRequest.builder()
                .name(scheduleName)
                .scheduleExpression("at(" + AT_EXPRESSION_FORMAT.format(triggerTime) + ")")
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .target(Target.builder()
                        .arn(targetArn)
                        .roleArn(roleArn)
                        .input(payload)
                        .build())
                .actionAfterCompletion(ActionAfterCompletion.DELETE)
                .build();

        scheduler.createSchedule(request);
        return scheduleName;
    }

    @Override
    public void cancelTimer(final String scheduleName) {
        if (scheduleName == null || scheduleName.isBlank()) {
            return;
        }

        try {
            scheduler.deleteSchedule(DeleteScheduleRequest.builder()
                    .name(scheduleName)
                    .build());
        } catch (ResourceNotFoundException e) {
            // Timer already completed or doesn't exist, ignore
        }
    }
}
