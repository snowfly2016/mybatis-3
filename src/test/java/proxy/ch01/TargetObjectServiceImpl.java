package proxy.ch01;

public class TargetObjectServiceImpl implements TargetObjectService{

  public void query(String name){
    String s = "查询登录人员" + name+"是否存在";
    System.out.println(s);
  }
}
