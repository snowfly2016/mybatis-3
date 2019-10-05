/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 */
public class RoutingStatementHandler implements StatementHandler {

  private final StatementHandler delegate;

  /**
   * RoutingStatementHandler的构造器，根据StatementType变量来创建不同的StatementHandler实现，作为它的代理
   * RoutingStatementHandler的几乎所有方法都是通过这些代理实现的，典型的代理模式
   *
   * @param executor
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   */
  public RoutingStatementHandler(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    /*根据StatementType来创建不同的StatementHandler实现类，StatementType是在xml配置文件的settings节点的statementType子元素中设置的*/
    switch (ms.getStatementType()) {
      case STATEMENT:
        /*直接操作sql 不进行预编译，此时直接进行字符串拼接构造sql String*/
        delegate = new SimpleStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      case PREPARED:
        /*默认的类型，预处理，需要进行预编译，可以使用参数替换，会将#转换为？，再设置对应的参数的值*/
        delegate = new PreparedStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      case CALLABLE:
        /*执行存储过程*/
        delegate = new CallableStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      default:
        throw new ExecutorException("Unknown statement type: " + ms.getStatementType());
    }

  }

  /**
   * 初始化StatementHandler
   * @param connection
   * @param transactionTimeout
   * @return
   * @throws SQLException
   */
  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    return delegate.prepare(connection, transactionTimeout);
  }

  /*RoutingStatementHandler 的parameterize方法通过代理模式实现*/
  @Override
  public void parameterize(Statement statement) throws SQLException {
    /*由具体的statementHandler实现类来实现*/
    delegate.parameterize(statement);
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    delegate.batch(statement);
  }

  @Override
  public int update(Statement statement) throws SQLException {
    return delegate.update(statement);
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    return delegate.query(statement, resultHandler);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    return delegate.queryCursor(statement);
  }

  @Override
  public BoundSql getBoundSql() {
    return delegate.getBoundSql();
  }

  @Override
  public ParameterHandler getParameterHandler() {
    return delegate.getParameterHandler();
  }
}
