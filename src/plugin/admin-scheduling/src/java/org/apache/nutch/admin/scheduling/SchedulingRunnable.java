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
package org.apache.nutch.admin.scheduling;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.nutch.admin.ConfigurationUtil;
import org.apache.nutch.crawl.CrawlTool;

public class SchedulingRunnable implements Runnable {

  private boolean LOCK = false;

  private static final Log LOG = LogFactory.getLog(SchedulingRunnable.class);

  private final CrawlDataPersistence _crawlDataPersistence;

  private DateFormat _format = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss");

  public SchedulingRunnable(CrawlDataPersistence crawlDataPersistence) {
    _crawlDataPersistence = crawlDataPersistence;
  }

  @Override
  public void run() {

    CrawlData crawlData = null;
    try {
      crawlData = _crawlDataPersistence.loadCrawlData();
    } catch (Exception e) {
      LOG.error("can not load crawl data.", e);
      return;
    }
    LOG.info("try to get lock for directory: "
        + crawlData.getWorkingDirectory().getAbsolutePath());
    if (!LOCK) {
      LOG.info("success.");
      LOG.info("lock the scheduled crawl: "
          + crawlData.getWorkingDirectory().getAbsolutePath());
      LOCK = true;
      try {

        File workingDirectory = crawlData.getWorkingDirectory();
        Path path = new Path(workingDirectory.getAbsolutePath(),
            "crawls");
        ConfigurationUtil configurationUtil = new ConfigurationUtil(
            workingDirectory);
        Configuration configuration = configurationUtil
            .loadConfiguration(workingDirectory.getName());
        FileSystem fileSystem = FileSystem.get(configuration);
        String folderName = "Crawl-" + _format.format(new Date());
        Path crawlDir = new Path(path, folderName);
        fileSystem.mkdirs(crawlDir);

        CrawlTool crawlTool = new CrawlTool(configuration, crawlDir);
        crawlTool.preCrawl();
        crawlTool.crawl(crawlData.getTopn(), crawlData.getDepth());
      } catch (Throwable e) {
      } finally {
        LOG.info("unlock the scheduled crawl: "
            + crawlData.getWorkingDirectory().getAbsolutePath());
        LOCK = false;
      }
    } else {
      LOG.info("fails...");
      LOG.info("crawl is locked: "
          + crawlData.getWorkingDirectory().getAbsolutePath());
    }

  }

}
