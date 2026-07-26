/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.policy;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

import com.android.internal.policy.KeyInterceptionInfo;
import com.android.server.wm.WindowManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;

public class DoubleTapToSleepListener implements PointerEventListener {
    private final Context mContext;
    private final PowerManager mPowerManager;
    private final WindowManagerInternal mWindowManagerInternal;
    private final GestureDetector mGestureDetector;

    public DoubleTapToSleepListener(Context context, WindowManagerInternal windowManagerInternal,
            ActivityTaskManagerInternal activityTaskManagerInternal, Handler handler) {
        mContext = context;
        mPowerManager = context.getSystemService(PowerManager.class);
        mWindowManagerInternal = windowManagerInternal;
        mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                triggerSleep(e);
                return true;
            }
        }, handler);
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
    }

    private void triggerSleep(MotionEvent e) {
        if (mPowerManager == null || !mPowerManager.isInteractive()) {
            return;
        }

        KeyInterceptionInfo info = mWindowManagerInternal.getFocusedWindowKeyInterceptionInfo();
        if (info == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivityAsUser(
                intent, 0, ActivityManager.getCurrentUser());
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return;
        }

        String homePkg = resolveInfo.activityInfo.packageName;

        int type = info.layoutParamsType;
        boolean isAppWindow = (type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                && type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW);

        if (isAppWindow && info.windowTitle != null && info.windowTitle.contains(homePkg)) {
            float zoom = mWindowManagerInternal.getFocusedWindowWallpaperZoom();
            if (zoom <= 0.001f) {
                long lastWidgetInteractionTime = mWindowManagerInternal.getLastWidgetInteractionTime();
                if (Math.abs(e.getEventTime() - lastWidgetInteractionTime) < 500) {
                    return;
                }
                mPowerManager.goToSleep(e.getEventTime());
                try {
                    android.view.WindowManagerGlobal.getWindowManagerService()
                            .lockNow(null);
                } catch (android.os.RemoteException ignored) {
                }
            }
        }
    }
}
