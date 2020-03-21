package huat.wubeibei.smartscreenserver.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.eventbus.Subscribe;
import com.xuhao.didi.core.iocore.interfaces.ISendable;
import com.xuhao.didi.core.pojo.OriginalData;
import com.xuhao.didi.socket.client.sdk.OkSocket;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.dispatcher.IRegister;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.server.IClient;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.server.IClientIOCallback;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.server.IClientPool;
import com.xuhao.didi.socket.common.interfaces.common_interfacies.server.IServerManager;
import com.xuhao.didi.socket.server.action.ServerActionAdapter;
import huat.wubeibei.smartscreenserver.eventbus.MessageWrap;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteService {
    private IServerManager serverManager;
    private final static int ClientPort = 5118;
    private String connectionIp = "";
    private String password = "";
    // KEY = "IP:PORT"
    // VALUE = uniqueTag
    private ConcurrentHashMap<String, String> mClientList = new ConcurrentHashMap<>();

    RemoteService() {
        // 注册事件总线
        MyEventBus.getInstance().register(this);
        // 开始监听
        IRegister register = OkSocket.server(ClientPort);
        serverManager = (IServerManager) register.registerReceiver(new ServerActionAdapter() {
            @Override
            public void onClientConnected(IClient client, int serverPort, IClientPool clientPool) {
                System.out.println("connected->" + client.getHostIp() + ":" + serverPort);
                mClientList.put(client.getHostIp(), client.getUniqueTag());
                client.addIOCallback(new IOCallBack());
            }

            @Override
            public void onClientDisconnected(IClient client, int serverPort, IClientPool clientPool) {
                System.out.println("disconnected->" + client.getHostIp() + ":" + serverPort);
                mClientList.remove(client.getHostIp());
                client.removeAllIOCallback();
            }
        });
        // 加载配置文件
        config();
        System.out.println("RemoteServiceResource load success!");
    }

    //加载配置文件
    void config() {
        connectionIp = getConnectionIp();
        password = getPassword();
        System.out.println("load connectionIp: " + connectionIp);
        System.out.println("load password: " + password);
    }

    // 服务器开始监听客户端请求
    void start() {
        if (serverManager != null) {
            if (!serverManager.isLive()) {
                System.out.println("RemoteServer Start listening!");
                serverManager.listen();
            }
        }
    }

    // 服务器接收客户端消息回调
    private class IOCallBack implements IClientIOCallback {
        // 客户端发给服务器
        @Override
        public void onClientRead(OriginalData originalData, IClient client, IClientPool<IClient, String> clientPool) {
            String str = new String(originalData.getBodyBytes(), StandardCharsets.UTF_8);
            // 收到的JSON串
            System.out.println(str);
            JSONObject jsonObject = JSON.parseObject(str);
            String action = jsonObject.getString("action");
            // 处理消息
            switch (action) {
                case "login":
                    login(jsonObject.getJSONObject("data"), client.getHostIp());
                    break;
                case "modify":
                    modify(jsonObject);
                    break;
                case "send":
                    send(jsonObject);
                    break;
                case "relogin":
                    if (mClientList.containsKey(client.getHostIp())) {
                        // 连接的客户端与当前客户端不一样
                        if (!client.getHostIp().equals(connectionIp))
                            closeClient(client.getHostIp());
                    }
                    break;
            }
        }

        // 服务器发给客户端
        @Override
        public void onClientWrite(ISendable sendable, IClient client, IClientPool<IClient, String> clientPool) {
        }
    }

    // 接收EventBus传递的事件（CanService->RemoteService）
    @Subscribe
    public void messageEventBus(MessageWrap messageWrap) {
        send(messageWrap.getMessage(), connectionIp);
    }

    // 发送给客户端
    private void send(String str, String Tag) {
        IClientPool pool = serverManager.getClientPool();
        IClient client = (IClient) pool.findByUniqueTag(Tag);
        if (client != null) {
            System.out.println(str);
            client.send(MessageWrap.getInstance(str));
        }
    }


    // 登录处理
    private void login(JSONObject jsonObject, String Ip) {
        String psw = jsonObject.getString("password");

        boolean flag = password.equals(psw);// 验证密码正确与否
        // 验证成功，踢掉之前的客户端，然后加入新客户端
        // 验证失败，返回验证失败信息
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("action", "login");
        jsonObject1.put("data", flag);
        jsonObject1.put("msg", !flag ? "密码错误" : "登录成功");

        // 登录成功保存的当前IP,踢掉上一次登录的账户
        if (flag) {
            if (!connectionIp.equals(Ip)) {
                closeClient(connectionIp);
            }
            connectionIp = Ip;
            saveConnectionIp(Ip);
        }
        send(jsonObject1.toString(), mClientList.get(connectionIp));
    }

    private void closeClient(String clientIp) {
        if (!mClientList.containsKey(clientIp))
            return;
        JSONObject json = new JSONObject();
        json.put("action", "message");
        json.put("status", 0);
        json.put("msg", "其他设备登录，本机自动下线");

        IClient client = (IClient) serverManager.getClientPool().findByUniqueTag(mClientList.get(clientIp));
        if (client != null) {
            System.out.println(json);
            client.send(MessageWrap.getInstance(json.toJSONString()));
        }
    }

    // 信号值修改
    private void modify(JSONObject jsonObject) {
        MyEventBus.getInstance().post(jsonObject.toJSONString());
    }

    // 发送给CAN总线
    private void send(JSONObject jsonObject) {
        MyEventBus.getInstance().post(jsonObject.toJSONString());
    }

    // 获得上一次登录的用户Ip
    public String getConnectionIp() {
        SAXReader saxReader = new SAXReader();
        Document document;
        try {
            document = saxReader.read("src/main/resources/Ip.xml");
            Element rootElement = document.getRootElement();
            Element tag = rootElement.element("ip");
            return tag.getText().trim();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return "";
    }

    // 存储上一次登录的用户Ip
    public void saveConnectionIp(String ip) {
        try {
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("res");
            Element tagele = root.addElement("ip");
            tagele.setText(ip);
            OutputStream os = new FileOutputStream("src/main/resources/Ip.xml");
            //Format格式输出格式刷
            OutputFormat format = OutputFormat.createPrettyPrint();
            //设置xml编码
            format.setEncoding("utf-8");

            //写：传递两个参数一个为输出流表示生成xml文件在哪里
            //另一个参数表示设置xml的格式
            XMLWriter xw = new XMLWriter(os, format);
            //将组合好的xml封装到已经创建好的document对象中，写出真实存在的xml文件中
            xw.write(doc);
            //清空缓存关闭资源
            xw.flush();
            xw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 获得密码
    private String getPassword() {
        SAXReader saxReader = new SAXReader();
        Document document;
        try {
            document = saxReader.read("src/main/resources/Psw.xml");
            Element rootElement = document.getRootElement();
            Element psd = rootElement.element("password");
            return psd.getText().trim();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return "123456";
    }
}
