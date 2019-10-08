package mybatis;

import com.mysql.jdbc.Driver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 原生的JDBC
 *
 * jdbc方式执行SQL语句
 */
public class JDBCTest {

  private final static String EXECUTE_SQL = "select * from user where id=?";

  static {
    try {
      Class.forName(Driver.class.getName());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    Connection root = DriverManager.getConnection("jdbc:mysql://localhost:3306/test?characterEncoding=utf-8", "root", "zyk123456");

    PreparedStatement preparedStatement = root.prepareStatement(EXECUTE_SQL);
    preparedStatement.setString(1,"1");
    preparedStatement.execute();
    preparedStatement.getResultSet();
    ResultSet resultSet = preparedStatement.executeQuery();
    preparedStatement.addBatch();
    while (resultSet.next()) {
      String columnName1 = resultSet.getMetaData().getColumnName(1);
      String columnName2 = resultSet.getMetaData().getColumnName(2);
      System.out.println(columnName1+":"+resultSet.getString(1));
      System.out.println(columnName2+":"+resultSet.getString(2));
    }
    resultSet.close();
    preparedStatement.close();
    root.close();
  }
}
