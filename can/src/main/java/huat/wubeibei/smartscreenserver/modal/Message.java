package huat.wubeibei.smartscreenserver.modal;

import java.util.HashMap;

public class Message {
    /**Message*/
    private Head Head;

    /**
     * key = signalName
     * value = SignalDefine
     * */
    private HashMap<String, Signal> SignalMap;
    /**
     * key = signalName
     * value = SignalValue
     * */
    private HashMap<String, Integer> ValueMap;

    public Message() {
        Head = new Head();
        SignalMap = new HashMap<>();
        ValueMap = new HashMap<>();
    }

    public void setSignalValue(String signalName, int signalValue){
        ValueMap.put(signalName, signalValue);
    }

    public int getSignalValue(String signalName){
        return ValueMap.get(signalName);
    }

    public String keyword(){
        return Head.getMsgID();
    }

    public Head getHead() {
        return Head;
    }

    public HashMap<String, Integer> getValueMap() {
        return ValueMap;
    }

    public void setValueMap(HashMap<String, Integer> valueMap) {
        ValueMap = valueMap;
    }

    public void setHead(Head head) {
        Head = head;
    }

    public HashMap<String, Signal> getSignalMap() {
        return SignalMap;
    }

    public void setSignalMap(HashMap<String, Signal> signalMap) {
        SignalMap = signalMap;
    }
}
