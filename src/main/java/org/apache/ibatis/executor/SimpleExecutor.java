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
package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      stmt = prepareStatement(handler, ms.getStatementLog());
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   * 数据库查询
   * doQuery流程：
   * 1.先创建StatementHandler语句处理器，StatementHandler是mybatis的四大组件之一，负责sql语句的执行。根据xml
   * 配置文件的settings节点的statementType子元素，来创建不同的实现类，如SimpleStatementHandler，PreparedStatementHandler、
   * CallableStatementHandler。它们的基类统一为BaseStatementHandler，外观类为RoutingStatementHandler
   * 2.创建完StatementHandler后，调用prepareStatement进行初始化
   * 3.然后调用实现类的query方法进行查询操作
   *
   *
   * @param ms
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   * @param <E>
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      /**
       * 创建Statementhandler，用来执行sql语句，simpleExecutor创建的是RoutingStatementHandler
       * 它的是一个门面类，几乎所有方法都是通过代理来实现。代理则由配置xml settings节点的StatementType区分。
       * 故仅仅是一个分发和路由。
       */
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      //构造Statement
      stmt = prepareStatement(handler, ms.getStatementLog());
      //通过语句执行器的query方法进行查询，查询结果通过resultHandler处理后返回
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    return Collections.emptyList();
  }

  /**
   * 通过事务构造sql执行语句statement 如jdbcTransaction
   * StatementHandler初始步骤如下：
   * 1.先开启一个数据库连接
   * 2.然后初始化StatementHandler
   * 3.最后进行参数预处理
   *
   * @param handler
   * @param statementLog
   * @return
   * @throws SQLException
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    /*开启数据库连接，创建connection对象，JdbcTransaction事务直接通过jdbc创建connection*/
    Connection connection = getConnection(statementLog);
    /*初始化statement并设置其相关变量，不同的statementHandler实现不同。后面以RoutingStatementHandler为案例分析*/
    stmt = handler.prepare(connection, transaction.getTimeout());
    /*设置parameterHandler，对于simpleStatementHandler来说不用处理*/
    handler.parameterize(stmt);
    return stmt;
  }

}
