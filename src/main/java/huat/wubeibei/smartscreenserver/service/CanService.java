package huat.wubeibei.smartscreenserver.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.eventbus.Subscribe;
import huat.wubeibei.smartscreenserver.DataConvert;
import huat.wubeibei.smartscreenserver.JSONStreamListener;
import huat.wubeibei.smartscreenserver.eventbus.MessageWrap;
import huat.wubeibei.smartscreenserver.eventbus.MyEventBus;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static huat.wubeibei.smartscreenserver.util.ByteUtil.bytesToHex;
import static java.util.Arrays.copyOfRange;

public class CanService {
    private DataConvert dataConvert;
    private final static int CANPort = 9988;   // CAN总线端口号
    private final static String CANIp = "192.168.1.60"; // CAN总线IP地址
    private final static int MessageLength = 14;
    private final Thread CanReceiveThread = new Thread(new CanReceive()); // CAN总线接收线程
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(); // 发送线程池，保持发送顺序


    // 初始化
    public CanService(InputStream config) {
        // 注册事件总线
        MyEventBus.getInstance().register(this);
        dataConvert = new DataConvert(config);
        System.out.println("CanReceiveResource load success!");
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
        Runnable runnable = () -> {
            JSONObject jsonObject = JSON.parseObject(json);
            String msgID = jsonObject.getString("msgID");
            byte[] bytes = dataConvert.getByte(msgID);
            DatagramPacket datagramPacket;
            try (DatagramSocket datagramSocket = new DatagramSocket()) {
                datagramPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(CANIp), CANPort);
                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        singleThreadExecutor.execute(runnable);
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
        switch (action) {
            case "send":
                sendData(data.toJSONString());
                break;
            case "modify":
                setSignalValue(data.getString("msg_id"), data.getString("signal_name"), data.getDoubleValue("value"));
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
                datagramSocket = new DatagramSocket(CANPort);
                while (true) {
                    datagramPacket = new DatagramPacket(receiveMsg, receiveMsg.length);
                    datagramSocket.receive(datagramPacket);
                    String check;
                    check = bytesToHex(copyOfRange(receiveMsg, 0, 2));
                    if (!check.equals("aabb")) {
                        return;
                    }
                    dataConvert.getJSONString(copyOfRange(receiveMsg, 2, 14), new JSONStreamListener() {
                        @Override
                        public void produce(String json) {
                            // 产生JSON数据流，放入EventBus，转发给客户端
                            MyEventBus.getInstance().post(MessageWrap.getInstance(json));
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
