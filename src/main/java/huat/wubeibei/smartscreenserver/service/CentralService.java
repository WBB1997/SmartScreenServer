package huat.wubeibei.smartscreenserver.service;


import com.google.common.eventbus.EventBus;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class CentralService{
    private CanService canService;
    private RemoteService remoteService;


    public static void main(String[] args) {
        try {
            CentralService centralService = new CentralService();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public CentralService() throws FileNotFoundException {
        canService = new CanService(new FileInputStream(new File("src/main/resources/MessageLayout.xml")));
        canService.start();
        remoteService = new RemoteService();
        remoteService.start();
        MyEventBus.getInstance().register(canService);
        MyEventBus.getInstance().register(remoteService);
    }
}
