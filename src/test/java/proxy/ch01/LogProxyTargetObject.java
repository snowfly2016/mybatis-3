package proxy.ch01;

public class LogProxyTargetObject extends TargetObjectServiceImpl {
  @Override
  public void query(String name) {
    System.out.println("开始查询......");
    super.query(name);
    System.out.println("查询end......");
  }
}
