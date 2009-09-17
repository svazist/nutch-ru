package org.apache.nutch.admin.security;

import java.security.Principal;
import java.util.Set;

public class NutchGuiPrincipal {

  public static class AnonymousPrincipal implements Principal {

    @Override
    public String getName() {
      return "Anonymous";
    }

  }

  public static class KnownPrincipal implements Principal {

    private final Set<String> _roles;
    private final String _name;
    private final String _password;

    public KnownPrincipal(String name, String password, Set<String> roles) {
      _name = name;
      _password = password;
      _roles = roles;
    }

    @Override
    public String getName() {
      return _name;
    }

    public String getPassword() {
      return _password;
    }

    public boolean isInRole(String role) {
      return _roles.contains(role);
    }

    @Override
    public int hashCode() {
      return _name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      KnownPrincipal other = (KnownPrincipal) obj;
      return other._name.equals(_name);
    }
  }
}
