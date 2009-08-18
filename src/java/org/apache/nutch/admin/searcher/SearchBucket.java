package org.apache.nutch.admin.searcher;

import org.apache.nutch.searcher.Hits;

public class SearchBucket {

  private Hits _hits = new Hits();
  private int _id;

  public SearchBucket() {
  }

  public SearchBucket(int id, Hits hits) {
    _id = id;
    _hits = hits;
  }

  public Hits getHits() {
    return _hits;
  }

  public int getId() {
    return _id;
  }
}
