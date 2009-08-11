/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.admin.crawl;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.nutch.crawl.CrawlTool;

public class PreCrawlRunnable implements Runnable {

  private final CrawlTool _crawlTool;

  private static final Log LOG = LogFactory.getLog(PreCrawlRunnable.class);

  public PreCrawlRunnable(final CrawlTool crawlTool) {
    _crawlTool = crawlTool;
  }

  @Override
  public void run() {
    FileSystem fileSystem = _crawlTool.getFileSystem();
    Path crawlDir = _crawlTool.getCrawlDir();
    Path lockPath = new Path(crawlDir, "crawl.running");
    try {
      fileSystem.createNewFile(lockPath);
      _crawlTool.preCrawl();
    } catch (IOException e) {
      LOG.warn("can not prepare crawl.", e);
    } finally {
      try {
        fileSystem.delete(lockPath, false);
      } catch (IOException e) {
        LOG.warn("can not delete lock file.", e);
      }
    }
  }

}
