package com.pharmacy.payment.service;

import com.pharmacy.payment.entity.OutboxEvent;
import com.pharmacy.payment.repository.OutboxRepository;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class OutboxDispatcher {

    private static final Logger LOG =
        Logger.getLogger(OutboxDispatcher.class);

    private static final int BATCH_SIZE = 5;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    @Channel("payment-completed-out")
    Emitter<String> completedEmitter;

    @Inject
    @Channel("payment-failed-out")
    Emitter<String> failedEmitter;

    @Scheduled(
        every = "5s",
        delayed = "10s",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    @Transactional
    public void dispatchPending() {
        List<OutboxEvent> events =
            outboxRepository.findUnpublishedForUpdate(BATCH_SIZE);

        for (OutboxEvent event : events) {
            event.attempts++;

            try {
                publish(event);

                event.publishedAt = Instant.now();
                event.lastError = null;

                LOG.infov(
                    "Published Outbox event {0} to topic {1}",
                    event.id,
                    event.topic
                );
            } catch (Exception exception) {
                event.lastError = describeError(exception);

                LOG.errorf(
                    exception,
                    "Cannot publish Outbox event %s to topic %s. Attempt: %d",
                    event.id,
                    event.topic,
                    event.attempts
                );

                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void publish(OutboxEvent event) throws Exception {
        switch (event.topic) {
            case "payment.completed" ->
                completedEmitter.send(event.payload)
                    .toCompletableFuture()
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            case "payment.failed" ->
                failedEmitter.send(event.payload)
                    .toCompletableFuture()
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            default ->
                throw new IllegalArgumentException(
                    "Unsupported Outbox topic: " + event.topic
                );
        }
    }

    private String describeError(Exception exception) {
        Throwable cause = exception;

        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        String message = cause.getClass().getSimpleName()
            + ": "
            + String.valueOf(cause.getMessage());

        return message.length() > 4000
            ? message.substring(0, 4000)
            : message;
    }
}