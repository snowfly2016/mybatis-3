package mybatis;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MybatisTest {

  public static void main(String[] args) throws Exception {
    String resource = "mybatis.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    //xml解析完成
    //其实我们mybatis初始化方法 除了XML意外 其实也可以0xml完成
//   new SqlSessionFactoryBuilder().b
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    Configuration configuration = sqlSessionFactory.getConfiguration();
    //使用者可以随时使用或者销毁缓存
    //默认sqlsession不会自动提交
    //从SqlSession对象打开开始 缓存就已经存在
    SqlSession sqlSession = sqlSessionFactory.openSession();

    //从调用者角度来讲 与数据库打交道的对象 SqlSession
    //通过动态代理 去帮我们执行SQL
    //拿到一个动态代理后的Mapper
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    Map<String,Object> map = new HashMap<>();
    map.put("id","1");
    //因为一级缓存 这里不会调用两次SQL
    System.out.println(mapper.selectAll("1", "1"));
    //如果有二级缓存 这里就不会调用两次SQL
    //当调用 sqlSession.close() 或者说刷新缓存方法， 或者说配置了定时清空缓存方法  都会销毁缓存
    sqlSession.close();

  }
}
