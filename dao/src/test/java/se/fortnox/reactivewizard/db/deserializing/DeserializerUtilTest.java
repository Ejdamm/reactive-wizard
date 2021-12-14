package se.fortnox.reactivewizard.db.deserializing;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.h2.tools.SimpleResultSet;
import org.junit.Test;
import se.fortnox.reactivewizard.test.LoggingMockUtil;

import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static se.fortnox.reactivewizard.test.LoggingMockUtil.createMockedLogAppender;
import static se.fortnox.reactivewizard.test.LoggingMockUtil.destroyMockedAppender;
import static se.fortnox.reactivewizard.test.TestUtil.matches;

public class DeserializerUtilTest {
    @Test
    public void shouldLogWarnIfNoPropertyDeserializerWasFound() throws SQLException {
        SimpleResultSet resultSet = new SimpleResultSet();
        resultSet.addColumn("test_a", Types.VARCHAR, 255, 0);

        Appender mockAppender = createMockedLogAppender(DeserializerUtil.class);
        DeserializerUtil.createPropertyDeserializers(ClassWithoutPropertyA.class, resultSet, (propertyResolver, deserializer) -> deserializer);

        verify(mockAppender).append(matches(log -> {
            assertThat(log.getLevel().toString()).isEqualTo("WARN");
            assertThat(log.getMessage().getFormattedMessage())
                .matches("Tried to deserialize column test_a, but found no matching property named testA in ClassWithoutPropertyA");
        }));
        destroyMockedAppender(DeserializerUtil.class);
    }

    @Test
    public void shouldNotWarnForFoundProperties() throws SQLException {
        SimpleResultSet resultSet = new SimpleResultSet();
        resultSet.addColumn("test_b", Types.VARCHAR, 255, 0);

        Appender mockAppender = createMockedLogAppender(DeserializerUtil.class);

        DeserializerUtil.createPropertyDeserializers(ClassWithoutPropertyA.class, resultSet, (propertyResolver, deserializer) -> deserializer);

        verify(mockAppender, never()).append(any());
        destroyMockedAppender(DeserializerUtil.class);
    }
}

class ClassWithoutPropertyA {
    String testB;
}
