package org.keycloak.authentication.authenticators.sms;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.AuthenticationSessionModel;
import org.keycloak.sms.SmsService; // Added import
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger; 

import java.util.Random;

public class SmsAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(SmsAuthenticator.class);
    public static final String SMS_CODE = "smsCode";
    public static final String PHONE_NUMBER = "phoneNumber";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null || !user.isEnabled()) {
            logger.warn("User not found or not enabled.");
            return;
        }

        String phoneNumber = user.getFirstAttribute(PHONE_NUMBER);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            logger.warnf("User %s has no phone number configured.", user.getUsername());
            Response challenge = context.form()
                .setError("missingPhoneNumber")
                .setAttribute("realm", context.getRealm())
                .createForm("sms-validation.ftl");
            context.challenge(challenge);
            return;
        }

        String generatedCode = String.format("%06d", new Random().nextInt(999999));
        logger.infof("Generated SMS code %s for user %s", generatedCode, user.getUsername());

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        authSession.setAuthNote(SMS_CODE, generatedCode);
        authSession.setAuthNote(PHONE_NUMBER, phoneNumber);

        SmsService smsService = context.getSession().getProvider(SmsService.class);
        if (smsService == null) {
            logger.error("SmsService not found or not configured.");
            Response challenge = context.form()
                .setError("smsServiceError") 
                .setAttribute("realm", context.getRealm())
                .createForm("sms-validation.ftl");
            context.challenge(challenge);
            return;
        }
        
        String message = "Your verification code is: " + generatedCode;
        try {
            smsService.send(phoneNumber, message);
        } catch (Exception e) {
            logger.error("Failed to send SMS via SmsService", e);
            Response challenge = context.form()
                .setError("smsServiceError") 
                .setAttribute("realm", context.getRealm())
                .createForm("sms-validation.ftl");
            context.challenge(challenge);
            return;
        }

        Response challenge = context.form()
            .setAttribute("realm", context.getRealm())
            .createForm("sms-validation.ftl");
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String submittedCode = context.getHttpRequest().getDecodedFormParameters().getFirst(SMS_CODE);
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String storedCode = authSession.getAuthNote(SMS_CODE);
        String storedPhoneNumber = authSession.getAuthNote(PHONE_NUMBER);

        if (storedCode == null || submittedCode == null || storedPhoneNumber == null) {
            logger.warnf("Missing SMS code or phone number in session or submission for user %s.", context.getUser() != null ? context.getUser().getUsername() : "unknown");
            Response challenge = context.form()
                .setError("smsAuthFailed")
                .setAttribute("realm", context.getRealm())
                .createForm("sms-validation.ftl");
            context.challenge(challenge);
            return;
        }

        if (submittedCode.equals(storedCode)) {
            logger.infof("SMS code validated successfully for user %s.", context.getUser().getUsername());
            authSession.removeAuthNote(SMS_CODE);
            authSession.removeAuthNote(PHONE_NUMBER);
            context.success();
        } else {
            logger.warnf("Invalid SMS code submitted for user %s.", context.getUser().getUsername());
            Response challenge = context.form()
                .setError("invalidSmsCode")
                .setAttribute("realm", context.getRealm())
                .createForm("sms-validation.ftl");
            context.challenge(challenge);
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        if (user == null || !user.isEnabled()) {
            return false;
        }
        String phoneNumber = user.getFirstAttribute(PHONE_NUMBER);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            logger.debugf("User %s is not configured for SMS auth: missing phone number.", user.getUsername());
            return false;
        }
        
        SmsService smsService = session.getProvider(SmsService.class);
        boolean serviceConfigured = smsService != null;
        if (smsService == null) {
            logger.warn("SmsService not found, SMS authenticator will not be available.");
        }
        
        logger.debugf("User %s configured for SMS auth: %s, service configured: %s", user.getUsername(), !phoneNumber.trim().isEmpty(), serviceConfigured);
        return !phoneNumber.trim().isEmpty() && serviceConfigured;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // Likely empty for now, or could be used to prompt user to add phone number
        // if not already present during some other flow.
    }

    @Override
    public void close() {
        // Likely empty
    }
}
