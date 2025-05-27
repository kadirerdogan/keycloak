package org.keycloak.sms;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderFactory;

import java.util.List;
import java.util.Collections;

public class SmsServiceFactory implements ProviderFactory<SmsService> {

    @Override
    public SmsService create(KeycloakSession session) {
        return new LoggingSmsService();
    }

    @Override
    public void init(Config.Scope config) {
        // Empty for now
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Empty for now
    }

    @Override
    public void close() {
        // Empty for now
    }

    @Override
    public String getId() {
        return "logging-sms-service";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }
}
