/**
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
package org.apache.iotdb.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class StatementDemo {

  public static void main(String[] args) throws ClassNotFoundException, SQLException {
    Class.forName("org.apache.iotdb.jdbc.IoTDBDriver");
    Connection connection = null;
    try {
      connection = DriverManager.getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
      Statement statement = connection.createStatement();
      statement.execute("SET STORAGE GROUP TO root.sg1");
      statement.execute("CREATE TIMESERIES root.sg1.d1.s1 WITH DATATYPE=FLOAT, ENCODING=RLE");
      statement.execute("CREATE TIMESERIES root.sg1.d1.s2 WITH DATATYPE=FLOAT, ENCODING=RLE");
      statement.execute("CREATE TIMESERIES root.sg1.d1.s3 WITH DATATYPE=FLOAT, ENCODING=RLE");

      long start = System.currentTimeMillis();
      for (int i = 0; i < 10000; i++) {
        for (int j = 0 ; j < 1000; j++) {
          statement.addBatch("insert into root.sg1.d1(timestamp, s1, s2, s3) values("+ (i * 1000 + j) + "," + 1.0 + "," + 1.0 + "," + 1.0 + ")");
        }
        statement.executeBatch();
        statement.clearBatch();
      }

      System.out.println("cost: " + (System.currentTimeMillis() - start));

//      ResultSet resultSet = statement.executeQuery("select * from root");
//      ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
//      while (resultSet.next()) {
//        StringBuilder builder = new StringBuilder();
//        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
//          builder.append(resultSet.getString(i)).append(",");
//        }
//        System.out.println(builder);
//      }
      statement.close();

    } finally {
      connection.close();
    }
  }
}
