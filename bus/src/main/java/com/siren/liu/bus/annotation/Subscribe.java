package com.siren.liu.bus.annotation;

import com.siren.liu.bus.mode.ThreadMode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by LiuG on 2018/11/7.
 */

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
    /**
     * 按code分发，就是打tag
     *
     * @return
     */
    int code() default -1;

    /**
     * 线程模式
     *
     * @return
     */
    ThreadMode threadMode() default ThreadMode.CURRENT_THREAD;

    /**
     * 是否粘性
     *
     * @return
     */
    boolean sticky() default false;

}
