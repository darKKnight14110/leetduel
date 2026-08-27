package com.leetduel.matchmaking.exception;

// A broker failure while publishing a join request must surface as an
// honest error, not a silent 202 that lied about the join ever being
// queued - same "never let a crash silently drop a real user action"
// posture the rest of this design is built around.
public class QueuePublishUnavailableException extends RuntimeException {

    public QueuePublishUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
