package org.apache.nutch.admin.search;

import junit.framework.TestCase;

public class TestThreadPool extends TestCase {

  public static int _counter = 0;

  public static class CounterRunnable implements Runnable {
    @Override
    public void run() {
      _counter++;
    }
  }

  public void testThreadPool() throws Exception {
    ThreadPool threadPool = new ThreadPool();
    CounterRunnable counterRunnable1 = new CounterRunnable();
    CounterRunnable counterRunnable2 = new CounterRunnable();
    CounterRunnable counterRunnable3 = new CounterRunnable();
    threadPool.execute(counterRunnable1);
    threadPool.execute(counterRunnable2);
    threadPool.execute(counterRunnable3);
    Thread.sleep(500);
    assertEquals(3, _counter);
  }
}
