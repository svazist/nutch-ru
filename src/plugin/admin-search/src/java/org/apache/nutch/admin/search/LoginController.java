package org.apache.nutch.admin.search;

import javax.servlet.http.HttpSession;

import org.apache.nutch.admin.NavigationSelector;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class LoginController extends NavigationSelector {

  @RequestMapping(value = "/login.html", method = RequestMethod.GET)
  public String login() {
    return "login";
  }

  @RequestMapping(value = "/logout.html", method = RequestMethod.GET)
  public String logout(HttpSession session) {
    session.invalidate();
    return "redirect:/index.html";
  }

}
