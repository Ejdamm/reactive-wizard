package se.fortnox.reactivewizard.jaxrs;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.Test;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import rx.exceptions.CompositeException;
import rx.exceptions.OnErrorThrowable;
import se.fortnox.reactivewizard.ExceptionHandler;
import se.fortnox.reactivewizard.mocks.MockHttpServerRequest;
import se.fortnox.reactivewizard.mocks.MockHttpServerResponse;

import java.nio.channels.ClosedChannelException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static se.fortnox.reactivewizard.test.TestUtil.matches;

public class ExceptionHandlerTest {

    @Test
    public void shouldLogHeadersForErrors() {
        MockHttpServerRequest request = new MockHttpServerRequest("/path");
        request.requestHeaders().add("X-DBID", "5678");
        String expectedLog = "500 Internal Server Error\n\tCause: runtime exception\n" +
            "\tResponse: {\"id\":\"*\",\"error\":\"internal\"}\n\tRequest: GET /path headers: X-DBID=5678 ";
        assertLog(request, new RuntimeException("runtime exception"), Level.ERROR, expectedLog);
    }

    @Test
    public void shouldLogHeadersForWarnings() {
        MockHttpServerRequest request = new MockHttpServerRequest("/path");
        request.requestHeaders().add("X-DBID", "5678");
        assertLog(request,
            new WebException(HttpResponseStatus.BAD_REQUEST),
            Level.WARN,
            "400 Bad Request\n\tCause: -\n\tResponse: {\"id\":\"*\",\"error\":\"badrequest\"}\n\tRequest: GET /path headers: X-DBID=5678 ");
    }

    @Test
    public void shouldRedactSensitiveHeaders(){
        MockHttpServerRequest request = new MockHttpServerRequest("/path");
        request.requestHeaders()
            .add("Authorization", "secret")
            .add("OtherHeader", "notasecret");
        assertLog(request,
            new WebException(HttpResponseStatus.BAD_REQUEST),
            Level.WARN,
            "400 Bad Request\n\tCause: -\n\tResponse: {\"id\":\"*\",\"error\":\"badrequest\"}\n\tRequest: GET /path headers: Authorization=REDACTED OtherHeader=notasecret ");
    }

    @Test
    public void shouldLog404AsDebug() {
        MockHttpServerRequest request = new MockHttpServerRequest("/path");
        String expectedLog = "404 Not Found\n" +
            "\tCause: -\n" +
            "\tResponse: {\"id\":\"*\",\"error\":\"notfound\"}\n" +
            "\tRequest: GET /path headers: ";

        assertLog(request, new NoSuchFileException(""), Level.DEBUG, expectedLog);
        assertLog(request, new FileSystemException(""), Level.DEBUG, expectedLog);
    }

    @Test
    public void shouldLogClosedChannelExceptionAtDebugLevel() {
        assertLog(new MockHttpServerRequest("/path"),
            new ClosedChannelException(),
            Level.DEBUG,
            "Inbound connection has been closed: GET /path");
    }

    @Test
    public void shouldReturnLastExceptionOfCompositeException() {
        WebException firstException = new WebException(HttpResponseStatus.INTERNAL_SERVER_ERROR).withLogLevel(org.slf4j.event.Level.DEBUG);
        WebException secondException = new WebException(HttpResponseStatus.BAD_GATEWAY).withLogLevel(org.slf4j.event.Level.WARN);

        CompositeException compositeException = new CompositeException(firstException, secondException);

        String expectedLog = "502 Bad Gateway\n" +
            "\tCause: -\n" +
            "\tResponse: {\"id\":\"*\",\"error\":\"badgateway\"}\n" +
            "\tRequest: GET /path headers: ";

        assertLog(new MockHttpServerRequest("/path"), compositeException, Level.WARN, expectedLog);
    }

    @Test
    public void shouldReturnCauseOfOnErrorThrowable() {
        WebException cause = new WebException(HttpResponseStatus.BAD_GATEWAY).withLogLevel(org.slf4j.event.Level.WARN);

        OnErrorThrowable onErrorThrowable = OnErrorThrowable.from(cause);

        String expectedLog = "502 Bad Gateway\n" +
            "\tCause: -\n" +
            "\tResponse: {\"id\":\"*\",\"error\":\"badgateway\"}\n" +
            "\tRequest: GET /path headers: ";

        assertLog(new MockHttpServerRequest("/path"), onErrorThrowable, Level.WARN, expectedLog);
    }

    @Test
    public void shouldSetLogLevelFromWebException() {
        WebException cause = new WebException(HttpResponseStatus.BAD_GATEWAY);

        cause.setLogLevel(org.slf4j.event.Level.WARN);
        assertLog(new MockHttpServerRequest("/path"), cause, Level.WARN, "*");

        cause.setLogLevel(org.slf4j.event.Level.DEBUG);
        assertLog(new MockHttpServerRequest("/path"), cause, Level.DEBUG, "*");

        cause.setLogLevel(org.slf4j.event.Level.ERROR);
        assertLog(new MockHttpServerRequest("/path"), cause, Level.ERROR, "*");

        cause.setLogLevel(org.slf4j.event.Level.TRACE);
        assertLog(new MockHttpServerRequest("/path"), cause, Level.DEBUG, "*");

        cause.setLogLevel(org.slf4j.event.Level.INFO);
        assertLog(new MockHttpServerRequest("/path"), cause, Level.INFO, "*");
    }

    @Test
    public void shouldSetContentLengthZeroFromHEAD() {
        MockHttpServerRequest       request   = new MockHttpServerRequest("/path", HttpMethod.HEAD);
        HttpServerResponse          response  = new MockHttpServerResponse();
        WebException                exception = new WebException(HttpResponseStatus.BAD_GATEWAY);
        new ExceptionHandler().handleException(request, response, exception);

        assertThat(response.responseHeaders().get("Content-Length")).isEqualTo("0");
    }

    private void assertLog(HttpServerRequest request, Exception exception, Level expectedLevel, String expectedLog) {
        Appender mockAppender = mock(Appender.class);
        LogManager.getLogger(ExceptionHandler.class).addAppender(mockAppender);
        LogManager.getLogger(ExceptionHandler.class).setLevel(Level.DEBUG);

        HttpServerResponse response = new MockHttpServerResponse();
        new ExceptionHandler().handleException(request, response, exception);

        String regex = expectedLog.replaceAll("\\*", ".*")
            .replaceAll("\\[", "\\\\[")
            .replaceAll("\\]", "\\\\]")
            .replaceAll("\\{", "\\\\{")
            .replaceAll("\\}", "\\\\}");

        verify(mockAppender).doAppend(matches(event -> {
            assertThat(event.getLevel()).isEqualTo(expectedLevel);
            if (!expectedLog.equals("*")) {
                assertThat(event.getMessage().toString()).matches(regex);
            }
        }));
    }
}
