/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nutch.protocol.httpclient;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.StringTokenizer;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Handler;

import org.apache.nutch.util.NutchConf;
import org.apache.nutch.util.LogFormatter;
import org.apache.nutch.protocol.ProtocolException;

/**
 * This class handles the parsing of <code>robots.txt</code> files.
 * It emits RobotRules objects, which describe the download permissions
 * as described in RobotRulesParser.
 *
 * @author Tom Pierce
 * @author Mike Cafarella
 * @author Doug Cutting
 */
public class RobotRulesParser {
  public static final Logger LOG=
    LogFormatter.getLogger("org.apache.nutch.fetcher.RobotRulesParser");

  private static final String[] AGENTS = getAgents();
  private static final Hashtable CACHE = new Hashtable();
  
  private static final String CHARACTER_ENCODING= "UTF-8";
  private static final int NO_PRECEDENCE= Integer.MAX_VALUE;
    
  private static final RobotRuleSet EMPTY_RULES= new RobotRuleSet();

  private static RobotRuleSet FORBID_ALL_RULES =
    new RobotRulesParser().getForbidAllRules();

  private HashMap robotNames;

  /**
   * This class holds the rules which were parsed from a robots.txt
   * file, and can test paths against those rules.
   */
  public static class RobotRuleSet {
    ArrayList tmpEntries;
    RobotsEntry[] entries;
    long expireTime;

    /**
     */
    private class RobotsEntry {
      String prefix;
      boolean allowed;

      RobotsEntry(String prefix, boolean allowed) {
        this.prefix= prefix;
        this.allowed= allowed;
      }
    }

    /**
     * should not be instantiated from outside RobotRulesParser
     */
    private RobotRuleSet() {
      tmpEntries= new ArrayList();
      entries= null;
    }

    /**
     */
    private void addPrefix(String prefix, boolean allow) {
      if (tmpEntries == null) {
        tmpEntries= new ArrayList();
        if (entries != null) {
          for (int i= 0; i < entries.length; i++) 
            tmpEntries.add(entries[i]);
        }
        entries= null;
      }

      tmpEntries.add(new RobotsEntry(prefix, allow));
    }

    /**
     */
    private void clearPrefixes() {
      if (tmpEntries == null) {
        tmpEntries= new ArrayList();
        entries= null;
      } else {
        tmpEntries.clear();
      }
    }

    /**
     * Change when the ruleset goes stale.
     */
    public void setExpireTime(long expireTime) {
      this.expireTime = expireTime;
    }

    /**
     * Get expire time
     */
    public long getExpireTime() {
      return expireTime;
    }

    /** 
     *  Returns <code>false</code> if the <code>robots.txt</code> file
     *  prohibits us from accessing the given <code>path</code>, or
     *  <code>true</code> otherwise.
     */ 
    public boolean isAllowed(String path) {
      try {
        path= URLDecoder.decode(path, CHARACTER_ENCODING);
      } catch (Exception e) {
        // just ignore it- we can still try to match 
        // path prefixes
      }

      if (entries == null) {
        entries= new RobotsEntry[tmpEntries.size()];
        entries= (RobotsEntry[]) 
          tmpEntries.toArray(entries);
        tmpEntries= null;
      }

      int pos= 0;
      int end= entries.length;
      while (pos < end) {
        if (path.startsWith(entries[pos].prefix))
          return entries[pos].allowed;
        pos++;
      }

      return true;
    }

    /**
     */
    public String toString() {
      isAllowed("x");  // force String[] representation
      StringBuffer buf= new StringBuffer();
      for (int i= 0; i < entries.length; i++) 
        if (entries[i].allowed)
          buf.append("Allow: " + entries[i].prefix
                     + System.getProperty("line.separator"));
        else 
          buf.append("Disallow: " + entries[i].prefix
                     + System.getProperty("line.separator"));
      return buf.toString();
    }
  }


  public RobotRulesParser() { this(AGENTS); }

  private static String[] getAgents() {
    //
    // Grab the agent names we advertise to robots files.
    //
    String agentName = NutchConf.get().get("http.agent.name");
    String agentNames = NutchConf.get().get("http.robots.agents");
    StringTokenizer tok = new StringTokenizer(agentNames, ",");
    ArrayList agents = new ArrayList();
    while (tok.hasMoreTokens()) {
      agents.add(tok.nextToken().trim());
    }

    //
    // If there are no agents for robots-parsing, use our 
    // default agent-string.  If both are present, our agent-string
    // should be the first one we advertise to robots-parsing.
    // 
    if (agents.size() == 0) {
      agents.add(agentName);
      LOG.severe("No agents listed in 'http.robots.agents' property!");
    } else if (!((String)agents.get(0)).equalsIgnoreCase(agentName)) {
      agents.add(0, agentName);
      LOG.severe("Agent we advertise (" + agentName 
                 + ") not listed first in 'http.robots.agents' property!");
    }

    return (String[])agents.toArray(new String[agents.size()]);
  }


