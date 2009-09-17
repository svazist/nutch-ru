package org.apache.nutch.admin.security;

import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.nutch.admin.security.NutchGuiPrincipal.KnownPrincipal;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.UserRealm;

public class NutchGuiRealm implements UserRealm {

  private final Log LOG = LogFactory.getLog(NutchGuiRealm.class);

  public NutchGuiRealm() {
    System.setProperty("java.security.auth.login.config", System
            .getProperty("user.dir")
            + "/conf/nutchgui.auth");
  }

  @Override
  public Principal authenticate(String userName, Object password,
          HttpRequest request) {
    Principal principal = null;
    try {
      JUserJPasswordCallbackHandler handler = new JUserJPasswordCallbackHandler(
              request);
      LoginContext loginContext = new LoginContext("PropertyFileLogin", handler);
      loginContext.login();
      Subject subject = loginContext.getSubject();
      Set<Principal> principals = subject.getPrincipals();
      principal = principals.isEmpty() ? null : principals.iterator().next();
    } catch (LoginException e) {
      LOG.error("login failed", e);
    }
    return principal;
  }

  @Override
  public void disassociate(Principal principal) {
    // nothing todo
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Principal getPrincipal(String name) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public boolean isUserInRole(Principal principal, String role) {
    boolean bit = false;
    if (principal instanceof KnownPrincipal) {
      KnownPrincipal knownPrincipal = (KnownPrincipal) principal;
      bit = knownPrincipal.isInRole(role);
    }
    return bit;
  }

  @Override
  public void logout(Principal principal) {
    // nothing todo
  }

  @Override
  public Principal popRole(Principal principal) {
    // not necessary
    return principal;
  }

  @Override
  public Principal pushRole(Principal principal, String role) {
    // not necessary
    return principal;
  }

  @Override
  public boolean reauthenticate(Principal principal) {
    return (principal instanceof KnownPrincipal);
  }
}
