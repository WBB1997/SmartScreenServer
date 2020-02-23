package huat.wubeibei.smartscreenserver.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.eventbus.Subscribe;
import com.xuhao.didi.core.iocore.interfaces.ISendable;
import com.xuhao.didi.core.pojo.OriginalData;
import com.xuhao.didi.socket.client.sdk.OkSocket;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.dispatcher.IRegister;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.server.*;
import com.xuhao.didi.socket.server.action.ServerActionAdapter;
import huat.wubeibei.smartscreenserver.eventbus.MessageWrap;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class RemoteService {
    private IServerManager serverManager;
    private final static int ClientPort = 5118;
    private String connectionClient = "";

    RemoteService() {
        // 开始监听
        IRegister register = OkSocket.server(ClientPort);
        serverManager = (IServerManager) register.registerReceiver(new ServerActionAdapter() {
            @Override
            public void onServerWillBeShutdown(int serverPort, IServerShutdown shutdown, IClientPool clientPool, Throwable throwable) {
                shutdown.shutdown();
            }

            @Override
            public void onClientConnected(IClient client, int serverPort, IClientPool clientPool) {
                client.addIOCallback(new IOCallBack());
            }

            @Override
            public void onClientDisconnected(IClient client, int serverPort, IClientPool clientPool) {
                client.removeAllIOCallback();
            }
        });
    }

    void start(){
        if(serverManager!= null) {
            if (!serverManager.isLive()) {
                System.out.println("RemoteServer Start listening!");
                serverManager.listen();
            }
        }
    }

    private class IOCallBack implements IClientIOCallback {
        // Server->Client
        @Override
        public void onClientRead(OriginalData originalData, IClient client, IClientPool<IClient, String> clientPool) {

        }

        // Client->Server
        @Override
        public void onClientWrite(ISendable sendable, IClient client, IClientPool<IClient, String> clientPool) {
            String str = new String(sendable.parse());
            // 收到的JSON串
            JSONObject jsonObject = JSON.parseObject(str);
            // 处理消息
            String action = jsonObject.getString("action");
            switch (action) {
                case "login":
                    login(jsonObject.getJSONObject("data"), client.getUniqueTag());
                    break;
                case "modify":
                    if (client.getUniqueTag().equals(connectionClient))
                        modify(jsonObject);
                    break;
                case "send":
                    send(jsonObject);
                    break;
            }
        }
    }

    // 接收EventBus里的事件（CanService->RemoteService）
    @Subscribe
    public void messageEventBus(MessageWrap messageWrap) {
        send(messageWrap.getMessage(), connectionClient);
    }

    // 发送给客户端
    private void send(String str, String ip) {
        IClientPool pool = serverManager.getClientPool();
        IClient client = (IClient) pool.findByUniqueTag(ip);
        if (client != null) {
            client.send(MessageWrap.getInstance(str));
        }
    }


    // 登录处理
    private void login(JSONObject jsonObject, String Ip) {
        String password = jsonObject.getString("password");

        boolean flag = check(password);// 验证密码正确与否
        // 验证成功，踢掉之前的客户端，然后加入新客户端
        // 验证失败，返回验证失败信息
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("action", "login");
        jsonObject2.put("data", flag);
        jsonObject2.put("msg", !flag ? "密码错误！" : "登录成功！");
        send(jsonObject2.toString(), Ip);

        // 登录成功保存的当前IP
        if (flag) {
            JSONObject jsonObject1 = new JSONObject();
            jsonObject1.put("action", "message");
            jsonObject1.put("status", 0);
            jsonObject1.put("msg", "其他设备登录，本机自动下线。");
            send(jsonObject1.toJSONString(), connectionClient);
            connectionClient = Ip;
        }
    }

    // 信号值修改
    private void modify(JSONObject jsonObject){
        MyEventBus.getInstance().post(jsonObject.toJSONString());
    }

    // 发送
    private void send(JSONObject jsonObject){
        MyEventBus.getInstance().post(jsonObject.toJSONString());
    }

    // 查询密码
    private boolean check(String password) {
        SAXReader saxReader = new SAXReader();
        Document document = null;
        try {
            document = saxReader.read("src/main/resources/Psw.xml");
            Element rootElement = document.getRootElement();
            Element psd = rootElement.element("password");
            String psw = psd.getText();
            return psw.equals(password);
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return false;
    }
}
