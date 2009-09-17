package org.apache.nutch.admin.security;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.mortbay.http.HttpRequest;

public class JUserJPasswordCallbackHandler implements CallbackHandler {

  private final HttpRequest _httpRequest;

  public JUserJPasswordCallbackHandler(HttpRequest httpRequest) {
    _httpRequest = httpRequest;
  }

  @Override
  public void handle(Callback[] callbacks) throws IOException,
          UnsupportedCallbackException {
    String userName = _httpRequest.getParameter("j_username");
    String password = _httpRequest.getParameter("j_password");
    ((NameCallback) callbacks[0]).setName(userName);
    ((PasswordCallback) callbacks[1]).setPassword(password.toCharArray());
  }

}