  /**
   *  Creates a new <code>RobotRulesParser</code> which will use the
   *  supplied <code>robotNames</code> when choosing which stanza to
   *  follow in <code>robots.txt</code> files.  Any name in the array
   *  may be matched.  The order of the <code>robotNames</code>
   *  determines the precedence- if many names are matched, only the
   *  rules associated with the robot name having the smallest index
   *  will be used.
   */
  public RobotRulesParser(String[] robotNames) {
    this.robotNames= new HashMap();
    for (int i= 0; i < robotNames.length; i++) {
      this.robotNames.put(robotNames[i].toLowerCase(), new Integer(i));
    }
    // always make sure "*" is included
    if (!this.robotNames.containsKey("*"))
      this.robotNames.put("*", new Integer(robotNames.length));
  }

  /**
   * Returns a {@link RobotRuleSet} object which encapsulates the
   * rules parsed from the supplied <code>robotContent</code>.
   */
  RobotRuleSet parseRules(byte[] robotContent) {
    if (robotContent == null) 
      return EMPTY_RULES;

    String content= new String (robotContent);

    StringTokenizer lineParser= new StringTokenizer(content, "\n\r");

    RobotRuleSet bestRulesSoFar= null;
    int bestPrecedenceSoFar= NO_PRECEDENCE;

    RobotRuleSet currentRules= new RobotRuleSet();
    int currentPrecedence= NO_PRECEDENCE;

    boolean addRules= false;    // in stanza for our robot
    boolean doneAgents= false;  // detect multiple agent lines

    while (lineParser.hasMoreTokens()) {
      String line= lineParser.nextToken();

      // trim out comments and whitespace
      int hashPos= line.indexOf("#");
      if (hashPos >= 0) 
        line= line.substring(0, hashPos);
      line= line.trim();

      if ( (line.length() >= 11) 
           && (line.substring(0, 11).equalsIgnoreCase("User-agent:")) ) {

        if (doneAgents) {
          if (currentPrecedence < bestPrecedenceSoFar) {
            bestPrecedenceSoFar= currentPrecedence;
            bestRulesSoFar= currentRules;
            currentPrecedence= NO_PRECEDENCE;
            currentRules= new RobotRuleSet();
          }
          addRules= false;
        }
        doneAgents= false;

        String agentNames= line.substring(line.indexOf(":") + 1);
        agentNames= agentNames.trim();
        StringTokenizer agentTokenizer= new StringTokenizer(agentNames);

        while (agentTokenizer.hasMoreTokens()) {
          // for each agent listed, see if it's us:
          String agentName= agentTokenizer.nextToken().toLowerCase();

          Integer precedenceInt= (Integer) robotNames.get(agentName);

          if (precedenceInt != null) {
            int precedence= precedenceInt.intValue();
            if ( (precedence < currentPrecedence)
                 && (precedence < bestPrecedenceSoFar) )
              currentPrecedence= precedence;
          }
        }

        if (currentPrecedence < bestPrecedenceSoFar) 
          addRules= true;

      } else if ( (line.length() >= 9)
                  && (line.substring(0, 9).equalsIgnoreCase("Disallow:")) ) {

        doneAgents= true;
        String path= line.substring(line.indexOf(":") + 1);
        path= path.trim();
        try {
          path= URLDecoder.decode(path, CHARACTER_ENCODING);
        } catch (Exception e) {
          LOG.warning("error parsing robots rules- can't decode path: "
                      + path);
        }

        if (path.length() == 0) { // "empty rule"
          if (addRules)
            currentRules.clearPrefixes();
        } else {  // rule with path
          if (addRules)
            currentRules.addPrefix(path, false);
        }

      } else if ( (line.length() >= 6)
                  && (line.substring(0, 6).equalsIgnoreCase("Allow:")) ) {

        doneAgents= true;
        String path= line.substring(line.indexOf(":") + 1);
        path= path.trim();

        if (path.length() == 0) { 
          // "empty rule"- treat same as empty disallow
          if (addRules)
            currentRules.clearPrefixes();
        } else {  // rule with path
          if (addRules)
            currentRules.addPrefix(path, true);
        }
      }
    }

    if (currentPrecedence < bestPrecedenceSoFar) {
      bestPrecedenceSoFar= currentPrecedence;
      bestRulesSoFar= currentRules;
    }

    if (bestPrecedenceSoFar == NO_PRECEDENCE) 
      return EMPTY_RULES;
    return bestRulesSoFar;
  }

