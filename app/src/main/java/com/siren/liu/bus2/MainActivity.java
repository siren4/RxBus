package com.siren.liu.bus2;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.siren.liu.bus.RxBus;
import com.siren.liu.bus.annotation.Subscribe;
import com.siren.liu.bus.mode.ThreadMode;
import com.siren.liu.bus2.sample.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RxBus.get().register(this);
        RxBus.get().post(new RxBusEvent());
    }

    @Override
    protected void onDestroy() {
        RxBus.get().unregister(this);
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.NEW_THREAD)
    public void onRxBusEvent(RxBusEvent rxBusEvent) {
        Log.d("siren", "++onRxBusEvent()++");
    }

}