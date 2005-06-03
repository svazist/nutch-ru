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

package org.apache.nutch.parse.ext;

import org.apache.nutch.protocol.ProtocolFactory;
import org.apache.nutch.protocol.Protocol;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.protocol.ProtocolException;

import org.apache.nutch.parse.ParserFactory;
import org.apache.nutch.parse.Parser;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseException;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** 
 * Unit tests for ExtParser.
 * First creates a temp file with fixed content, then fetch
 * and parse it using external command 'cat' and 'md5sum' alternately
 * for 10 times. Doing so also does a light stress test for class
 * CommandRunner.java (as used in ExtParser.java).
 *
 * Warning: currently only do test on linux platform.
 *
 * @author John Xing
 */
public class TestExtParser extends TestCase {
  private File tempFile = null;
  private String urlString = null;
  private Content content = null;;
  private Parser parser = null;;
  private Parse parse = null;

  private String expectedText = "nutch rocks nutch rocks nutch rocks";
  // echo -n "nutch rocks nutch rocks nutch rocks" | md5sum
  private String expectedMD5sum = "df46711a1a48caafc98b1c3b83aa1526";

  public TestExtParser(String name) { 
    super(name); 
  }

  protected void setUp() throws ProtocolException, IOException {
    // prepare a temp file with expectedText as its content
    // This system property is defined in ./src/plugin/build-plugin.xml
    String path = System.getProperty("test.data");
    if (path != null) {
      File tempDir = new File(path);
      if (!tempDir.exists())
        tempDir.mkdir();
      tempFile = File.createTempFile("nutch.test.plugin.ExtParser.",".txt",tempDir);
    } else {
      // otherwise in java.io.tmpdir
      tempFile = File.createTempFile("nutch.test.plugin.ExtParser.",".txt");
    }
    urlString = tempFile.toURL().toString();

    FileOutputStream fos = new FileOutputStream(tempFile);
    fos.write(expectedText.getBytes());
    fos.close();

    // get nutch content
    Protocol protocol = ProtocolFactory.getProtocol(urlString);
    content = protocol.getProtocolOutput(urlString).getContent();
    protocol = null;
  }

  protected void tearDown() {
    // clean content
    content = null;

    // clean temp file
    //if (tempFile != null && tempFile.exists())
    //  tempFile.delete();
  }

  public void testIt() throws ParseException {
    String contentType;

    // now test only on linux platform
    if (!System.getProperty("os.name").equalsIgnoreCase("linux")) {
      System.err.println("Current OS is "+System.getProperty("os.name")+".");
      System.err.println("No test is run on OS other than linux.");
      return;
    }

    // loop alternately, total 10*2 times of invoking external command
    for (int i=0; i<10; i++) {
      // check external parser that does 'cat'
      contentType = "application/vnd.nutch.example.cat";
      content.setContentType(contentType);
      parser = ParserFactory.getParser(contentType, urlString);
      parse = parser.getParse(content);
      assertEquals(expectedText,parse.getText());

      // check external parser that does 'md5sum'
      contentType = "application/vnd.nutch.example.md5sum";
      content.setContentType(contentType);
      parser = ParserFactory.getParser(contentType, urlString);
      parse = parser.getParse(content);
      assertTrue(parse.getText().startsWith(expectedMD5sum));
    }
  }

}
