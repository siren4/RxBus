package com.siren.liu.bus.bean;

/**
 * Created by LiuG on 2018/11/8.
 */

public final class Message {

    private int code;
    private Object object;

    public Message(int code, Object object) {
        this.code = code;
        this.object = object;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }
}
