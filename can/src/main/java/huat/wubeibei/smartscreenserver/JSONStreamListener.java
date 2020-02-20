package huat.wubeibei.smartscreenserver;

public interface JSONStreamListener {
    void produce(String json);

    void onComplete();

    void onError(Throwable e);
}
