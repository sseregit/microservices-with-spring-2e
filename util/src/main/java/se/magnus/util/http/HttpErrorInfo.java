package se.magnus.util.http;

import org.springframework.http.HttpStatus;

import java.time.ZonedDateTime;

public class HttpErrorInfo {
    private final ZonedDateTime timestamp;
    private final HttpStatus httpStatus;
    private final String path;
    private final String message;

    public HttpErrorInfo() {
        timestamp = null;
        httpStatus = null;
        path = null;
        message = null;
    }

    public HttpErrorInfo(HttpStatus httpStatus, String path, String message) {
        this.timestamp = ZonedDateTime.now();
        this.httpStatus = httpStatus;
        this.path = path;
        this.message = message;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }
}
