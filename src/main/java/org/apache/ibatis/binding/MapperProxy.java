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

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
      | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
  private static final Constructor<Lookup> lookupConstructor;
  private static final Method privateLookupInMethod;
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  static {
    Method privateLookupIn;
    try {
      privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      privateLookupIn = null;
    }
    privateLookupInMethod = privateLookupIn;

    Constructor<Lookup> lookup = null;
    if (privateLookupInMethod == null) {
      // JDK 1.8
      try {
        lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        lookup.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
            e);
      } catch (Throwable t) {
        lookup = null;
      }
    }
    lookupConstructor = lookup;
  }

  /**
   * 调用mapper接口中方法是的入口，Proxy会调用invocationHandler的invoke方法
   * @param proxy JAVA反射的动态代理对象，getMapper()中通过JAVA反射Proxy.newProxyInstance()生成的动态代理
   * @param method 要调用的接口方法
   * @param args 方法入参
   * @return
   * @throws Throwable
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      /*mapper接口类中的抽象方法不会执行这里的if 和else*/
      if (Object.class.equals(method.getDeclaringClass())) {
        /*mapper是一个接口，而非实现类，不会走到这个地方*/
        return method.invoke(this, args);
      } else if (method.isDefault()) {
        /*mapper中方法为抽象方法，也不会走到这儿*/
        if (privateLookupInMethod == null) {
          return invokeDefaultMethodJava8(proxy, method, args);
        } else {
          return invokeDefaultMethodJava9(proxy, method, args);
        }
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
    /*生成mapperMethod，先从cache中取，如果没有则创建一个MapperMethod*/
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    /*执行execute，通过mapperMethod的method方法名，从xml中找到匹配的SQL语句，最终利用SQLSession执行数据库操作*/
    return mapperMethod.execute(sqlSession, args);
  }

  /*生成MapperMethod，先从cache中取，如果没有则创建一个MapperMethod*/
  private MapperMethod cachedMapperMethod(Method method) {
    /**
     * 先从cache中取，未命中，则创建一个MapperMethod
     * 创建MapperMethod用来执行mapper接口的方法
     * mapperInterface 用户定义的mapper接口类
     * method 要执行的的mapper接口中的方法
     * Configuration xml配置信息
     */
    return methodCache.computeIfAbsent(method,
        k -> new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
  }

  private Object invokeDefaultMethodJava9(Object proxy, Method method, Object[] args)
      throws Throwable {
    final Class<?> declaringClass = method.getDeclaringClass();
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup()))
        .findSpecial(declaringClass, method.getName(),
            MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass)
        .bindTo(proxy).invokeWithArguments(args);
  }

  private Object invokeDefaultMethodJava8(Object proxy, Method method, Object[] args)
      throws Throwable {
    final Class<?> declaringClass = method.getDeclaringClass();
    return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass)
        .bindTo(proxy).invokeWithArguments(args);
  }
}
