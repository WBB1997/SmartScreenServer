package huat.wubeibei.smartscreenserver.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.eventbus.Subscribe;
import huat.wubeibei.candataconvert.DataConvert;
import huat.wubeibei.candataconvert.JSONStreamListener;
import huat.wubeibei.smartscreenserver.eventbus.MessageWrap;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CanService {
    private DataConvert dataConvert;
    private final static int receivePort = 8888;
    private final static int sendPort = 9988;
//    private final static String CANIp = "192.168.1.60"; // CAN总线IP地址
    private final static String CANIp = "127.0.0.1"; // CAN总线IP地址
    private final static int MessageLength = 14;
    private final Thread CanReceiveThread = new Thread(new CanReceive()); // CAN总线接收线程
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(); // 发送线程池，保持发送顺序


    // 初始化
    public CanService() {
        dataConvert = new DataConvert();
    }

    // 启动接收
    public void start() {
        if (dataConvert != null) {
            System.out.println("CanReceiveThread Start listening!");
            CanReceiveThread.start();
        }
    }

    // 向CAN总线发消息
    private void sendData(final String json) {
        singleThreadExecutor.execute(() -> {
            try {
                JSONObject jsonObject = JSON.parseObject(json);
                String msgName = jsonObject.getString("msg_name");
                // 获取需要发送的Byte
                byte[] bytes = dataConvert.getByte(msgName);
                DatagramPacket datagramPacket;
                DatagramSocket datagramSocket = new DatagramSocket();
                datagramPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(CANIp), sendPort);
                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // 修改信号值
    private void setSignalValue(String msgName, String signalName, double value){
        dataConvert.setSignalValue(msgName,signalName,value);
    }

    // 接收Client发来的指令
    @Subscribe
    public void messageEventBus(MessageWrap messageWrap) {
        JSONObject jsonObject = JSON.parseObject(messageWrap.getMessage());
        String action = jsonObject.getString("action");
        JSONObject data = jsonObject.getJSONObject("data");
        System.out.println("CanService EventBus receive: " + jsonObject);
        switch (action) {
            case "send":
                sendData(data.toJSONString());
                break;
            case "modify":
                setSignalValue(data.getString("msg_name"), data.getString("signal_name"), data.getDoubleValue("value"));
                break;
        }
    }


    // CAN总线接收类
    private class CanReceive implements Runnable {
        @Override
        public void run() {
            byte[] receiveMsg = new byte[MessageLength];
            DatagramSocket datagramSocket;
            DatagramPacket datagramPacket;
            try {
                datagramSocket = new DatagramSocket(receivePort);
                while (true) {
                    datagramPacket = new DatagramPacket(receiveMsg, receiveMsg.length);
                    datagramSocket.receive(datagramPacket);
//                    String check;
//                    check = bytesToHex(copyOfRange(receiveMsg, 0, 2));
//                    if (!check.equals("aabb")) {
//                        return;
//                    }
                    dataConvert.getJSONString(receiveMsg, new JSONStreamListener() {
                        @Override
                        public void produce(String json) {
                            // 产生JSON数据流，放入EventBus，转发给客户端
                            MyEventBus.post(MessageWrap.getBean(json));
                        }

                        @Override
                        public void onComplete() {
                            // 解析完成的动作
                        }

                        @Override
                        public void onError(Throwable e) {
                            // 出现异常的动作
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
