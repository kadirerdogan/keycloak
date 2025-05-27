package org.keycloak.sms;

import org.keycloak.provider.Provider;

public interface SmsService extends Provider {
    void send(String phoneNumber, String message);

    default void close() {}
}
