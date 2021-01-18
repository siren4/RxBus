package com.siren.liu.bus;

import com.siren.liu.bus.annotation.Subscribe;
import com.siren.liu.bus.bean.Message;
import com.siren.liu.bus.bean.SubscriberMethod;
import com.siren.liu.bus.exception.RxBusException;
import com.siren.liu.bus.mode.ThreadMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by LiuG on 2018/11/7.
 */

public final class RxBus {

    private Map<Object, List<Disposable>> disposablesBySubscriber = new ConcurrentHashMap<>();//注册类-注册类中所有的Disposable集合
    private Map<Class, List<Class>> eventTypesBySubscriber = new ConcurrentHashMap<>();//注册类-注册类中所有事件类型的集合
    private Map<Class, List<SubscriberMethod>> subscriberMethodByEventType = new ConcurrentHashMap<>();//事件类型-含有该事件的注解方法集合
    private final Map<Class<?>, Object> stickyEvent = new ConcurrentHashMap<>();
    private final Subject bus;

    private RxBus() {
        bus = PublishSubject.create().toSerialized();
    }

    private enum Singleton {
        SINGLETON;
        RxBus rxBus;

        Singleton() {
            rxBus = new RxBus();
        }

        RxBus getRxBus() {
            return rxBus;
        }
    }

    public static RxBus get() {
        return Singleton.SINGLETON.getRxBus();
    }

