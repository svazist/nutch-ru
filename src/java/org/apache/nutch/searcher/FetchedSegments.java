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

package org.apache.nutch.searcher;

import java.io.IOException;
import java.io.File;

import java.util.HashMap;
import java.util.Arrays;

import org.apache.nutch.io.*;
import org.apache.nutch.fs.*;
import org.apache.nutch.db.*;
import org.apache.nutch.util.*;
import org.apache.nutch.fetcher.*;
import org.apache.nutch.protocol.*;
import org.apache.nutch.parse.*;
import org.apache.nutch.pagedb.*;
import org.apache.nutch.indexer.*;

/** Implements {@link HitSummarizer} and {@link HitContent} for a set of
 * fetched segments. */
public class FetchedSegments implements HitSummarizer, HitContent {

  private static class Segment {
    private NutchFileSystem nfs;
    private File segmentDir;

    private MapFile.Reader[] content;
    private MapFile.Reader[] parseText;
    private MapFile.Reader[] parseData;

    public Segment(NutchFileSystem nfs, File segmentDir) throws IOException {
      this.nfs = nfs;
      this.segmentDir = segmentDir;
    }

    public FetcherOutput getFetcherOutput(UTF8 url) throws IOException {
      throw new UnsupportedOperationException();
    }

    public byte[] getContent(UTF8 url) throws IOException {
      synchronized (this) {
        if (content == null)
          content = getReaders(Content.DIR_NAME);
      }
      return ((Content)getEntry(content, url, new Content())).getContent();
    }

    public ParseData getParseData(UTF8 url) throws IOException {
      synchronized (this) {
        if (content == null)
          content = getReaders(ParseData.DIR_NAME);
      }
      return (ParseData)getEntry(content, url, new ParseData());
    }

    public ParseText getParseText(UTF8 url) throws IOException {
      synchronized (this) {
        if (content == null)
          content = getReaders(ParseText.DIR_NAME);
      }
      return (ParseText)getEntry(content, url, new ParseText());
    }
    
    private MapFile.Reader[] getReaders(String subDir) throws IOException {
      File[] names = nfs.listFiles(new File(segmentDir, subDir));
      
      // sort names, so that hash partitioning works
      Arrays.sort(names);

      MapFile.Reader[] parts = new MapFile.Reader[names.length];
      for (int i = 0; i < names.length; i++) {
        parts[i] = new MapFile.Reader(nfs, names[i].toString());
      }
      return parts;
    }

    // hash the url to figure out which part its in
    private Writable getEntry(MapFile.Reader[] readers, UTF8 url,
                              Writable entry) throws IOException {
      return readers[url.hashCode()%readers.length].get(url, entry);
    }

  }

  private HashMap segments = new HashMap();

  /** Construct given a directory containing fetcher output. */
  public FetchedSegments(NutchFileSystem nfs, String segmentsDir) throws IOException {
    File[] segmentDirs = nfs.listFiles(new File(segmentsDir));

    if (segmentDirs != null) {
        for (int i = 0; i < segmentDirs.length; i++) {
            File segmentDir = segmentDirs[i];
//             File indexdone = new File(segmentDir, IndexSegment.DONE_NAME);
//             if (nfs.exists(indexdone) && nfs.isFile(indexdone)) {
//             	segments.put(segmentDir.getName(), new Segment(nfs, segmentDir));
//             }
            segments.put(segmentDir.getName(), new Segment(nfs, segmentDir));

        }
    }
  }

  public String[] getSegmentNames() {
    return (String[])segments.keySet().toArray(new String[segments.size()]);
  }

  public byte[] getContent(HitDetails details) throws IOException {
    return getSegment(details).getContent(getUrl(details));
  }

  public ParseData getParseData(HitDetails details) throws IOException {
    return getSegment(details).getParseData(getUrl(details));
  }

  public String[] getAnchors(HitDetails details) throws IOException {
    return getSegment(details).getFetcherOutput(getUrl(details))
      .getFetchListEntry().getAnchors();
  }

  public long getFetchDate(HitDetails details) throws IOException {
    return getSegment(details).getFetcherOutput(getUrl(details))
      .getFetchDate();
  }

  public ParseText getParseText(HitDetails details) throws IOException {
    return getSegment(details).getParseText(getUrl(details));
  }

  public String getSummary(HitDetails details, Query query)
    throws IOException {

    String text = getSegment(details).getParseText(getUrl(details)).getText();

    return new Summarizer().getSummary(text, query).toString();
  }
    
  private class SummaryThread extends Thread {
    private HitDetails details;
    private Query query;

    private String summary;
    private Throwable throwable;

    public SummaryThread(HitDetails details, Query query) {
      this.details = details;
      this.query = query;
    }

    public void run() {
      try {
        this.summary = getSummary(details, query);
      } catch (Throwable throwable) {
        this.throwable = throwable;
      }
    }

  }


  public String[] getSummary(HitDetails[] details, Query query)
    throws IOException {
    SummaryThread[] threads = new SummaryThread[details.length];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new SummaryThread(details[i], query);
      threads[i].start();
    }

    String[] results = new String[details.length];
    for (int i = 0; i < threads.length; i++) {
      try {
        threads[i].join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (threads[i].throwable instanceof IOException) {
        throw (IOException)threads[i].throwable;
      } else if (threads[i].throwable != null) {
        throw new RuntimeException(threads[i].throwable);
      }
      results[i] = threads[i].summary;
    }
    return results;
  }


  private Segment getSegment(HitDetails details) {
    return (Segment)segments.get(details.getValue("segment"));
  }

  private UTF8 getUrl(HitDetails details) {
    return new UTF8(details.getValue("url"));
  }


}
