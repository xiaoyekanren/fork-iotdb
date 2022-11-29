# M4-LSM 
- The code of two M4-LSM deployments, M4-UDF and M4-LSM, is available in this repository.
    - M4-UDF: [`org.apache.iotdb.db.query.udf.builtin.UDTFM4MAC`](https://github.com/apache/iotdb/blob/research%2FM4-visualization/server/src/main/java/org/apache/iotdb/db/query/udf/builtin/UDTFM4MAC.java).  
    The document of the M4 aggregation function (implemented as a UDF based on MA) has been released on the official [website](https://iotdb.apache.org/UserGuide/Master/UDF-Library/M4.html#m4-2) of Apache IoTDB.
    - M4-LSM: [`org.apache.iotdb.db.query.dataset.groupby.LocalGroupByExecutor4CPV`](https://github.com/apache/iotdb/blob/research%2FM4-visualization/server/src/main/java/org/apache/iotdb/db/query/dataset/groupby/LocalGroupByExecutor4CPV.java)
    - Some integration tests for correctness are in [`org.apache.iotdb.db.integration.m4.MyTest1~4`](https://github.com/apache/iotdb/blob/research%2FM4-visualization/server/src/test/java/org/apache/iotdb/db/integration/m4/MyTest1.java).
- The experiment-related code, data and scripts are in [another GitHub repository](https://github.com/LeiRui/M4-visualization-exp.git) for easier reproducibility.
- For the README of Apache IoTDB itself, please see [README_IOTDB.md](README_IOTDB.md).
