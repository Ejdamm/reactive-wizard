package se.fortnox.reactivewizard.test;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.slf4j.impl.Log4jLoggerAdapter;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;

public class LoggingMockUtil {
    private LoggingMockUtil() {

    }

    public static Appender createMockedLogAppender(Class cls) {
        Logger   logger       = LoggingMockUtil.getLogger(cls);
        Appender mockAppender = mock(Appender.class);
        logger.addAppender(mockAppender);
        return mockAppender;
    }

    public static void destroyMockedAppender(Appender appender, Class cls) {
        Logger logger = LoggingMockUtil.getLogger(cls);
        appender.close();
        logger.removeAppender(appender);
    }

    /**
     * This unorthodox reflection magic is needed because the static logger of the ObservableStatementFactory may or may
     * not be initialized with the current LogManager, depending on the tests that have been run before.
     *
     * @return the logger instance used in the class
     */
    static Logger getLogger(Class cls) {
        try {
            Field logField = cls.getDeclaredField("LOG");
            logField.setAccessible(true);
            Log4jLoggerAdapter loggerAdapter = (Log4jLoggerAdapter)logField.get(null);
            Field innerLogField = loggerAdapter.getClass()
                .getDeclaredField("logger");
            innerLogField.setAccessible(true);
            return (Logger)innerLogField.get(loggerAdapter);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
