package huat.wubeibei.smartscreenserver;

import com.alibaba.fastjson.JSONObject;
import huat.wubeibei.smartscreenserver.modal.Message;
import huat.wubeibei.smartscreenserver.modal.Signal;
import huat.wubeibei.smartscreenserver.util.ByteUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static huat.wubeibei.smartscreenserver.util.ByteUtil.countBit;
import static java.util.Arrays.copyOfRange;

public class DataConvert {

    private HashMap<String, Message> messageMap = new HashMap<>();
    private InputStream config;

    // 类初始化
    public DataConvert(InputStream config) {
        this.config = config;
        initMessageMap();
    }

    // 获得报文段的字节流，并重置报文段的值(恢复为默认值，如默认值小于零则不恢复)
    // msgID 为报文段的ID
    public byte[] getByte(String msgID) {
        Message msg = messageMap.get(msgID);
        if (msg == null)
            return null;
        else {
            // 构建一个Byte数组
            int length = msg.getHead().getMsgLength();
            byte[] bytes = new byte[length];

            HashMap<String, Signal> hashMap = msg.getSignalMap();
            Collection<Signal> signalCollection = hashMap.values();
            for (Signal sig : signalCollection) {
                int SrcNum = msg.getSignalValue(sig.getSignalName());
                int Byte_offset = sig.getOffset();
                int start_bit_index = sig.getStartBitPosition();
                int bitLength = sig.getSignalLength();
                String state = sig.getLayoutFormat();
                ByteUtil.setBits(bytes, SrcNum, Byte_offset, start_bit_index, bitLength, state);
                // 顺便恢复默认值，如果值小于零则无效，不恢复
                if (sig.getDefaultValue() >= 0) {
                    msg.setSignalValue(sig.getSignalName(), sig.getDefaultValue());
                }
            }
            return bytes;
        }
    }

    // 从字节流中获得报文的JSON串
    public void getJSONString(byte[] bytes, JSONStreamListener jsonStreamListener) {
        try {
            String key = ByteUtil.bytesToHex(copyOfRange(bytes, 8, 4));
            String sampleKey = key.substring(key.charAt('0'));
            // 报文段
            Message message = messageMap.get(sampleKey);
            Collection<Signal> signalCollection = message.getSignalMap().values();
            for (Signal sig : signalCollection) {
                double value = sig.getResolution() * countBit(bytes, 0, sig.getStartBitPosition(), sig.getSignalLength(), sig.getLayoutFormat());
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("msg_id", message.getHead().getMsgID());
                jsonObject.put("signal_name", sig.getSignalName());
                jsonObject.put("value", value);
                jsonStreamListener.produce(jsonObject.toJSONString());
            }
            jsonStreamListener.onComplete();
        } catch (Throwable e) {
            jsonStreamListener.onError(e);
        }
    }

    // 设置报文的值
    public void setSignalValue(String msgName, String signalName, double value) {
        try {
            Signal sig = messageMap.get(msgName).getSignalMap().get(signalName);
            int maxValue = sig.getMaxValue();
            int minValue = sig.getMinValue();
            if (value < minValue || value > maxValue)
                return;
            int realValue = (int) (value / sig.getResolution());
            messageMap.get(msgName).setSignalValue(signalName, realValue);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // 测试
    public static void main(String[] args) {
//        File file = new File("D:\\AndroidStudioProject\\SmartScreen\\app\\src\\main\\assets\\messageLayout.xml");
//        huat.wubeibei.DataConvert dataConvert = new huat.wubeibei.DataConvert(file);
//        dataConvert.initMessageMap();
    }

    // 初始化报文
    private void initMessageMap() {
        SAXReader saxReader = new SAXReader();
        try {
            Document document = saxReader.read(config);
            Element rootElement = document.getRootElement();
            List<Element> messageList_element = rootElement.elements("Message");
            for (Element msg_element : messageList_element) {

                Message msg = new Message();

                Element head_element = msg_element.element("Head");
                fillAttribute(head_element, msg.getHead());

                List<Element> signalList_element = msg_element.elements("SignalList");
                HashMap<String, Signal> hashMap = new HashMap<>();
                for (Element signal_element : signalList_element) {
                    Signal sig = new Signal();
                    fillAttribute(signal_element.element("Signal"), sig);
                    ///////////////////////////////////////
                    hashMap.put(sig.keyword(), sig);
                }
                msg.setSignalMap(hashMap);
                ///////////////////////////////////////
                messageMap.put(msg.keyword(), msg);
            }
        } catch (DocumentException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // 初始化报文的子函数
    private void fillAttribute(Element element, Object object) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (object != null) {
            // 拿到该类
            Class<?> clz = object.getClass();
            // 获取实体类的所有属性，返回Field数组
            Field[] fields = clz.getDeclaredFields();

            for (Field field : fields) {
                // 如果类型是String
                if (field.getGenericType().toString().equals("class java.lang.String")) {
                    Method m = object.getClass().getMethod("set" + field.getName(), String.class);
                    m.invoke(object, element.element(field.getName()).getData());
                }
                // 如果类型是int
                if (field.getGenericType().toString().equals("int")) {
                    Method m = object.getClass().getMethod("set" + field.getName(), int.class);
                    String num = (String) element.element(field.getName()).getData();
                    m.invoke(object, Integer.parseInt(num));
                }
            }
        }
    }
}