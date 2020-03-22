package huat.wubeibei.smartscreenserver.service;


import com.google.common.eventbus.EventBus;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class CentralService{


    public static void main(String[] args) {
        try {
            CentralService centralService = new CentralService();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public CentralService() throws FileNotFoundException {
        CanService canService = new CanService(new FileInputStream(new File("src/main/resources/MessageLayout.xml")));
        RemoteService remoteService = new RemoteService();
        MyEventBus.register(canService);
        MyEventBus.register(remoteService);
        canService.start();
        remoteService.start();
    }
}
