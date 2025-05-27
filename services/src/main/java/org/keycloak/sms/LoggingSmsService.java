package org.keycloak.sms;

import org.jboss.logging.Logger;

public class LoggingSmsService implements SmsService {

    private static final Logger logger = Logger.getLogger(LoggingSmsService.class);

    @Override
    public void send(String phoneNumber, String message) {
        logger.infof("Sending SMS to %s: %s", phoneNumber, message);
    }
}
