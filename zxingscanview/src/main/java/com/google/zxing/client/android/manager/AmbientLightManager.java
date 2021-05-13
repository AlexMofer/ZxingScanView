/*
 * Copyright (C) 2015 AlexMofer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android.manager;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * 背光管理器
 * Created by Alex on 2016/11/28.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class AmbientLightManager implements SensorEventListener {
    public static final int MODE_AUTO = 0;
    public static final int MODE_OPEN = 1;
    public static final int MODE_CLOSE = 2;
    private static final float LUX_TOO_DARK = 45.0f;
    private static final float LUX_BRIGHT_ENOUGH = 450.0f;
    private final AmbientLightCallBack mCallBack;
    private int mMode;
    private boolean isResume = false;
    private SensorManager sensorManager;
    private float mMinLux;
    private float mMaxLux;

    public AmbientLightManager(Context context, AmbientLightCallBack callBack) {
        this(context, callBack, MODE_AUTO);
    }

    public AmbientLightManager(Context context, AmbientLightCallBack callBack, int mode) {
        mCallBack = callBack;
        setMode(mode);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        setMaxLux(LUX_BRIGHT_ENOUGH);
        setMinLux(LUX_TOO_DARK);
    }

    /**
     * 设置背光模式
     *
     * @param mode 背光模式
     */
    public void setMode(int mode) {
        if (mode != MODE_AUTO && mode != MODE_CLOSE && mode != MODE_OPEN)
            return;
        if (mode == mMode)
            return;
        mMode = mode;
        if (isResume) {
            setTorch();
        }
    }

    private void setTorch() {
        switch (mMode) {
            case MODE_AUTO:
                if (sensorManager != null) {
                    sensorManager.registerListener(this,
                            sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
                            SensorManager.SENSOR_DELAY_NORMAL);
                }
                break;
            case MODE_OPEN:
                if (sensorManager != null) {
                    sensorManager.unregisterListener(this);
                }
                if (mCallBack != null)
                    mCallBack.onChange(true);
                break;
            case MODE_CLOSE:
                if (sensorManager != null) {
                    sensorManager.unregisterListener(this);
                }
                if (mCallBack != null)
                    mCallBack.onChange(false);
                break;
        }
    }

    public void setMinLux(float mix) {
        mMinLux = mix;
    }

    public void setMaxLux(float max) {
        mMaxLux = max;
    }

    public void resume() {
        if (isResume)
            return;
        isResume = true;
        setTorch();
    }

    public void pause() {
        if (!isResume)
            return;
        isResume = false;
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
    }

    /**
     * 销毁
     */
    public void release() {
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
        sensorManager = null;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float ambientLightLux = sensorEvent.values[0];
        if (mCallBack != null) {
            if (ambientLightLux <= mMinLux) {
                mCallBack.onChange(true);
            } else if (ambientLightLux >= mMaxLux) {
                mCallBack.onChange(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * 背光状态变化回调
     */
    public interface AmbientLightCallBack {
        /**
         * 状态变化
         *
         * @param on 打开还是关闭
         */
        void onChange(boolean on);
    }
}
