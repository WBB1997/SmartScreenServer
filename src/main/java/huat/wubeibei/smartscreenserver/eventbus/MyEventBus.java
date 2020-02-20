package huat.wubeibei.smartscreenserver.eventbus;

import com.google.common.eventbus.EventBus;

public class MyEventBus {
    private static class SingletonHolder{
        private static EventBus instance=new EventBus();
    }

    private MyEventBus(){
        System.out.println("EventBus has loaded");
    }

    public static EventBus getInstance(){
        return SingletonHolder.instance;
    }

    // 发送事件
    public void post(Object event) {
        getInstance().post(event);
    }

    // 为类注册监听
    public void register(Object object) {
        getInstance().register(object);
    }
}
