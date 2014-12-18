package io.github.deton.yoteibot;

public class UnknownUserNickException extends ExchangeAppointmentBotException {
    public UnknownUserNickException() {
        super();
    }

    public UnknownUserNickException(String message) {
        super(message);
    }

    public UnknownUserNickException(Throwable cause) {
        super(cause);
    }
}
