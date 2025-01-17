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

package org.apache.iotdb.db.storageengine.dataregion.compaction;

import org.apache.iotdb.commons.exception.IllegalPathException;
import org.apache.iotdb.commons.exception.MetadataException;
import org.apache.iotdb.commons.path.MeasurementPath;
import org.apache.iotdb.commons.path.PartialPath;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.storageengine.dataregion.compaction.selector.impl.RewriteCrossSpaceCompactionSelector;
import org.apache.iotdb.db.storageengine.dataregion.compaction.selector.utils.CrossCompactionTaskResource;
import org.apache.iotdb.db.storageengine.dataregion.compaction.utils.TsFileGeneratorUtils;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResourceStatus;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.enums.TSDataType;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.TimeRange;
import org.apache.iotdb.tsfile.write.chunk.ChunkWriterImpl;
import org.apache.iotdb.tsfile.write.chunk.IChunkWriter;
import org.apache.iotdb.tsfile.write.writer.TsFileIOWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * If an unsequence file is generated by a load operation or some files in sequence space is
 * deleted, some devices in it may not overlap with any files in sequence space, these devices'
 * startTime may larger than the latest endTime of it in sequence space. When performing a
 * cross-space compaction, the selected sequence file may not contain these devices, causing these
 * devices to be allocated to the first target file when performing the compaction.
 */
public class CrossSpaceCompactionWithUnusualCasesTest extends AbstractCompactionTest {

  @Before
  public void setUp()
      throws IOException, WriteProcessException, MetadataException, InterruptedException {
    super.setUp();
    IoTDBDescriptor.getInstance().getConfig().setEnableTsFileValidation(true);
    IoTDBDescriptor.getInstance().getConfig().setMinCrossCompactionUnseqFileLevel(0);
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(10);
    TSFileDescriptor.getInstance().getConfig().setMaxDegreeOfIndexNode(3);
  }

  @After
  public void tearDown() throws IOException, StorageEngineException {
    super.tearDown();
  }

