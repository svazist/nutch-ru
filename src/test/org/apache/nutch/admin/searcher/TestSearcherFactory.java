package org.apache.nutch.admin.searcher;

import java.io.File;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileUtil;
import org.apache.nutch.admin.searcher.MultipleSearcher;
import org.apache.nutch.admin.searcher.SearcherFactory;
import org.apache.nutch.util.NutchConfiguration;

public class TestSearcherFactory extends TestCase {

  private File _folder = new File(System.getProperty("java.io.tmpdir"),
          TestSearcherFactory.class.getName());

  @Override
  protected void setUp() throws Exception {
    assertTrue(_folder.mkdirs());
    assertTrue(new File(_folder, "general").mkdirs());
    // create test crawl folder
    assertTrue(new File(_folder, "testCrawl").mkdirs());
    assertTrue(new File(_folder, "testCrawl/crawls/Crawl-2009.08.17_00.00.00")
            .mkdirs());
    assertTrue(new File(_folder,
            "testCrawl/crawls/Crawl-2009.08.17_00.00.00/search.done")
            .createNewFile());
    assertTrue(new File(_folder, "testCrawl/crawls/Crawl-2009.08.17_00.00.01")
            .mkdirs());
    assertTrue(new File(_folder,
            "testCrawl/crawls/Crawl-2009.08.17_00.00.01/search.done")
            .createNewFile());
    assertTrue(new File(_folder, "testCrawl/crawls/Crawl-2009.08.17_00.00.02")
            .mkdirs());
    // create second test crawl folder
    assertTrue(new File(_folder, "testCrawl2").mkdirs());
    assertTrue(new File(_folder, "testCrawl2/crawls/Crawl-2009.08.17_00.00.00")
            .mkdirs());
    assertTrue(new File(_folder,
            "testCrawl2/crawls/Crawl-2009.08.17_00.00.00/search.done")
            .createNewFile());
    assertTrue(new File(_folder, "testCrawl2/crawls/Crawl-2009.08.17_00.00.01")
            .mkdirs());
    assertTrue(new File(_folder,
            "testCrawl2/crawls/Crawl-2009.08.17_00.00.01/search.done")
            .createNewFile());
    assertTrue(new File(_folder, "testCrawl2/crawls/Crawl-2009.08.17_00.00.02")
            .mkdirs());

  }

  @Override
  protected void tearDown() throws Exception {
    assertTrue(FileUtil.fullyDelete(_folder));
  }

  public void testInstanceCreate() throws Exception {
    Configuration configuration = NutchConfiguration.create();
    configuration.set("nutch.instance.folder", new File(_folder, "testCrawl")
            .getAbsolutePath());
    SearcherFactory instance = SearcherFactory.getInstance(configuration);
    MultipleSearcher searcher = instance.get(false);
    assertEquals(2, searcher.getNutchBeanLength());
  }

  public void testInstanceReloadCreate() throws Exception {
    Configuration configuration = NutchConfiguration.create();
    configuration.set("nutch.instance.folder", new File(_folder, "testCrawl")
            .getAbsolutePath());
    SearcherFactory instance = SearcherFactory.getInstance(configuration);
    MultipleSearcher searcher = instance.get(false);
    assertEquals(2, searcher.getNutchBeanLength());
    assertTrue(new File(_folder,
            "testCrawl/crawls/Crawl-2009.08.17_00.00.02/search.done")
            .createNewFile());
    searcher = instance.get(false);
    assertEquals(2, searcher.getNutchBeanLength());
    searcher = instance.get(true);
    assertEquals(3, searcher.getNutchBeanLength());
  }

  public void testGeneralReloadCreate() throws Exception {
    Configuration configuration = NutchConfiguration.create();
    configuration.set("nutch.instance.folder", new File(_folder, "general")
            .getAbsolutePath());
    SearcherFactory instance = SearcherFactory.getInstance(configuration);
    MultipleSearcher searcher = instance.get(false);
    assertEquals(4, searcher.getNutchBeanLength());
    assertTrue(new File(_folder,
            "testCrawl/crawls/Crawl-2009.08.17_00.00.02/search.done")
            .createNewFile());
    assertTrue(new File(_folder,
            "testCrawl2/crawls/Crawl-2009.08.17_00.00.02/search.done")
            .createNewFile());
    searcher = instance.get(false);
    assertEquals(4, searcher.getNutchBeanLength());
    searcher = instance.get(true);
    assertEquals(6, searcher.getNutchBeanLength());

  }
}
