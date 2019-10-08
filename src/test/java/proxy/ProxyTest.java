package proxy;

import proxy.ch01.LogProxyTargetObject;
import proxy.ch01.TargetObjectService;
import proxy.ch01.TargetObjectServiceImpl;
import proxy.ch01.TimeProxyTargetObject;
import proxy.ch02.LogProxy;
import proxy.ch02.TimeProxy;
import proxy.ch03.ProxyUtils;

/**
 * 代理的实现方式：
 * 1.静态代理（继承&聚合）
 * (1)聚合：代理类聚合了被代理类，且代理类及被代理类都实现了相同的接口，则可实现灵活多变，具体看代码ch02
 * (2)继承：继承不够灵活，随着功能需求增多，继承体系会非常臃肿。具体看代码ch01
 * 静态代理类优缺点
 * 优点：业务类只需要关注业务逻辑本身，保证了业务类的重用性。这是代理的共有优点。
 * 缺点：
 * 1）代理对象的一个接口只服务于一种类型的对象，如果要代理的方法很多，势必要为每一种方法都进行代理，静态代理在程序规模稍大时就无法胜任了。
 * 2）如果接口增加一个方法，除了所有实现类需要实现这个方法外，所有代理类也需要实现此方法。增加了代码维护的复杂度。
 * 另外，如果要按照上述的方法使用代理模式，那么真实角色(委托类)必须是事先已经存在的，并将其作为代理对象的内部属性。但是实际使用时，
 * 一个真实角色必须对应一个代理角色，如果大量使用会导致类的急剧膨胀；此外，如果事先并不知道真实角色（委托类），该如何使用代理呢？
 * 这个问题可以通过Java的动态代理类来解决。
 *
 * 2.动态代理
 */
public class ProxyTest {

  public static void main(String[] args) throws Exception{
    /**
     * ch01
     * 场景：查询某一个人是否在系统中存在
     * 要求在查询前后输出相关日志信息
     *
     * 实现方式：通过继承实现
     */
   /* LogProxyTargetObject proxyTargetObject = new LogProxyTargetObject();
    proxyTargetObject.query("张三");

    TimeProxyTargetObject timeProxyTargetObject = new TimeProxyTargetObject();
    timeProxyTargetObject.query("张三");
*/
    System.out.println("----------------ch01 end------------------------------");
    /**
     * ch02
     * 场景：查询某一个人是否在系统中存在
     * 要求在查询前后输出相关日志信息
     *
     * 实现方式：通过聚合实现
     */
    TargetObjectService targetObjectService = new TargetObjectServiceImpl();
    /* LogProxy logProxy = new LogProxy(targetObjectService);
    TimeProxy timeProxy = new TimeProxy(logProxy);
    timeProxy.query("张三");*/
    System.out.println("----------------ch02 end------------------------------");

    TargetObjectService newProxyInstance = (TargetObjectService)ProxyUtils.newProxyInstance(targetObjectService);
    newProxyInstance.query("李四");
    System.out.println("----------------ch03 end------------------------------");
  }
}
