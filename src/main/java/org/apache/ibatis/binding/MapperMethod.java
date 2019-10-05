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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  private final SqlCommand command;
  private final MethodSignature method;

  /**
   * MapperMethodde的构造方法，创建SQLCommand和MethodSignature两个主要成员变量。二者也是动态代理的关键
   * @param mapperInterface
   * @param method
   * @param config
   */
  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    /*创建SqlCommand 包含SQL语句的id和type两个关键字段，type为insert update delete等类型*/
    this.command = new SqlCommand(config, mapperInterface, method);
    /*创建MethodSignature 包含方法的返回值returnType等关键字段*/
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  /*执行mapper接口中的方法*/
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    /*按照SQL语句的不同type，如insert update等，调用SQLSession的insert update等对应的方法，执行数据库操作*/
    switch (command.getType()) {
      case INSERT: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        /*根据select返回值的不同，调用对应的不同的方法来查询，其实本质上是大同小异，最后都是调用了sqlSession的select方法*/
        if (method.returnsVoid() && method.hasResultHandler()) {
          /*无需返回值时*/
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          /*返回值为collection集合类型或者数组类型时*/
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          /*返回值为map类型时*/
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          /*返回值为cursor类型时*/
          result = executeForCursor(sqlSession, args);
        } else {
          /*返回值为其他类型时，认为不是集合类型，调用selectOne返回一个对象*/
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional()
              && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  /*select返回值为集合类型时，如list*/
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    /*调用paramNameResolver处理入参*/
    Object param = method.convertArgsToSqlCommandParam(args);
    /**
     * 调用SQLSession的selectList()方法，查询数据库，是否定义了逻辑分页时，会有些细微的差别
     * selectList方法已经分析
     * 从这里可见，mapper方式最终还是通过SQLSession操作数据库
     * mapper方式比直接操作方式更灵活且不易出错。mybatis建议大家使用mapper方式
     *
     */
    if (method.hasRowBounds()) {
      /*定义了逻辑分页RowBounds时*/
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      /*未定义RowBounds时*/
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    /*由于selectList返回的是list类型的集合，而mapper接口中的方法返回值可能为set queue等其他集合类型，因而此处需要做转换*/
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[])array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

    private final String name;
    private final SqlCommandType type;

    /**
     * SqlCommand构造方法 描述了mapper.xml配置信息中一条sql语句
     * @param configuration
     * @param mapperInterface
     * @param method
     */
    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      /*先获取mapper接口方法的名字和方法声明所在的类，也就是mapper接口类*/
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      /**
       * 拼接mapperInterface和methodName，构成id，从mybatis初始化阶段生成的mappedStatements这个map中获取
       * 当前方法对应的mappedStatement，mybatis初始化讲过mappedStatement它是通过解析mapper.xml生成的，描述
       * 了其中的一条crud语句。mappedStatement是mapper.xml中SQL操作语句的核心，包含SQLSource和boundSQL等重要对象
       *
       */
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      if (ms == null) {
        /*找不到此SQL语句时*/
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        /*找到此SQL语句对应的mappedStatement时，name为SQL语句的id，mapper接口类名.方法名，拼接而成，也就是上面分析的statementId*/
        name = ms.getId();
        /*SQLCommondType为mapper.xml中的SQL语句的标签，如select insert delete等*/
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 从mybatis初始化时创建的mappedStatements中找到本SQL语句对应的MappedStatement
     * @param mapperInterface
     * @param methodName
     * @param declaringClass
     * @param configuration
     * @return
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      /*以mapper接口类名.方法名，作为id，这样可以唯一对应一条SQL操作*/
      /*mapper.xml中namespace对应接口类名，SQL语句id对应mapper接口中方法*/
      String statementId = mapperInterface.getName() + "." + methodName;
      /*本SQL语句在mybatis初始化阶段已经添加了时*/
      if (configuration.hasStatement(statementId)) {
        /*直接从初始化mybatis阶段，生成的mappedStatements总map表中获取当前SQL语句的mappedStatement*/
        return configuration.getMappedStatement(statementId);
      } else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  public static class MethodSignature {

    private final boolean returnsMany;
    private final boolean returnsMap;
    private final boolean returnsVoid;
    private final boolean returnsCursor;
    private final boolean returnsOptional;
    private final Class<?> returnType;
    private final String mapKey;
    private final Integer resultHandlerIndex;
    private final Integer rowBoundsIndex;
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
