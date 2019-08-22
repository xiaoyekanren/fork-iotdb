package org.apache.iotdb.client;

import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.service.rpc.thrift.IoTDBDataType;
import org.apache.iotdb.session.IoTDBRowBatch;
import org.apache.iotdb.session.Session;

public class SessionExample {

  public static void main(String[] args) {
    Session session = new Session("127.0.0.1", 6667, "root", "root");
    session.open();
    List<String> measurements = new ArrayList<>();
    measurements.add("s1");
    measurements.add("s2");
    measurements.add("s3");
    List<IoTDBDataType> dataTypes = new ArrayList<>();
    dataTypes.add(IoTDBDataType.FLOAT);
    dataTypes.add(IoTDBDataType.FLOAT);
    dataTypes.add(IoTDBDataType.FLOAT);

    IoTDBRowBatch rowBatch = new IoTDBRowBatch("root.sg1.d1", measurements, dataTypes);
    for (long i = 1; i <= 100; i++) {
      List<Object> values = new ArrayList<>();
      values.add(1.0f);
      values.add(1.0f);
      values.add(1.0f);
      rowBatch.addRow(i, values);
    }
    session.insertBatch(rowBatch);
    session.close();
  }

}
