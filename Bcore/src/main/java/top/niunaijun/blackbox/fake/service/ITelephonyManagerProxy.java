package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.os.BRServiceManager;
import black.com.android.internal.telephony.BRITelephonyStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.fake.service.cell.CellInfoFactory;
import top.niunaijun.blackbox.utils.Md5Utils;


public class ITelephonyManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ITelephonyManagerProxy";

    public ITelephonyManagerProxy() {
        super(BRServiceManager.get().getService(Context.TELEPHONY_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder telephony = BRServiceManager.get().getService(Context.TELEPHONY_SERVICE);
        return BRITelephonyStub.get().asInterface(telephony);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getDeviceId")
    public static class GetDeviceId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {


            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    @ProxyMethod("getImeiForSlot")
    public static class getImeiForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {


            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    @ProxyMethod("getMeidForSlot")
    public static class GetMeidForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {


            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    @ProxyMethod("isUserDataEnabled")
    public static class IsUserDataEnabled extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }


    @ProxyMethod("getLine1NumberForDisplay")
    public static class getLine1NumberForDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ProxyMethod("getSubscriberId")
    public static class GetSubscriberId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    @ProxyMethod("getDeviceIdWithFeature")
    public static class GetDeviceIdWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Md5Utils.md5(BlackBoxCore.getHostPkg());
        }
    }

    /**
     * 单基站查询。
     *
     * <p>模拟开启时返回与坐标同城的 {@code GsmCellLocation}。
     * 早期实现直接返回 null，导致依赖基站辅助定位的应用认为信号缺失，
     * 在拿到坐标后仍报「定位失败」。</p>
     */
    @ProxyMethod("getCellLocation")
    public static class GetCellLocation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getCellLocation");
            if (BLocationManager.isFakeLocationEnable()) {
                BCell cell = BLocationManager.get()
                        .getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (cell != null) {
                    CellLocation fake = CellInfoFactory.createCellLocation(cell);
                    if (fake != null) {
                        return fake;
                    }
                }
            }
            return method.invoke(who, args);
        }
    }

    /**
     * 全量小区信息。
     *
     * <p>必须返回 {@code List<CellInfo>}，之前直接把内部实体列表返回给应用，
     * 任何读取操作都会因类型不匹配而崩溃。</p>
     */
    @ProxyMethod("getAllCellInfo")
    public static class GetAllCellInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                List<BCell> cells = BLocationManager.get()
                        .getAllCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                List<CellInfo> infos = CellInfoFactory.createCellInfoList(cells);
                if (!infos.isEmpty()) {
                    return infos;
                }
            }
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                return Collections.emptyList();
            }
        }
    }

    /**
     * 运营商编码，返回与模拟基站一致的 MCC+MNC。
     *
     * <p>部分应用会把 {@code getNetworkOperator} 与基站 MCC/MNC 做一致性校验，
     * 二者不一致同样会被判为伪造。</p>
     */
    @ProxyMethod("getNetworkOperator")
    @ProxyMethods({"getSimOperator", "getNetworkOperatorForPhone"})
    public static class GetNetworkOperator extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getNetworkOperator");
            if (BLocationManager.isFakeLocationEnable()) {
                BCell cell = BLocationManager.get()
                        .getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (cell != null && cell.isValid()) {
                    return String.format(java.util.Locale.US, "%03d%02d", cell.MCC, cell.MNC);
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getNetworkTypeForSubscriber")
    public static class GetNetworkTypeForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                return 0;
            }
        }
    }

    /**
     * 邻区列表。
     *
     * <p>{@code NeighboringCellInfo} 的构造器在新版系统被移除，
     * 构造失败时改用 {@code CellInfo} 列表，保证应用至少能读到一个合法结果。</p>
     */
    @ProxyMethod("getNeighboringCellInfo")
    public static class GetNeighboringCellInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "getNeighboringCellInfo");
            if (BLocationManager.isFakeLocationEnable()) {
                List<BCell> cells = BLocationManager.get()
                        .getNeighboringCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                List<NeighboringCellInfo> legacy = CellInfoFactory.createNeighboringCells(cells);
                if (!legacy.isEmpty()) {
                    return legacy;
                }
                List<CellInfo> infos = CellInfoFactory.createCellInfoList(cells);
                if (!infos.isEmpty()) {
                    return infos;
                }
            }
            return method.invoke(who, args);
        }
    }
}
