package org.keycloak.sms;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class SmsServiceSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "sms-service";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return SmsService.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return SmsServiceFactory.class;
    }
}
