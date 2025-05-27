package org.keycloak.sms;

import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

class LoggingSmsServiceTest {

    // Option 1: Test that it calls the logger (requires more advanced mocking or a test appender setup for JBoss Logging)
    // For simplicity in this subtask, we will focus on ensuring the method runs without error
    // and that a logger instance is present, rather than capturing exact log output.

    @Test
    void send_shouldLogMessageWithoutError() {
        LoggingSmsService service = new LoggingSmsService();
        String phoneNumber = "+1234567890";
        String message = "Test SMS message";

        // Ensure the method can be called without throwing an exception
        assertDoesNotThrow(() -> service.send(phoneNumber, message));
        
        // Basic check: Verify a logger instance exists (though not its output)
        try {
            Field loggerField = LoggingSmsService.class.getDeclaredField("logger");
            loggerField.setAccessible(true);
            Logger loggerInstance = (Logger) loggerField.get(null); // static field
            assertNotNull(loggerInstance, "Logger instance should not be null");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not access logger field for verification", e);
        }
    }

    @Test
    void send_withNullPhoneNumber_shouldLogMessageWithoutError() {
        LoggingSmsService service = new LoggingSmsService();
        String message = "Test SMS message with null phone";
        assertDoesNotThrow(() -> service.send(null, message));
    }

    @Test
    void send_withNullMessage_shouldLogMessageWithoutError() {
        LoggingSmsService service = new LoggingSmsService();
        String phoneNumber = "+1234567890";
        assertDoesNotThrow(() -> service.send(phoneNumber, null));
    }
}
