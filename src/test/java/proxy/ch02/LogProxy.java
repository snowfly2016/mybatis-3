package proxy.ch02;

import proxy.ch01.TargetObjectService;

public class LogProxy implements TargetObjectService {

  TargetObjectService targetObjectService;


  public LogProxy(TargetObjectService targetObjectService) {
    this.targetObjectService = targetObjectService;
  }

  @Override
  public void query(String name) {
    System.out.println("log-------------------start");
    targetObjectService.query(name);
    System.out.println("log-------------------end");
  }
}
