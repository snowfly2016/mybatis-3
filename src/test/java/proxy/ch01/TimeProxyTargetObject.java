package proxy.ch01;

public class TimeProxyTargetObject extends TargetObjectServiceImpl {
  @Override
  public void query(String name) {
    System.out.println("开始查询时间......");
    super.query(name);
    System.out.println("查询end时间......");
  }
}