  @Test
  public void testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles1()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    // selection
    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(3, result.get(0).getTotalFileNums());
  }

  @Test
  public void testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles2()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3
    // device: d2, time: [210, 270]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 210, 270);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(3, result.get(0).getTotalFileNums());
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource2));
    Assert.assertTrue(result.get(0).getSeqFiles().contains(seqTsFileResource3));
  }

  @Test
  public void testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles3()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3
    // device: d2, time: [210, 270]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 210, 270);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // seq file 4
    // device: d3, time: [280, 290]
    TsFileResource seqTsFileResource4 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource4.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource4, "d3", true, 280, 290);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource4.serialize();
    seqResources.add(seqTsFileResource4);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(3, result.get(0).getTotalFileNums());
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource2));
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource4));
    Assert.assertTrue(result.get(0).getSeqFiles().contains(seqTsFileResource3));
  }

  @Test
  public void testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles4()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3
    // device: d2, time: [210, 270]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 210, 270);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // seq file 4
    // device: d3, time: [280, 290]
    TsFileResource seqTsFileResource4 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource4.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource4, "d3", true, 280, 290);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource4.serialize();
    seqResources.add(seqTsFileResource4);

    // seq file 5
    // device: d2, time: [800, 900]
    TsFileResource seqTsFileResource5 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource5.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource5, "d2", true, 800, 900);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource5.serialize();
    seqResources.add(seqTsFileResource5);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(3, result.get(0).getTotalFileNums());
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource2));
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource4));
    Assert.assertTrue(result.get(0).getSeqFiles().contains(seqTsFileResource5));
  }

  @Test
  public void testMultiUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3
    // device: d2, time: [210, 270]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 210, 270);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // seq file 4
    // device: d3, time: [280, 290]
    TsFileResource seqTsFileResource4 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource4.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource4, "d3", true, 280, 290);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource4.serialize();
    seqResources.add(seqTsFileResource4);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    // unSeq file 2
    // device: d1, time: [400, 500]
    // device: d2, time: [500, 600]
    // device: d3, time: [100, 200]
    TsFileResource unSeqTsFileResource2 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource2, "d1", false, 400, 500);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource2, "d2", false, 500, 600);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource2, "d3", false, 100, 200);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource2.serialize();
    unseqResources.add(unSeqTsFileResource2);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(5, result.get(0).getTotalFileNums());
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource2));
  }

  @Test
  public void testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles5()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d3, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d3", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    // selection
    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(2, result.get(0).getTotalFileNums());
  }

  @Test
  public void testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFiles6()
      throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3
    // device: d2, time: [210, 270]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 210, 270);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // seq file 4
    // device: d3, time: [280, 290]
    TsFileResource seqTsFileResource4 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource4.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource4, "d3", true, 280, 290);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource4.serialize();
    seqResources.add(seqTsFileResource4);

    // seq file 5
    // device: d2, time: [500, 600]
    TsFileResource seqTsFileResource5 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource5.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource5, "d2", true, 500, 600);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource5.serialize();
    seqResources.add(seqTsFileResource5);

    // seq file 6
    // device: d2, time: [700, 800]
    TsFileResource seqTsFileResource6 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource6.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource6, "d2", true, 700, 800);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource6.serialize();
    seqResources.add(seqTsFileResource6);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(3, result.get(0).getTotalFileNums());
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource2));
    Assert.assertFalse(result.get(0).getSeqFiles().contains(seqTsFileResource4));
    Assert.assertTrue(result.get(0).getSeqFiles().contains(seqTsFileResource5));
  }

  @Test
  public void
      testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFilesWithUnclosedSeqFile1()
          throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2 (unclosed)
    // device: d2, time: [100, ]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    seqTsFileResource2.updateStartTime(COMPACTION_TEST_SG + ".d2", 100);
    seqTsFileResource2.setStatusForTest(TsFileResourceStatus.UNCLOSED);
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    // selection
    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(0, result.size());
  }

  @Test
  public void
      testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFilesWithUnclosedSeqFile2()
          throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3 (unclosed)
    // device: d2, time: [610, 670]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    seqTsFileResource3.updateStartTime(COMPACTION_TEST_SG + ".d2", 610);
    seqTsFileResource3.setStatusForTest(TsFileResourceStatus.UNCLOSED);

    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(2, result.get(0).getSeqFiles().size());
    Assert.assertEquals(seqTsFileResource2, result.get(0).getSeqFiles().get(1));
  }

  @Test
  public void
      testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFilesWithUnclosedSeqFileAndInvalidCandidate()
          throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2 (invalid)
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqTsFileResource2.setStatus(TsFileResourceStatus.COMPACTION_CANDIDATE);
    seqResources.add(seqTsFileResource2);

    // seq file 3 (unclosed)
    // device: d2, time: [500, ]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    seqTsFileResource3.updateStartTime(COMPACTION_TEST_SG + ".d2", 500);
    seqTsFileResource3.setStatusForTest(TsFileResourceStatus.UNCLOSED);

    seqTsFileResource3.serialize();
    seqResources.add(seqTsFileResource3);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [300, 400]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 300, 400);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(0, result.size());
  }

  @Test
  public void
      testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFilesWithInvalidCandidate1()
          throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3 (invalid)
    // device: d2, time: [500, 600]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 500, 600);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqTsFileResource3.setStatus(TsFileResourceStatus.COMPACTION_CANDIDATE);
    seqResources.add(seqTsFileResource3);

    // seq file 4
    // device: d2, time: [900, 1000]
    TsFileResource seqTsFileResource4 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource4.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource4, "d2", true, 900, 1000);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource4.serialize();
    seqResources.add(seqTsFileResource4);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [700, 800]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 700, 800);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(2, result.get(0).getSeqFiles().size());
    Assert.assertEquals(seqTsFileResource1, result.get(0).getSeqFiles().get(0));
    Assert.assertEquals(seqTsFileResource4, result.get(0).getSeqFiles().get(1));
  }

  @Test
  public void
      testUnSeqFileOverlapWithSeqFilesButOneDeviceNotExistInOverlapSeqFilesWithInvalidCandidate2()
          throws IOException, IllegalPathException {
    // seq file 1
    // device: d1, time: [150, 400]
    TsFileResource seqTsFileResource1 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource1, "d1", true, 150, 400);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource1.serialize();
    seqResources.add(seqTsFileResource1);

    // seq file 2
    // device: d2, time: [100, 200]
    TsFileResource seqTsFileResource2 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource2.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource2, "d2", true, 100, 200);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource2.serialize();
    seqResources.add(seqTsFileResource2);

    // seq file 3 (invalid)
    // device: d2, time: [500, 600]
    TsFileResource seqTsFileResource3 = createEmptyFileAndResource(true);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(seqTsFileResource3.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, seqTsFileResource3, "d2", true, 500, 600);
      tsFileIOWriter.endFile();
    }
    seqTsFileResource3.serialize();
    seqTsFileResource3.setStatus(TsFileResourceStatus.COMPACTION_CANDIDATE);
    seqResources.add(seqTsFileResource3);

    // unSeq file 1
    // device: d1, time: [100, 300]
    // device: d2, time: [700, 800]
    TsFileResource unSeqTsFileResource1 = createEmptyFileAndResource(false, 1);
    try (TsFileIOWriter tsFileIOWriter = new TsFileIOWriter(unSeqTsFileResource1.getTsFile())) {
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d1", false, 100, 300);
      createSimpleDevice(tsFileIOWriter, unSeqTsFileResource1, "d2", false, 700, 800);
      tsFileIOWriter.endFile();
    }
    unSeqTsFileResource1.serialize();
    unseqResources.add(unSeqTsFileResource1);

    tsFileManager.addAll(seqResources, true);
    tsFileManager.addAll(unseqResources, false);
    RewriteCrossSpaceCompactionSelector selector =
        new RewriteCrossSpaceCompactionSelector(COMPACTION_TEST_SG, "0", 0, tsFileManager);

    List<CrossCompactionTaskResource> result =
        selector.selectCrossSpaceTask(seqResources, unseqResources);
    Assert.assertEquals(0, result.size());
  }

  public void createSimpleDevice(
      TsFileIOWriter fileWriter,
      TsFileResource resource,
      String deviceName,
      boolean isSeq,
      long startTime,
      long endTime)
      throws IOException, IllegalPathException {
    String deviceId = COMPACTION_TEST_SG + "." + deviceName;

    fileWriter.startChunkGroup(deviceName);

    List<TSDataType> dataTypes = TsFileGeneratorUtils.createDataType(1);
    List<TSEncoding> encodingTypes = TsFileGeneratorUtils.createEncodingType(1);
    List<CompressionType> compressionTypes = TsFileGeneratorUtils.createCompressionType(1);
    List<PartialPath> timeSeriesPaths = new ArrayList<>();
    timeSeriesPaths.add(new MeasurementPath(deviceId + ".s1", dataTypes.get(0)));

    List<IChunkWriter> chunkWriters =
        TsFileGeneratorUtils.createChunkWriter(
            timeSeriesPaths, dataTypes, encodingTypes, compressionTypes, false);
    IChunkWriter chunkWriter = chunkWriters.get(0);
    List<TimeRange> pages = new ArrayList<>();
    pages.add(new TimeRange(startTime, endTime));
    TsFileGeneratorUtils.writeOneNonAlignedPage((ChunkWriterImpl) chunkWriter, pages, isSeq);

    fileWriter.endChunkGroup();

    resource.updateStartTime(deviceId, startTime);
    resource.updateEndTime(deviceId, endTime);
  }
}