    /**
     * 注册
     *
     * @param subscriber 订阅者
     */
    public void register(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();
        if (eventTypesBySubscriber.containsKey(subscriberClass)) {//防止重复注册
            return;
        }
        synchronized (this) {
            int subscribeMethodCount = 0;
            Method[] methods = subscriberClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Subscribe.class)) {//找到被注解的方法
                    subscribeMethodCount++;
                    Class[] parameterType = method.getParameterTypes();//获取方法的参数
                    if (parameterType != null && parameterType.length == 1) {//且有且只能有1个参数
                        Class eventType = parameterType[0];
                        addEventTypeToMap(subscriberClass, eventType);

                        //获取注解的配置属性
                        Subscribe sub = method.getAnnotation(Subscribe.class);
                        int code = sub.code();
                        ThreadMode threadMode = sub.threadMode();
                        boolean sticky = sub.sticky();
                        SubscriberMethod subscriberMethod = new SubscriberMethod(subscriber, method, eventType, code, threadMode, sticky);

                        if (isNeedAddSubscriberMethod(eventType, subscriberMethod)) {
                            //保存注解方法
                            addSubscriberMethodToMap(eventType, subscriberMethod);
                            //创建添加订阅者并切换线程
                            addSubscriber(subscriberMethod);
                        } else {
                            throw new RxBusException("SubscribeMethod already be added!");
                        }

                    } else {
//                        throw new RxBusException("SubscribeMethod must have and only one parameter!");
                    }
                }
            }
            if (subscribeMethodCount == 0) {
                throw new RxBusException("Subscriber hasn't subscribeAnnotation method!");
            }
        }
    }

    /**
     * 按注册类保存事件类型
     *
     * @param subscriberClass 订阅者
     * @param eventType       事件类型
     */
    private void addEventTypeToMap(Class<?> subscriberClass, Class<?> eventType) {
        List<Class> eventTypes = eventTypesBySubscriber.get(subscriberClass);
        if (eventTypes == null) {
            eventTypes = new ArrayList<>();
            eventTypesBySubscriber.put(subscriberClass, eventTypes);
        }
        //在同一个类中，可以有参数相同的多个注解方法，然后用code区分。
        if (!eventTypes.contains(eventType)) {
            eventTypes.add(eventType);
        }
    }

    /**
     * 方法是否被添加？
     *
     * @param eventType
     * @param subscriberMethod
     * @return
     */
    private boolean isNeedAddSubscriberMethod(Class<?> eventType, SubscriberMethod subscriberMethod) {
        boolean need = true;
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods != null) {
            for (SubscriberMethod method : subscriberMethods) {
                //如果订阅者相同且code相同，就表示已经添加
                if (method.subscriber == subscriberMethod.subscriber && method.code == subscriberMethod.code) {
                    //一个类可以存在多个eventType相同的方法，然后用code区别标记。
                    need = false;
                    break;
                }
            }
        }
        return need;
    }

    /**
     * 按事件类型保存注解方法，用于反射回调。
     *
     * @param eventType
     * @param subscriberMethod
     */
    private void addSubscriberMethodToMap(Class<?> eventType, SubscriberMethod subscriberMethod) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods == null) {
            subscriberMethods = new ArrayList<>();
            subscriberMethodByEventType.put(eventType, subscriberMethods);
        }

        if (!subscriberMethods.contains(subscriberMethod)) {
            subscriberMethods.add(subscriberMethod);
        }
    }

    /**
     * 用RxJava添加订阅者
     *
     * @param subscriberMethod
     */
    private void addSubscriber(final SubscriberMethod subscriberMethod) {
        Observable observable;
        if (subscriberMethod.sticky) {
            observable = toObservableSticky(subscriberMethod.eventType);
        } else {
            if (subscriberMethod.code == -1) {
                observable = toObservable(subscriberMethod.eventType);
            } else {
                observable = toObservable(subscriberMethod.code, subscriberMethod.eventType);
            }
        }
        //Subscription subscription = postToObservable(observable, subscriberMethod).subscribe(...);
        //rxJava2.0，disposable取代Subscription完成任务的取消或执行
        //一个方法对应一个disposable
        Disposable disposable = postToObservable(observable, subscriberMethod)
                .subscribe(new Consumer() {
                    @Override
                    public void accept(Object eventObj) throws Exception {
                        callEvent(subscriberMethod, eventObj);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {

                    }
                });
        addDisposableToMap(subscriberMethod.subscriber, disposable);
    }

    /**
     * 过滤，根据给定的eventType类型返回特定类型(eventType)的被观察者
     *
     * @param eventType
     * @param <T>
     * @return
     */
    public <T> Observable<T> toObservable(Class<T> eventType) {
        return bus.ofType(eventType);
    }

    /**
     * 过滤，根据给定的code和eventType类型返回特定类型(eventType)的被观察者
     *
     * @param code
     * @param eventType
     * @param <T>
     * @return
     */
    public <T> Observable<T> toObservable(final int code, final Class<T> eventType) {

        return bus.ofType(Message.class).filter(new Predicate<Message>() {
            @Override
            public boolean test(Message o) throws Exception {
                //(1)A instanceof B:表示B是否拥有子类或本身类
                //(2)B.class.isInstance(A):表示A是否能强转成B，(1)和(2)只是用法的区别。
                return o.getCode() == code && eventType.isInstance(o.getObject());
            }
        }).map(new Function<Message, Object>() {
            @Override
            public Object apply(Message o) throws Exception {
                return o.getObject();
            }
        }).cast(eventType);
    }


    /**
     * 过滤，根据给定的eventType类型返回特定类型(eventType)的被观察者
     *
     * @param eventType
     * @param <T>
     * @return
     */
    public <T> Observable<T> toObservableSticky(final Class<T> eventType) {
        synchronized (stickyEvent) {
            Observable<T> observable = bus.ofType(eventType);
            final Object event = stickyEvent.get(eventType);
            if (event != null) {
                //mergeWith：把多个异步任务合并，等到所有任务完成后发送事件。
                //flatMap：解决多个任务的嵌套，整个流程是同步操作。1->2->3->4
                return observable.mergeWith(Observable.create(new ObservableOnSubscribe<T>() {
                    @Override
                    public void subscribe(ObservableEmitter<T> e) throws Exception {
                        e.onNext(eventType.cast(event));
                    }
                }));

            } else {
                return observable;
            }
        }
    }

    /**
     * 用于处理订阅事件在哪个线程中执行
     *
     * @param observable
     * @param subscriberMethod
     * @return
     */
    private Observable postToObservable(Observable observable, SubscriberMethod subscriberMethod) {
        Observable ob;
        switch (subscriberMethod.threadMode) {
            case MAIN:
                ob = observable.observeOn(AndroidSchedulers.mainThread());
                break;
            case NEW_THREAD:
                //每次都新建一个线程来执行任务，效率低
                ob = observable.observeOn(Schedulers.newThread());
                break;
            case CURRENT_THREAD:
                //暂停当前线程未完成的任务，马上执行插入的任务，完成后再继续执行未完成的任务。
                ob = observable.observeOn(Schedulers.trampoline());
                break;
            case IO:
                //相比newThread，具有线程缓存机制效率比较高
                ob = observable.observeOn(Schedulers.io());
                break;
            default:
                //Schedulers.computation()，用于cpu密集型计算任务，如XML、Json的解析，Bitmap的压缩取样等
                //Schedulers.from(excutor)，指定一个线程池来控制任务的执行策略，
                //Schedulers.single()，单例线程，所有的任务都在这个线程中执行，按照先进先出的顺序。
                throw new IllegalStateException("unknown thread mode:" + subscriberMethod.threadMode);
        }
        return ob;
    }

    /**
     * 回调订阅者的方法
     *
     * @param subscriberMethod
     * @param eventObject      事件类
     */
    private void callEvent(SubscriberMethod subscriberMethod, Object eventObject) {
        Class eventType = eventObject.getClass();
        List<SubscriberMethod> methods = subscriberMethodByEventType.get(eventType);
        if (methods != null && methods.size() > 0) {
            for (SubscriberMethod method : methods) {
                //Subscribe sub = method.method.getAnnotation(Subscribe.class);
                if (method.code == subscriberMethod.code && method.subscriber == subscriberMethod.subscriber) {
                    //回调所有参数类型相同且code相同的注解方法，范围所有类。
                    method.invoke(eventObject);//同个类，根据参数类型来区分调用方法。
                }
            }
        }
    }

    /**
     * 按注册类保存disposable，用于取消订阅。
     *
     * @param subscriber
     * @param disposable
     */
    private void addDisposableToMap(Object subscriber, Disposable disposable) {
        List<Disposable> disposables = disposablesBySubscriber.get(subscriber);
        if (disposables == null) {
            disposables = new ArrayList<>();
            disposablesBySubscriber.put(subscriber, disposables);
        }

        if (!disposables.contains(disposable)) {
            disposables.add(disposable);
        }
    }

    /**
     * 发送一个事件
     *
     * @param event
     */
    public void post(Object event) {
        synchronized (stickyEvent) {
            stickyEvent.put(event.getClass(), event);
        }
        bus.onNext(event);
    }

    /**
     * 发送一个事件，只有code相同才能接收
     *
     * @param code
     * @param event
     */
    public void post(int code, Object event) {
        bus.onNext(new Message(code, event));
    }

    /**
     * 取消注册
     *
     * @param subcriber
     */
    public void unregister(Object subcriber) {
        Class<?> subcriberClass = subcriber.getClass();
        unDisposablesBySubcriber(subcriber);
        List<Class> eventTypes = eventTypesBySubscriber.get(subcriberClass);//注册类中包含的事件类型集合
        if (eventTypes == null || eventTypes.size() == 0) {
            return;
        }
        for (Class eventType : eventTypes) {
            unSubscribeMethodByEventType(subcriber, eventType);//根据事件类型和注册类移除注解方法
        }
        eventTypesBySubscriber.remove(subcriberClass);
    }

    /**
     * 取消指定注册类的所有订阅
     *
     * @param subscriber 订阅者
     */
    private void unDisposablesBySubcriber(Object subscriber) {
        List<Disposable> disposables = disposablesBySubscriber.get(subscriber);
        if (disposables != null) {
            Iterator<Disposable> iterator = disposables.iterator();
            while (iterator.hasNext()) {
                Disposable disposable = iterator.next();
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 移除注解方法：根据事件类型找到所有包含该事件的注解方法，然后遍历对比注册类删除，作用原理见callEvent方法。
     *
     * @param subcriber 注册类
     * @param eventType 事件类型
     */
    private void unSubscribeMethodByEventType(Object subcriber, Class eventType) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods != null) {
            Iterator<SubscriberMethod> iterator = subscriberMethods.iterator();
            while (iterator.hasNext()) {
                SubscriberMethod subscriberMethod = iterator.next();
                if (subscriberMethod.subscriber.equals(subcriber)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 移除指定eventType类型的sticky事件
     *
     * @param eventType
     * @param <T>
     * @return
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvent) {
            return eventType.cast(stickyEvent.remove(eventType));
        }
    }

    /**
     * 移除所有的sticky事件
     */
    public void removeAllStickyEvent() {
        synchronized (stickyEvent) {
            stickyEvent.clear();
        }
    }

}
