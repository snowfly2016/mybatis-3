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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  /**
   * new XMLConfigBuilder()过程
   *
   * 读取全局xml配置文件，解析文件节点，生成xNode对象
   *
   * @param inputStream
   * @param environment
   * @param props
   */
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    //利用xml配置文件输入流，创建XPathParser解析对象
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //先创建Configuration实例对象，也会做一些基本的初始化，主要是注册一些别名供后面解析xml用，比如JDBC、CGLIB...
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    /**
     * 将new SqlSessionFactoryBuilder().build()中传入的properties键值对，设置到configuration对象的variables变量中，
     * 它会和后面解析properties子节点得到的键值对做合并。主要的作用就是配置常量动态化
     */
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   *
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //利用parser，创建xNode对象
    //parseConfiguration将解析后的键值对设置到Configuration实例相关变中
    //解析xml，生成xNode链式节点的过程就在parser.evalNode("/configuration")，它通过Apache的XPath解析器，读取并解析各个节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析由xml生成的xNode对象，生成Configuration对象
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      //解析properties节点，存放到Configuration对象的variables变量中，用来将配置动态化
      //比如配置datasourece的username/password
      propertiesElement(root.evalNode("properties"));
      //解析settings节点，会改写configuration中的相关值
      //这些值决定了mybatis的运行方式，如CacheEnable、LazyLoadEnabled等属性
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      //设置日志
      loadCustomLogImpl(settings);
      //解析typeAliases节点，定义别名，一般用来为Java权利路径类型取一个比较短的名字
      typeAliasesElement(root.evalNode("typeAliases"));
      //解析plugin节点，定义插件，用来拦截某些类，从而改变这类的执行
      pluginElement(root.evalNode("plugins"));
      //解析objectFactory节点，定义对象工厂，不常用。对象工厂用来创建mybatis返回的结果对象
      objectFactoryElement(root.evalNode("objectFactory"));
      //解析objectWrapperFactory节点，包装Object实例，不常用
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //解析reflectorFactory节点，创建Reflector类反射，不常用
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      //将解析并读取的settings节点后得到的键值对，设置到Configuration实例的相关变量中
      //这些键值对决定了mybatis的运行方式，如果没有设置，则采用默认值
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      //解析environments节点，定义数据库环境
      //可以配置多个environment，每个对应一个dataSource和transactionManager
      environmentsElement(root.evalNode("environments"));
      //解析databaseIdProvider节点，定义数据库厂商标识
      //mybatis可以根据不同的厂商执行不同的语句
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //解析typeHandlers接点，定义类型处理器，用来将数据库中获取的值转化为Java类型
      typeHandlerElement(root.evalNode("typeHandlers"));
      //解析mappers节点，定义映射器，也就是SQL映射语句，mappers中定义好映射文件的位置即可
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 读取并解析settings元素，生成properties键值对
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 获取用户自定义的vf的实现，配置在settings元素中
   * settings中放置自定义vfs实现类的全限定名，以逗号分隔
   * VFS是mybatis提供的用于访问AS内资源的一个简便接口
   * @param props
   * @throws ClassNotFoundException
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      //vfs实现类可以有多个，其实现类全限定名以逗号隔开
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          //反射加载自定义vfs实现，并设置到configuration实例中
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 读取并解析typeAliases元素，并设置到Configuration的typeAliasesRegistry中
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //元素为package时，mybatis会搜索包名下需要的JAVA bean。使用bean的首字母小写的非限定名来作为他的别名
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //元素为typeAliases时，读取单个typeAliases的alias和type元素，他们分别是别名和原名
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            /**
             * 反射加载type对应的原类型，然后以alias作为key，class对象作为value放入typeAliases这个Map中
             * 这样使用到别名的时候就可以使用真实的class类来替换
             * 这个Map定义了很多默认的别名映射，如果String等
             *
             */
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 读取并解析plugin元素，并添加到Configuration的InterceptorChain中
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      //遍历plugin中的每一个plugin
      for (XNode child : parent.getChildren()) {
        //读取plugin中的interceptor属性，它声明了插件的实现类的全限定名
        String interceptor = child.getStringAttribute("interceptor");
        //读取plugin中的properties元素，它声明了插件类的构造参数
        Properties properties = child.getChildrenAsProperties();
        //有了实现类的全限定名和构造参数后，就可以反射创建插件对象实例了，并初始化它
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        interceptorInstance.setProperties(properties);
        //将创建并初始化好的插件对象添加到Configuration对象中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 读取并解析properties节点，合并到configuration的variables变量中
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();
      //获取properties中的resource或者url属性，二者必须定义一个，否则定义有误，直接抛出异常
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      //从resource或者url中读取资源，生成Properties对象。
      //Properties是一个hashtable，保存的<key,value>的键值对
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //将Configuration本来就存在的Hashtable,variables变量，和从resource或者url中加载的hashtable合并，更新到variables变量中
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * 将解析并读取settings节点后得到的键值对，设置到Configuration实例的相关变量中
   *
   * @param props
   */
  private void settingsElement(Properties props) {
    //设置Configuration的autoMappingBehavior变量，指定mybatis应如何自动映射数据库列到POJO对象属性
    //NONE表示取消自动映射，PARTIAL表示只映射非嵌套的结果集，FULL表示映射所有结果集，即使有嵌套
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    //设置AutoMappingUnknownColumnBehavior，mybatis自动映射时对未定义列如何处理
    //NONE则不做任何处理，WARNING输出提醒日志，FAILIN映射失败，抛出SqlSessionException
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    //设置cacheEnabled，是否开启二级缓存，也是就SqlSessionFactory级别的缓存。一级缓存即SqlSession内的HashMap，是默认开启的；
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    //设置ProxyFactory，指定mybatis创建延迟加载对象所用到的动态代理工具，可以CGLIB或JAVASSIST
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    //设置LazyLoadingEnabled，延迟加载，开启式所有关联对象都会延迟加载，除非关联对象中设置了fetchType类覆盖它
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    //设置AggressiveLazyLoading，开启时，调用对象内任何方法都会加载对象内所有属性，默认为false，即为按需加载
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    //设置MultipleResultSetsEnabled，开启时允许单一语句返回多结果
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    //设置UseColumnLabel，使用列标签代替列名
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    //设置UseGeneratedKeys，允许jdbc自动生成主键
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    //设置DefaultExecutorType，默认执行器
    //simple 普通执行器 reuse 重用预处理语句 batch 批量更新
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    //设置DefaultStatementTimeout 超时时间，也就是数据库驱动等待数据库响应的时间，单位秒
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    //设置DefaultFetchSize，每次返回的数据库结果集的行数，使用它可以避免内存溢出
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    //设置DefaultResultSetType
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    //设置MapUnderscoreToCamelCase，开启自动驼峰命名规则映射，即将数据库列名xxx_column映射为java属性xxxColumn
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    //设置SafeRowBoundsEnabled，允许在嵌套语句中使用分页RowBounds
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    //设置LocalCacheScope，本地缓存的作用域，本地缓存用来加速嵌套查询和防止循环引用
    //session 则缓存为sqlSession中的所有查询语句，statement 则相同sqlSession的同一个调用语句才做缓存
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    //设置JdbcTypeForNull 没有为参数提供特定的jdbc类型，JAVA null对应的jdbc类型
    //可为 null varchar other
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    //设置LazyLoadTriggerMethods 指定哪些方法会触发延迟加载关联对象，方法名之间用逗号隔开
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    //设置SafeResultHandlerEnabled 允许在嵌套语句中使用分页 ResultHandler
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    //设置DefaultScriptingLanguage 指定用来生成动态SQL语句的默认语言
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    //设置DefaultEnumTypeHandler 指定enum对应的默认的TypeHandler，它是一个Java全限定名，
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    //设置CallSettersOnNulls 指定结果集中null值是否调用JAVA映射对象的setter
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    //设置UseActualParamName 允许使用方法签名中的形参名作为sql语句的参数名称，使用这个属性时，工程必须支持JAVA8
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    //设置ReturnInstanceForEmptyRow 当某一行的所有列为空时，mybatis返回一个null实例对象
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    //设置LogPrefix 指定mybatis添加到日志中的前缀，设置后每一条日志都会添加这个前缀
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    //设置ConfigurationFactory 指定生成Configuration对象的工厂类，工厂类必须包含getConfiguration（）方法
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 读取并解析environments元素，并设置到Configuration实例中
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        //environment为SqlSessionFactoryBuilder().build()时传入的String，
        //它指定了SqlSessionFactory所使用的数据库环境，不声明的话则采用xml中的default元素对应的数据库环境
        environment = context.getStringAttribute("default");
      }
      //遍历各个子environment
      for (XNode child : context.getChildren()) {
        //先获取environment的id元素
        String id = child.getStringAttribute("id");
        /**
         * 判断id是否等于上面指定的environment String。因为我们只需要加载我们指定的environment即可。
         * 这里就可以明白为啥xml中可以配置多个数据库环境了，运行时可以由我们动态选择
         */
        if (isSpecifiedEnvironment(id)) {
          //获取transactionManager元素，创建transactionFactory实例并初始化它
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //获取dataSource元素，创建DataSourceFactory实例并初始化它
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          //builder设计模式创建environment对象，它包含id，transactionFactory，dataSource成员变量
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          //将创建好的Environment对象设置到Configuration实例中
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 读取并解析typeHandler元素，并添加到typeHandlerRegistry变量中
   * @param parent
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          /*使用package子元素时，mybatis自动检索包名下添加了相关注解的typeHandler，实例化它*/
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          /**
           * 使用typeHandler子元素时，分别读取javaType/jdbcType/handler三个元素
           * javaType对应JAVA类型
           * jdbcType对应数据库中的类型
           * handler则为处理类型
           * 比如将varchar变为String，就需要一个typeHandler
           * */
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          /*利用javaTypeName、jdbcTypeName、handlerTypeName创建实例化对象，可以为别名或者全限定名*/
          /*typeAlias这个时候就派上用场了*/
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          /*将实例化好的对象添加到typeHandlerRegistry中，mybatis运行时就需要用到他了，此时是注册阶段*/
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 读取并解析mappers元素，并添加到Configuration实例的mapperRegistry变量中
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //子元素是package时，mybatis将包名下所有的接口认为是mapper类，创建其类对象并添加到mapperRegistry中
          //此时一般是注解方式，不需要使用xml mapper文件
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          /*子元素为mapper时，读取子元素的resource或者url或者class属性*/
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          /**
           * url resource class 三者必居其一
           */
          if (resource != null && url == null && mapperClass == null) {
            //resource 属性不为空时，读取resource对应的xml资源并解析它
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            //url属性不为空时，读取url对应的xml资源并解析它
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            //class属性不为空时，直接创建class对象的类实例对象，并添加到Configuration中
            //仅使用mapperClass 而不使用xml mapper文件，一般是注解方式，建议采用注解方式
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
