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
package org.apache.iotdb.commons.schema.node.info;

import org.apache.iotdb.commons.schema.node.role.IMeasurementMNode;
import org.apache.iotdb.tsfile.enums.TSDataType;
import org.apache.iotdb.tsfile.write.schema.IMeasurementSchema;

public interface IMeasurementInfo {

  IMeasurementSchema getSchema();

  void setSchema(IMeasurementSchema schema);

  TSDataType getDataType();

  String getAlias();

  void setAlias(String alias);

  long getOffset();

  void setOffset(long offset);

  boolean isPreDeleted();

  void setPreDeleted(boolean preDeleted);

  int estimateSize();

  void moveDataToNewMNode(IMeasurementMNode<?> newMNode);
}
