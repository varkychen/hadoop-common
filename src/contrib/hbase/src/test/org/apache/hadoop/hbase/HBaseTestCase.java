/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;

/**
 * Abstract base class for test cases. Performs all static initialization
 */
public abstract class HBaseTestCase extends TestCase {
  protected final static String COLFAMILY_NAME1 = "colfamily1:";
  protected final static String COLFAMILY_NAME2 = "colfamily2:";
  protected final static String COLFAMILY_NAME3 = "colfamily3:";
  protected Path testDir = null;
  protected FileSystem localFs = null;
  protected static final char FIRST_CHAR = 'a';
  protected static final char LAST_CHAR = 'z';
  protected static final byte [] START_KEY_BYTES =
    {FIRST_CHAR, FIRST_CHAR, FIRST_CHAR};
  
  static {
    StaticTestEnvironment.initialize();
  }
  
  protected volatile Configuration conf;

  /** constructor */
  public HBaseTestCase() {
    super();
    conf = new HBaseConfiguration();
  }
  
  /**
   * @param name
   */
  public HBaseTestCase(String name) {
    super(name);
    conf = new HBaseConfiguration();
  }
  
  /** {@inheritDoc} */
  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.testDir = getUnitTestdir(getName());
    this.localFs = FileSystem.getLocal(this.conf);
    if (localFs.exists(testDir)) {
      localFs.delete(testDir);
    }
  }
  
  /** {@inheritDoc} */
  @Override
  public void tearDown() throws Exception {
    if (this.localFs != null && this.testDir != null &&
        this.localFs.exists(testDir)) {
      this.localFs.delete(testDir);
    }
    super.tearDown();
  }

  protected Path getUnitTestdir(String testName) {
    return new Path(StaticTestEnvironment.TEST_DIRECTORY_KEY, testName);
  }

  protected HRegion createNewHRegion(Path dir, Configuration c,
    HTableDescriptor desc, long regionId, Text startKey, Text endKey)
  throws IOException {
    HRegionInfo info = new HRegionInfo(regionId, desc, startKey, endKey);
    Path regionDir = HRegion.getRegionDir(dir, info.regionName);
    FileSystem fs = dir.getFileSystem(c);
    fs.mkdirs(regionDir);
    return new HRegion(dir,
      new HLog(fs, new Path(regionDir, HConstants.HREGION_LOGDIR_NAME), conf),
      fs, conf, info, null);
  }
  
  protected HTableDescriptor createTableDescriptor(final String name) {
    HTableDescriptor htd = new HTableDescriptor(name);
    htd.addFamily(new HColumnDescriptor(COLFAMILY_NAME1));
    htd.addFamily(new HColumnDescriptor(COLFAMILY_NAME2));
    htd.addFamily(new HColumnDescriptor(COLFAMILY_NAME3));
    return htd;
  }
  
  /**
   * Add content to region <code>r</code> on the passed column
   * <code>column</code>.
   * Adds data of the from 'aaa', 'aab', etc where key and value are the same.
   * @param r
   * @param column
   * @throws IOException
   */
  protected static void addContent(final HRegion r, final String column)
  throws IOException {
    Text startKey = r.getRegionInfo().getStartKey();
    Text endKey = r.getRegionInfo().getEndKey();
    byte [] startKeyBytes = startKey.getBytes();
    if (startKeyBytes == null || startKeyBytes.length == 0) {
      startKeyBytes = START_KEY_BYTES;
    }
    addContent(new HRegionLoader(r), column, startKeyBytes, endKey, -1);
  }

  /**
   * Add content to region <code>r</code> on the passed column
   * <code>column</code>.
   * Adds data of the from 'aaa', 'aab', etc where key and value are the same.
   * @param updater  An instance of {@link Loader}.
   * @param column
   * @throws IOException
   */
  protected static void addContent(final Loader updater, final String column)
  throws IOException {
    addContent(updater, column, START_KEY_BYTES, null);
  }

  /**
   * Add content to region <code>r</code> on the passed column
   * <code>column</code>.
   * Adds data of the from 'aaa', 'aab', etc where key and value are the same.
   * @param updater  An instance of {@link Loader}.
   * @param column
   * @param startKeyBytes Where to start the rows inserted
   * @param endKey Where to stop inserting rows.
   * @throws IOException
   */
  protected static void addContent(final Loader updater, final String column,
      final byte [] startKeyBytes, final Text endKey)
  throws IOException {
    addContent(updater, column, startKeyBytes, endKey, -1);
  }
  
  /**
   * Add content to region <code>r</code> on the passed column
   * <code>column</code>.
   * Adds data of the from 'aaa', 'aab', etc where key and value are the same.
   * @param updater  An instance of {@link Loader}.
   * @param column
   * @param startKeyBytes Where to start the rows inserted
   * @param endKey Where to stop inserting rows.
   * @param ts Timestamp to write the content with.
   * @throws IOException
   */
  protected static void addContent(final Loader updater, final String column,
      final byte [] startKeyBytes, final Text endKey, final long ts)
  throws IOException {
    // Add rows of three characters.  The first character starts with the
    // 'a' character and runs up to 'z'.  Per first character, we run the
    // second character over same range.  And same for the third so rows
    // (and values) look like this: 'aaa', 'aab', 'aac', etc.
    char secondCharStart = (char)startKeyBytes[1];
    char thirdCharStart = (char)startKeyBytes[2];
    EXIT: for (char c = (char)startKeyBytes[0]; c <= LAST_CHAR; c++) {
      for (char d = secondCharStart; d <= LAST_CHAR; d++) {
        for (char e = thirdCharStart; e <= LAST_CHAR; e++) {
          byte [] bytes = new byte [] {(byte)c, (byte)d, (byte)e};
          Text t = new Text(new String(bytes, HConstants.UTF8_ENCODING));
          if (endKey != null && endKey.getLength() > 0
              && endKey.compareTo(t) <= 0) {
            break EXIT;
          }
          long lockid = updater.startBatchUpdate(t);
          try {
            updater.put(lockid, new Text(column), bytes);
            if (ts == -1) {
              updater.commit(lockid);
            } else {
              updater.commit(lockid, ts);
            }
            lockid = -1;
          } finally {
            if (lockid != -1) {
              updater.abort(lockid);
            }
          }
        }
        // Set start character back to FIRST_CHAR after we've done first loop.
        thirdCharStart = FIRST_CHAR;
      }
      secondCharStart = FIRST_CHAR;
    }
  }
  
  /**
   * Interface used by the addContent methods so either a HTable or a HRegion
   * can be passed to the methods.
   */
  public static interface Loader {
    public long startBatchUpdate(final Text row) throws IOException;
    public void put(long lockid, Text column, byte val[]) throws IOException;
    public void commit(long lockid) throws IOException;
    public void commit(long lockid, long ts) throws IOException;
    public void abort(long lockid) throws IOException;
  }
  
  /**
   * A class that makes a {@link Loader} out of a {@link HRegion}
   */
  public static class HRegionLoader implements Loader {
    final HRegion region;
    public HRegionLoader(final HRegion HRegion) {
      super();
      this.region = HRegion;
    }
    public void abort(long lockid) throws IOException {
      this.region.abort(lockid);
    }
    public void commit(long lockid) throws IOException {
      this.region.commit(lockid, System.currentTimeMillis());
    }
    public void commit(long lockid, final long ts) throws IOException {
      this.region.commit(lockid, ts);
    }
    public void put(long lockid, Text column, byte[] val) throws IOException {
      this.region.put(lockid, column, val);
    }
    public long startBatchUpdate(Text row) throws IOException {
      return this.region.startUpdate(row);
    }
  }

  /**
   * A class that makes a {@link Loader} out of a {@link HTable}
   */
  public static class HTableLoader implements Loader {
    final HTable table;
    public HTableLoader(final HTable table) {
      super();
      this.table = table;
    }
    public void abort(long lockid) throws IOException {
      this.table.abort(lockid);
    }
    public void commit(long lockid) throws IOException {
      this.table.commit(lockid);
    }
    public void commit(long lockid, final long ts) throws IOException {
      this.table.commit(lockid, ts);
    }
    public void put(long lockid, Text column, byte[] val) throws IOException {
      this.table.put(lockid, column, val);
    }
    public long startBatchUpdate(Text row) {
      return this.table.startUpdate(row);
    }
  }
}