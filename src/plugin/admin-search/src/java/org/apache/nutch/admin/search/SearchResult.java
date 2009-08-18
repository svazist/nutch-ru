package org.apache.nutch.admin.search;

public class SearchResult {

  private String _url;
  private String _title;
  private String _summary;

  public SearchResult(String url, String title, String summary) {
    _url = url;
    _title = title;
    _summary = summary;
  }

  public String getUrl() {
    return _url;
  }

  public void setUrl(String url) {
    _url = url;
  }

  public String getTitle() {
    return _title;
  }

  public void setTitle(String title) {
    _title = title;
  }

  public String getSummary() {
    return _summary;
  }

  public void setSummary(String summary) {
    _summary = summary;
  }

}
