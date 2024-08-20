/*
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.subscription.it.triple.regression.pullconsumer.mode;

import org.apache.iotdb.it.framework.IoTDBTestRunner;
import org.apache.iotdb.itbase.category.MultiClusterIT2SubscriptionRegression;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.rpc.subscription.config.TopicConstant;
import org.apache.iotdb.session.subscription.consumer.SubscriptionPullConsumer;
import org.apache.iotdb.subscription.it.triple.regression.AbstractSubscriptionRegressionIT;

import org.apache.thrift.TException;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.enums.CompressionType;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.apache.tsfile.write.schema.MeasurementSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/***
 * PullConsumer
 * pattern: device
 * tsfile
 */
@RunWith(IoTDBTestRunner.class)
@Category({MultiClusterIT2SubscriptionRegression.class})
public class IoTDBSnapshotDevicePullConsumerTsfileIT extends AbstractSubscriptionRegressionIT {
  private static final String database = "root.test.SnapshotDevicePullConsumerTsfile";
  private static final String database2 = "root.SnapshotDevicePullConsumerTsfile";
  private static final String device = database + ".d_0";
  private static final String topicName = "topicSnapshotDevicePullConsumerTsfile";
  private static final String pattern = device + ".**";
  private static SubscriptionPullConsumer consumer;
  private static List<IMeasurementSchema> schemaList = new ArrayList<>();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    createDB(database);
    createDB(database2);
    createTopic_s(topicName, pattern, null, null, true, TopicConstant.MODE_SNAPSHOT_VALUE, null);
    session_src.createTimeseries(
        device + ".s_0", TSDataType.INT64, TSEncoding.GORILLA, CompressionType.LZ4);
    session_src.createTimeseries(
        device + ".s_1", TSDataType.DOUBLE, TSEncoding.TS_2DIFF, CompressionType.LZMA2);
    session_dest.createTimeseries(
        device + ".s_0", TSDataType.INT64, TSEncoding.GORILLA, CompressionType.LZ4);
    session_dest.createTimeseries(
        device + ".s_1", TSDataType.DOUBLE, TSEncoding.TS_2DIFF, CompressionType.LZMA2);
    session_src.executeNonQueryStatement(
        "create aligned timeseries " + database + ".d_1(s_0 int64,s_1 double);");
    session_dest.executeNonQueryStatement(
        "create aligned timeseries " + database + ".d_1(s_0 int64,s_1 double);");
    session_src.executeNonQueryStatement("create timeseries " + database2 + ".d_2.s_0 int32;");
    session_dest.executeNonQueryStatement("create timeseries " + database2 + ".d_2.s_0 int32;");
    session_src.executeNonQueryStatement("create timeseries " + database2 + ".d_2.s_1 double;");
    session_dest.executeNonQueryStatement("create timeseries " + database2 + ".d_2.s_1 double;");
    session_src.executeNonQueryStatement(
        "insert into " + database2 + ".d_2(time,s_0,s_1)values(1000,132,4567.89);");
    session_src.executeNonQueryStatement(
        "insert into " + database + ".d_1(time,s_0,s_1)values(2000,232,567.891);");
    schemaList.add(new MeasurementSchema("s_0", TSDataType.INT64));
    schemaList.add(new MeasurementSchema("s_1", TSDataType.DOUBLE));
    subs.getTopics().forEach((System.out::println));
    assertTrue(subs.getTopic(topicName).isPresent(), "Create show topics");
  }

  @Override
  @After
  public void tearDown() throws Exception {
    try {
      consumer.close();
    } catch (Exception e) {
    }
    subs.dropTopic(topicName);
    dropDB(database);
    dropDB(database2);
    super.tearDown();
  }

  private void insert_data(long timestamp)
      throws IoTDBConnectionException, StatementExecutionException, InterruptedException {
    Tablet tablet = new Tablet(device, schemaList, 5);
    int rowIndex = 0;
    for (int row = 0; row < 5; row++) {
      rowIndex = tablet.rowSize++;
      tablet.addTimestamp(rowIndex, timestamp);
      tablet.addValue("s_0", rowIndex, (row + 1) * 20L + row);
      tablet.addValue("s_1", rowIndex, row + 2.45);
      timestamp += 2000;
    }
    session_src.insertTablet(tablet);
    session_src.executeNonQueryStatement("flush;");
  }

  @Test
  public void do_test()
      throws InterruptedException,
          TException,
          IoTDBConnectionException,
          IOException,
          StatementExecutionException {
    // Write data before subscribing
    insert_data(1706659200000L); // 2024-01-31 08:00:00+08:00

    consumer =
        new SubscriptionPullConsumer.Builder()
            .host(SRC_HOST)
            .port(SRC_PORT)
            .consumerId("device_tsfile_snapshot")
            .consumerGroupId("pull_mode")
            .autoCommit(false)
            .fileSaveDir("target/pull-subscription") // hack for license check
            .buildPullConsumer();
    consumer.open();
    // Subscribe
    consumer.subscribe(topicName);
    subs.getSubscriptions().forEach(System.out::println);
    assertEquals(subs.getSubscriptions().size(), 1, "show subscriptions after subscription");

    insert_data(System.currentTimeMillis());
    System.out.println("src :" + getCount(session_src, "select count(s_0) from " + device));

    // Consumption data
    List<String> devices = new ArrayList<>(3);
    devices.add(device);
    devices.add(database + ".d_1");
    devices.add(database2 + ".d_2");
    List<Integer> rowCounts = consume_tsfile(consumer, devices);

    assertEquals(rowCounts.get(0), 5);
    assertEquals(rowCounts.get(1), 0);
    assertEquals(rowCounts.get(2), 0);

    // Unsubscribe
    consumer.unsubscribe(topicName);
    assertEquals(subs.getSubscriptions().size(), 0, "Show subscriptions after unsubscription");

    // Subscribe
    consumer.subscribe(topicName);
    assertEquals(subs.getSubscriptions().size(), 1, "show subscriptions after re-subscribing");

    // Consumption data: Progress is not retained after unsubscribing and then re-subscribing. Full
    // synchronization.
    rowCounts = consume_tsfile(consumer, devices);
    assertEquals(
        rowCounts.get(0),
        10,
        "Unsubscribe and resubscribe, progress is not retained. Full synchronization.");
    assertEquals(rowCounts.get(1), 0, "Unsubscribe and resubscribe," + database + ".d_1");
    assertEquals(
        rowCounts.get(2), 0, "Cancel subscription and subscribe again," + database2 + ".d_2");
  }
}