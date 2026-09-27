package org.svenehrke.triptychdemo.cross.kafka;

/**
 * Thrown by a receiver for a message that can never be processed, however often it is retried
 * (tombstone, broken structure). {@link DeadLetterOrFailStop} sends it to the dead-letter topic;
 * every other exception is treated as transient and stops the channel instead.
 */
public class UnprocessableMessageException extends RuntimeException {

    public UnprocessableMessageException(String message) {
        super(message);
    }
}
