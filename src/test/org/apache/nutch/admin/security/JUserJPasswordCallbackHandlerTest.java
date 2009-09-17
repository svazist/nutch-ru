package org.apache.nutch.admin.security;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mortbay.http.HttpRequest;

public class JUserJPasswordCallbackHandlerTest extends TestCase {

  @Mock
  private HttpRequest _httpRequest;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  public void testCallback() throws Exception {
    Mockito.when(_httpRequest.getParameter("j_username")).thenReturn("foo");
    Mockito.when(_httpRequest.getParameter("j_password")).thenReturn("bar");

    CallbackHandler callbackHandler = new JUserJPasswordCallbackHandler(
            _httpRequest);
    Callback[] callbacks = new Callback[2];
    callbacks[0] = new NameCallback("user name:");
    callbacks[1] = new PasswordCallback("password:", false);
    callbackHandler.handle(callbacks);
    assertEquals("foo", ((NameCallback) callbacks[0]).getName());
    assertEquals("bar", new String(((PasswordCallback) callbacks[1])
            .getPassword()));

  }
}
