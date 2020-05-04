package huat.wubeibei.smartscreenserver.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.eventbus.Subscribe;
import huat.wubeibei.candataconvert.DataConvert;
import huat.wubeibei.candataconvert.JSONStreamListener;
import huat.wubeibei.candataconvert.util.ByteUtil;
import huat.wubeibei.smartscreenserver.eventbus.MessageWrap;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CanService {
    private DataConvert dataConvert;
    private final static int receivePort = 9999;
    private final static int sendPort = 8888;
    private final static String CANIp = "192.168.0.102"; // CAN总线IP地址
    private final static int MessageLength = 10;
    private final Thread CanReceiveThread = new Thread(new CanReceive()); // CAN总线接收线程
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(); // 发送线程池，保持发送顺序


    // 初始化
    public CanService() {
        try {
            dataConvert = new DataConvert(new FileInputStream(new File("src/main/resources/MessageLayout.xml")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    // 启动接收
    public void start() {
        if (dataConvert != null) {
            CanReceiveThread.start();
            System.out.println("CanReceiveThread Start listening!");
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
                System.out.println("Send->" + CANIp + ": " + ByteUtil.bytesToHex(bytes));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // 修改信号值
    private void setSignalValue(String msgName, String signalName, double value) {
        dataConvert.setSignalValue(msgName, signalName, value);
    }

    // 接收Client发来的指令
    @Subscribe
    public void messageEventBus(MessageWrap messageWrap) {
        try {
            JSONObject jsonObject = JSON.parseObject(messageWrap.getMessage());
            String action = jsonObject.getString("action");
            JSONObject data = jsonObject.getJSONObject("data");
            switch (action) {
                case "send":
                    sendData(data.toJSONString());
                    break;
                case "modify":
                    setSignalValue(data.getString("msg_name"), data.getString("signal_name"), data.getDoubleValue("value"));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                    dataConvert.getJSONString(receiveMsg, new JSONStreamListener() {
                        @Override
                        public void produce(String json) {
                            // 产生JSON数据流，放入EventBus，转发给客户端
                            JSONObject object = new JSONObject();
                            object.put("action", "instruction");
                            object.put("data", JSONObject.parseObject(json));
                            MyEventBus.post(MessageWrap.getBean(object.toJSONString()));
                        }

                        @Override
                        public void onComplete() {
                            // 解析完成的动作
                        }

                        @Override
                        public void onError(Throwable e) {
                            // 出现异常的动作
                            e.printStackTrace();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
