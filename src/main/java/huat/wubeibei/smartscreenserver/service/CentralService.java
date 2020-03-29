package huat.wubeibei.smartscreenserver.service;


import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;

public class CentralService{


    public static void main(String[] args) {
        CentralService centralService = new CentralService();
    }

    public CentralService() {
        CanService canService = new CanService();
        RemoteService remoteService = new RemoteService();
        MyEventBus.register(canService);
        MyEventBus.register(remoteService);
        canService.start();
        remoteService.start();
    }
}