  /**
   *  Returns a <code>RobotRuleSet</code> object appropriate for use
   *  when the <code>robots.txt</code> file is empty or missing; all
   *  requests are allowed.
   */
  static RobotRuleSet getEmptyRules() {
    return EMPTY_RULES;
  }

  /**
   *  Returns a <code>RobotRuleSet</code> object appropriate for use
   *  when the <code>robots.txt</code> file is not fetched due to a
   *  <code>403/Forbidden</code> response; all requests are
   *  disallowed.
   */
  static RobotRuleSet getForbidAllRules() {
    RobotRuleSet rules= new RobotRuleSet();
    rules.addPrefix("", false);
    return rules;
  }
  
  public static boolean isAllowed(URL url)
    throws ProtocolException, IOException {

    String host = url.getHost();

    RobotRuleSet robotRules = (RobotRuleSet)CACHE.get(host);

    if (robotRules == null) {                     // cache miss
      LOG.fine("cache miss " + url);
      try {
        HttpResponse response = new HttpResponse(new URL(url, "/robots.txt"));

        if (response.getCode() == 200)               // found rules: parse them
          robotRules = new RobotRulesParser().parseRules(response.getContent());
        else if (response.getCode() == 403)
          robotRules = FORBID_ALL_RULES;            // use forbid all
        else                                        
          robotRules = EMPTY_RULES;                 // use default rules
      } catch (Throwable t) {
        LOG.info("Couldn't get robots.txt for " + url + ": " + t.toString());
        robotRules = EMPTY_RULES;
      }

      CACHE.put(host, robotRules);                // cache rules for host
    }

    String path = url.getPath();                  // check rules
    if ((path == null) || "".equals(path)) {
      path= "/";
    }

    return robotRules.isAllowed(path);
  }

  private final static int BUFSIZE= 2048;

  /** command-line main for testing */
  public static void main(String[] argv) {
    if (argv.length != 3) {
      System.out.println("Usage:");
      System.out.println("   java <robots-file> <url-file> <agent-name>+");
      System.out.println("");
      System.out.println("The <robots-file> will be parsed as a robots.txt file,");
      System.out.println("using the given <agent-name> to select rules.  URLs ");
      System.out.println("will be read (one per line) from <url-file>, and tested");
      System.out.println("against the rules.");
      System.exit(-1);
    }
    try { 
      FileInputStream robotsIn= new FileInputStream(argv[0]);
      LineNumberReader testsIn= new LineNumberReader(new FileReader(argv[1]));
      String[] robotNames= new String[argv.length - 1];

      for (int i= 0; i < argv.length - 2; i++) 
        robotNames[i]= argv[i+2];

      ArrayList bufs= new ArrayList();
      byte[] buf= new byte[BUFSIZE];
      int totBytes= 0;

      int rsize= robotsIn.read(buf);
      while (rsize >= 0) {
        totBytes+= rsize;
        if (rsize != BUFSIZE) {
          byte[] tmp= new byte[rsize];
          System.arraycopy(buf, 0, tmp, 0, rsize);
          bufs.add(tmp);
        } else {
          bufs.add(buf);
          buf= new byte[BUFSIZE];
        }
        rsize= robotsIn.read(buf);
      }

      byte[] robotsBytes= new byte[totBytes];
      int pos= 0;

      for (int i= 0; i < bufs.size(); i++) {
        byte[] currBuf= (byte[]) bufs.get(i);
        int currBufLen= currBuf.length;
        System.arraycopy(currBuf, 0, robotsBytes, pos, currBufLen);
        pos+= currBufLen;
      }

      RobotRulesParser parser= 
        new RobotRulesParser(robotNames);
      RobotRuleSet rules= parser.parseRules(robotsBytes);
      System.out.println("Rules:");
      System.out.println(rules);
      System.out.println();

      String testPath= testsIn.readLine().trim();
      while (testPath != null) {
        System.out.println( (rules.isAllowed(testPath) ? 
                             "allowed" : "not allowed")
                            + ":\t" + testPath);
        testPath= testsIn.readLine();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}