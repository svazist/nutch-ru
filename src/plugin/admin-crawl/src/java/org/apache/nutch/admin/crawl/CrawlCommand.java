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

public class CrawlCommand {

  private Integer _depth;

  private Integer _topn;

  private String _crawlFolder;

  public Integer getDepth() {
    return _depth;
  }

  public void setDepth(Integer depth) {
    _depth = depth;
  }

  public Integer getTopn() {
    return _topn;
  }

  public void setTopn(Integer topn) {
    _topn = topn;
  }

  public String getCrawlFolder() {
    return _crawlFolder;
  }

  public void setCrawlFolder(String crawlFolder) {
    _crawlFolder = crawlFolder;
  }

}