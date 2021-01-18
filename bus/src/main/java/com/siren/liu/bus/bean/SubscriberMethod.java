package com.siren.liu.bus.bean;

import com.siren.liu.bus.mode.ThreadMode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by LiuG on 2018/11/7.
 */

public class SubscriberMethod {

    public Method method; //注解标记的方法
    public ThreadMode threadMode;//线程模式
    public Class<?> eventType;//方法参数类型
    public Object subscriber;//注册类
    public int code;//标签
    public boolean sticky;//粘性

    public SubscriberMethod(Object subscriber, Method method, Class<?> eventType, int code, ThreadMode threadMode, boolean sticky) {
        this.subscriber = subscriber;
        this.method = method;
        this.eventType = eventType;
        this.code = code;
        this.threadMode = threadMode;
        this.sticky = sticky;
    }

    public void invoke(Object o) {
        try {
            method.invoke(subscriber, o);
        } catch (IllegalAccessException e) {//访问权限异常
            e.printStackTrace();
        } catch (InvocationTargetException e) {//当被调用的方法内部抛出异常而没有被捕获时，将由该异常接收。
            e.printStackTrace();
        }
    }
}
