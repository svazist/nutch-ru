package org.apache.nutch.admin.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.nutch.searcher.NutchBean;

public class SearcherFactory {

  private static Map<String, SearcherFactory> _instances = new HashMap<String, SearcherFactory>();

  private Map<String, MultipleSearcher> _map = new HashMap<String, MultipleSearcher>();

  private final Configuration _configuration;

  public SearcherFactory(Configuration configuration) {
    _configuration = configuration;
  }

  public static SearcherFactory getInstance(Configuration configuration) {
    String instance = configuration.get("nutch.instance.folder");
    if (instance == null) {
      throw new IllegalArgumentException(
              "key 'nutch.instance.folder' is not configured");
    }
    if (!_instances.containsKey(instance)) {
      _instances.put(instance, new SearcherFactory(configuration));
    }
    return _instances.get(instance);
  }

  public MultipleSearcher get(boolean reload) throws IOException {
    String instance = _configuration.get("nutch.instance.folder");
    if (!_map.containsKey(instance) || reload) {
      clearCache(instance);
      Path parent = new Path(instance);
      if (instance.endsWith("/general")) {
        parent = parent.getParent();
      }
      FileSystem fileSystem = FileSystem.get(_configuration);
      List<Path> list = new ArrayList<Path>();
      findActivatedCrawlPaths(fileSystem, parent, list);
      Path[] paths = list.toArray(new Path[list.size()]);
      ThreadPool threadPool = new ThreadPool();
      NutchBean[] beans = new NutchBean[paths.length];
      for (int i = 0; i < beans.length; i++) {
        beans[i] = new NutchBean(_configuration, paths[i]);
      }
      MultipleSearcher searcher = new MultipleSearcher(
              threadPool, beans, beans);
      _map.put(instance, searcher);

    }
    MultipleSearcher searcher = _map.get(instance);
    return searcher;
  }

  private void clearCache(String instance) throws IOException {
    MultipleSearcher cachedSearcher = _map.remove(instance);
    if (cachedSearcher != null) {
      cachedSearcher.close();
      cachedSearcher = null;
    }
  }

  private static void findActivatedCrawlPaths(FileSystem fileSystem,
          Path parent, List<Path> list) throws IOException {
    FileStatus[] status = fileSystem.listStatus(parent);
    for (FileStatus fileStatus : status) {
      Path path = fileStatus.getPath();
      if (fileStatus.isDir()) {
        findActivatedCrawlPaths(fileSystem, path, list);
      } else if (path.getName().equals("search.done")) {
        Path pathToPush = path.getParent();
        list.add(pathToPush);
      }
    }

  }

}
