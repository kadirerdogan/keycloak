package org.keycloak.authentication.authenticators.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.sms.SmsService;
import org.keycloak.http.HttpRequest; // Added import
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SmsAuthenticatorTest {

    @InjectMocks
    private SmsAuthenticator authenticator;

    @Mock
    private AuthenticationFlowContext context;

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private LoginFormsProvider form;
    
    @Mock
    private AuthenticationSessionModel authSession;

    @Mock
    private SmsService smsService;

    @Mock
    private HttpRequest httpRequest;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Captor
    private ArgumentCaptor<Response> responseCaptor;
    
    @Captor
    private ArgumentCaptor<String> authNoteKeyCaptor;

    @Captor
    private ArgumentCaptor<String> authNoteValueCaptor;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.form()).thenReturn(form);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(session.getProvider(SmsService.class)).thenReturn(smsService); // Default happy path for SmsService
    }

    // --- Test methods for authenticate() ---
    @Test
    void authenticate_userNotFound_shouldNotProceed() {
        when(context.getUser()).thenReturn(null);
        authenticator.authenticate(context);
        verify(form, never()).createForm(anyString());
        verify(context, never()).challenge(any(Response.class));
    }

    @Test
    void authenticate_userDisabled_shouldNotProceed() {
        when(user.isEnabled()).thenReturn(false);
        authenticator.authenticate(context);
        verify(form, never()).createForm(anyString());
        verify(context, never()).challenge(any(Response.class));
    }

    @Test
    void authenticate_phoneNumberMissing_shouldChallengeWithMissingPhoneError() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn(null);
        
        authenticator.authenticate(context);
        
        verify(form).setError("missingPhoneNumber");
        verify(form).createForm("sms-validation.ftl");
        verify(context).challenge(any(Response.class));
        verify(authSession, never()).setAuthNote(anyString(), anyString());
        verify(smsService, never()).send(anyString(), anyString());
    }
    
    @Test
    void authenticate_smsServiceMissing_shouldChallengeWithServiceError() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn("+1234567890");
        when(session.getProvider(SmsService.class)).thenReturn(null); // Simulate missing service

        authenticator.authenticate(context);

        verify(form).setError("smsServiceError");
        verify(form).createForm("sms-validation.ftl");
        verify(context).challenge(any(Response.class));
        verify(authSession, never()).setAuthNote(anyString(), anyString());
    }

    @Test
    void authenticate_happyPath_shouldSendCodeAndChallenge() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn("+1234567890");
        // smsService is already mocked and returned by default from session.getProvider

        authenticator.authenticate(context);

        verify(authSession, times(2)).setAuthNote(authNoteKeyCaptor.capture(), authNoteValueCaptor.capture());
        assertEquals(SmsAuthenticator.SMS_CODE, authNoteKeyCaptor.getAllValues().get(0));
        assertNotNull(authNoteValueCaptor.getAllValues().get(0)); // Generated code
        assertEquals(6, authNoteValueCaptor.getAllValues().get(0).length());
        assertEquals(SmsAuthenticator.PHONE_NUMBER, authNoteKeyCaptor.getAllValues().get(1));
        assertEquals("+1234567890", authNoteValueCaptor.getAllValues().get(1));
        
        verify(smsService).send(eq("+1234567890"), stringCaptor.capture());
        assertTrue(stringCaptor.getValue().contains(authNoteValueCaptor.getAllValues().get(0))); // Message contains code

        verify(form).createForm("sms-validation.ftl");
        verify(context).challenge(any(Response.class));
    }
    
    // --- Test methods for action() ---
    @Test
    void action_noStoredCode_shouldChallengeWithFailure() {
        when(authSession.getAuthNote(SmsAuthenticator.SMS_CODE)).thenReturn(null);
        // Simulate form submission
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add(SmsAuthenticator.SMS_CODE, "123456");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        authenticator.action(context);

        verify(form).setError("smsAuthFailed");
        verify(context).challenge(any(Response.class));
        verify(context, never()).success();
    }

    @Test
    void action_noSubmittedCode_shouldChallengeWithFailure() {
        when(authSession.getAuthNote(SmsAuthenticator.SMS_CODE)).thenReturn("123456");
        when(authSession.getAuthNote(SmsAuthenticator.PHONE_NUMBER)).thenReturn("+1234567890");
        // Simulate form submission with no code
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        authenticator.action(context);

        verify(form).setError("smsAuthFailed"); // Or a more specific error like "missingSubmittedCode" if implemented
        verify(context).challenge(any(Response.class));
        verify(context, never()).success();
    }
    
    @Test
    void action_codesMismatch_shouldChallengeWithInvalidCode() {
        when(authSession.getAuthNote(SmsAuthenticator.SMS_CODE)).thenReturn("123456");
        when(authSession.getAuthNote(SmsAuthenticator.PHONE_NUMBER)).thenReturn("+1234567890");
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add(SmsAuthenticator.SMS_CODE, "654321"); // Different code
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        authenticator.action(context);

        verify(form).setError("invalidSmsCode");
        verify(context).challenge(any(Response.class));
        verify(context, never()).success();
    }

    @Test
    void action_codesMatch_shouldSucceedAndClearAuthNotes() {
        String testCode = "123456";
        String testPhone = "+1234567890";
        when(authSession.getAuthNote(SmsAuthenticator.SMS_CODE)).thenReturn(testCode);
        when(authSession.getAuthNote(SmsAuthenticator.PHONE_NUMBER)).thenReturn(testPhone);
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add(SmsAuthenticator.SMS_CODE, testCode); // Correct code
        when(httpRequest.getDecodedFormParameters()).thenReturn(formData);

        authenticator.action(context);

        verify(context).success();
        verify(authSession).removeAuthNote(SmsAuthenticator.SMS_CODE);
        verify(authSession).removeAuthNote(SmsAuthenticator.PHONE_NUMBER);
        verify(context, never()).challenge(any(Response.class));
    }

    // --- Test methods for requiresUser() ---
    @Test
    void requiresUser_shouldReturnTrue() {
        assertTrue(authenticator.requiresUser());
    }

    // --- Test methods for configuredFor() ---
    @Test
    void configuredFor_userIsNull_shouldReturnFalse() {
        assertFalse(authenticator.configuredFor(session, realm, null));
    }

    @Test
    void configuredFor_userDisabled_shouldReturnFalse() {
        when(user.isEnabled()).thenReturn(false);
        assertFalse(authenticator.configuredFor(session, realm, user));
    }

    @Test
    void configuredFor_phoneNumberMissing_shouldReturnFalse() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn(null);
        assertFalse(authenticator.configuredFor(session, realm, user));
    }
    
    @Test
    void configuredFor_phoneNumberBlank_shouldReturnFalse() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn("   "); // Blank
        assertFalse(authenticator.configuredFor(session, realm, user));
    }

    @Test
    void configuredFor_smsServiceMissing_shouldReturnFalse() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn("+1234567890");
        when(session.getProvider(SmsService.class)).thenReturn(null); // Simulate missing service
        assertFalse(authenticator.configuredFor(session, realm, user));
    }

    @Test
    void configuredFor_allConfigured_shouldReturnTrue() {
        when(user.isEnabled()).thenReturn(true);
        when(user.getFirstAttribute(SmsAuthenticator.PHONE_NUMBER)).thenReturn("+1234567890");
        // session.getProvider(SmsService.class) returns smsService (mock) by default from setUp

        assertTrue(authenticator.configuredFor(session, realm, user));
    }
}
