package proxy.ch02;

import proxy.ch01.TargetObjectService;

public class TimeProxy implements TargetObjectService {

  TargetObjectService targetObjectService;


  public TimeProxy(TargetObjectService targetObjectService) {
    this.targetObjectService = targetObjectService;
  }

  @Override
  public void query(String name) {
    System.out.println("time-------------------start");
    targetObjectService.query(name);
    System.out.println("time-------------------end");
  }
}
