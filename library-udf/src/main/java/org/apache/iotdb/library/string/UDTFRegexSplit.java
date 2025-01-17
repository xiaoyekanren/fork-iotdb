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

package org.apache.iotdb.library.string;

import org.apache.iotdb.tsfile.access.Column;
import org.apache.iotdb.tsfile.access.ColumnBuilder;
import org.apache.iotdb.tsfile.utils.Binary;
import org.apache.iotdb.udf.api.UDTF;
import org.apache.iotdb.udf.api.access.Row;
import org.apache.iotdb.udf.api.collector.PointCollector;
import org.apache.iotdb.udf.api.customizer.config.UDTFConfigurations;
import org.apache.iotdb.udf.api.customizer.parameter.UDFParameterValidator;
import org.apache.iotdb.udf.api.customizer.parameter.UDFParameters;
import org.apache.iotdb.udf.api.customizer.strategy.MappableRowByRowAccessStrategy;
import org.apache.iotdb.udf.api.type.Type;

import java.io.IOException;

/** This function splits string from an input series according to given regex. */
public class UDTFRegexSplit implements UDTF {

  private String regex;
  private int index;

  @Override
  public void beforeStart(UDFParameters udfParameters, UDTFConfigurations udtfConfigurations)
      throws Exception {
    regex = udfParameters.getString("regex");
    index = udfParameters.getIntOrDefault("index", -1);
    udtfConfigurations.setAccessStrategy(new MappableRowByRowAccessStrategy());
    if (index == -1) {
      udtfConfigurations.setOutputDataType(Type.INT32);
    } else {
      udtfConfigurations.setOutputDataType(Type.TEXT);
    }
  }

  @Override
  public void transform(Row row, PointCollector collector) throws Exception {
    String[] splitResult = row.getString(0).split(regex);
    if (index == -1) {
      collector.putInt(row.getTime(), splitResult.length);
    } else {
      if (index < splitResult.length) {
        collector.putString(row.getTime(), splitResult[index]);
      }
    }
  }

  @Override
  public Object transform(Row row) throws IOException {
    if (row.isNull(0)) {
      return null;
    }
    String str = row.getString(0);
    String[] splitResult = str.split(regex);

    if (index == -1) {
      return splitResult.length;
    } else {
      if (index < splitResult.length) {
        return splitResult[index];
      }

      return null;
    }
  }

  @Override
  public void transform(Column[] columns, ColumnBuilder builder) throws Exception {
    Binary[] inputs = columns[0].getBinaries();
    boolean[] isNulls = columns[0].isNull();

    int count = columns[0].getPositionCount();

    if (index == -1) {
      for (int i = 0; i < count; i++) {
        if (isNulls[i]) {
          builder.appendNull();
        } else {
          String str = inputs[i].toString();

          String[] splitResult = str.split(regex);
          builder.writeInt(splitResult.length);
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (isNulls[i]) {
          builder.appendNull();
        } else {
          String str = inputs[i].toString();

          String[] splitResult = str.split(regex);
          if (index < splitResult.length) {
            builder.writeBinary(new Binary(splitResult[index].getBytes()));
          } else {
            builder.appendNull();
          }
        }
      }
    }
  }

  @Override
  public void validate(UDFParameterValidator validator) throws Exception {
    validator
        .validateInputSeriesNumber(1)
        .validateInputSeriesDataType(0, Type.TEXT)
        .validate(
            regex -> ((String) regex).length() > 0,
            "regexp has to be a valid regular expression.",
            validator.getParameters().getStringOrDefault("regex", ""))
        .validate(
            index -> (int) index >= -1,
            "index must a non-negative integer to fetch split results or -1 to get length.",
            validator.getParameters().getIntOrDefault("index", -1));
  }
}
