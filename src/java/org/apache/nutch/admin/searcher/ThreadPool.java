package org.apache.nutch.admin.searcher;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class ThreadPool {

  class PooledThread extends Thread {
    @Override
    public void run() {
      while (!isInterrupted()) {
        try {
          // take and remove new runnable, wait's until it is available
          Runnable runnable = _runnables.take();
          runnable.run();
          // put itself, wait's until it is possible
          _threads.put(this);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  };

  private LinkedBlockingQueue<PooledThread> _threads = new LinkedBlockingQueue<PooledThread>(
          100);
  private SynchronousQueue<Runnable> _runnables = new SynchronousQueue<Runnable>();

  public void execute(Runnable runnable) {
    PooledThread thread = _threads.poll();
    if (thread == null) {
      PooledThread pooledThread = new PooledThread();
      pooledThread.start();
    }
    try {
      // put runnable, wait's until a thread is calling take
      _runnables.put(runnable);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void close() {
    for (PooledThread thread : _threads) {
      thread.interrupt();
    }
    _threads.clear();
    _runnables.clear();
  }
}
