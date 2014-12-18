package io.github.deton.yoteibot;

public class UnknownBotNickException extends ExchangeAppointmentBotException {
    public UnknownBotNickException() {
        super();
    }

    public UnknownBotNickException(String message) {
        super(message);
    }

    public UnknownBotNickException(Throwable cause) {
        super(cause);
    }
}
