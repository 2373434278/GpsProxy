package top.niunaijun.blackbox.fake.service.cell;

import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import top.niunaijun.blackbox.entity.location.BCell;

/**
 * 把 {@link BCell} 转换成容器应用可直接使用的系统电话对象。
 *
 * <p>{@code TelephonyManager} 的相关接口返回的是框架类
 * （{@link GsmCellLocation} / {@link NeighboringCellInfo} / {@link CellInfo}），
 * 直接把 {@link BCell} 丢回去会触发 {@code ClassCastException}。
 * 这里统一通过反射构造这些对象，屏蔽不同 Android 版本的构造器差异。</p>
 *
 * <p>整个类遵循「失败即降级」原则：任一构造步骤异常都返回 null 或空列表，
 * 由调用方决定是透传真实值还是返回空，绝不会把异常抛给容器应用。</p>
 */
public final class CellInfoFactory {

    private static final String TAG = "CellInfoFactory";

    private CellInfoFactory() {
    }

    /**
     * 构造 {@link GsmCellLocation}。
     *
     * @return 构造成功返回实例；平台缺少对应字段（例如纯 CDMA 设备）时返回 null
     */
    public static CellLocation createCellLocation(BCell cell) {
        if (cell == null || !cell.isValid()) {
            return null;
        }
        try {
            GsmCellLocation location = new GsmCellLocation();
            // setLac/setCid 是 @UnsupportedAppUsage 隐藏 API，只能反射调用
            invokeIntSetter(location, "setLacAndCid", cell.LAC, cell.CID);
            if (location.getLac() == -1 && location.getCid() == -1) {
                // 双参方法不可用时退化为单参两次调用
                invokeIntSetter(location, "setLac", cell.LAC);
                invokeIntSetter(location, "setCid", cell.CID);
            }
            return location;
        } catch (Throwable t) {
            Log.w(TAG, "createCellLocation failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 构造邻区列表。
     *
     * <p>{@link NeighboringCellInfo} 的公开构造器自 API 29 起被移除，
     * 因此在较新系统上返回空列表，由调用方回落到 {@link #createCellInfoList}。</p>
     */
    public static List<NeighboringCellInfo> createNeighboringCells(List<BCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return Collections.emptyList();
        }
        List<NeighboringCellInfo> result = new ArrayList<>(cells.size());
        for (BCell cell : cells) {
            if (cell == null || !cell.isValid()) continue;
            try {
                // 公开可反射的构造器签名：(int rssi, int cid)
                Constructor<NeighboringCellInfo> constructor =
                        NeighboringCellInfo.class.getDeclaredConstructor(int.class, int.class);
                constructor.setAccessible(true);
                NeighboringCellInfo info = constructor.newInstance(cell.signalStrengthDbm, cell.CID);
                // LAC 只能写字段，方法在部分版本被移除
                setIntField(info, "mLac", cell.LAC);
                result.add(info);
            } catch (Throwable ignored) {
                // 该版本无可用构造器，交由 CellInfo 通道兜底
            }
        }
        return result;
    }

    /**
     * 构造 {@link CellInfo} 列表，适配 API 17+ 的 {@code getAllCellInfo}。
     *
     * <p>优先构造 GSM 制式的 {@code CellInfoGsm}：主小区 marked registered，
     * 其余为邻区。构造失败时返回空列表。</p>
     */
    public static List<CellInfo> createCellInfoList(List<BCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return Collections.emptyList();
        }
        List<CellInfo> result = new ArrayList<>(cells.size());
        for (BCell cell : cells) {
            if (cell == null || !cell.isValid()) continue;
            CellInfo info = createGsmCellInfo(cell);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 反射构造单个 {@code CellInfoGsm} 并填充标识与信号字段。
     *
     * <p>字段名随 Android 版本变化（M 为 mCellIdentityGsm，Q 起为 mCellIdentity），
     * 因此逐个尝试已知字段名，命中即写。</p>
     */
    private static CellInfo createGsmCellInfo(BCell cell) {
        try {
            Class<?> cellInfoGsmClass = Class.forName("android.telephony.CellInfoGsm");
            Object cellInfo = cellInfoGsmClass.getDeclaredConstructor().newInstance();

            Object identity = buildGsmIdentity(cell);
            Object signal = buildGsmSignal(cell);

            if (identity != null) {
                if (!setField(cellInfo, "mCellIdentity", identity)) {
                    setField(cellInfo, "mCellIdentityGsm", identity);
                }
            }
            if (signal != null) {
                if (!setField(cellInfo, "mCellSignalStrength", signal)) {
                    setField(cellInfo, "mCellSignalStrengthGsm", signal);
                }
            }
            // 是否注册到该小区：字段名在 R 之前为 mRegistered，之后并入位标记
            setField(cellInfo, "mRegistered", cell.registered);
            return (CellInfo) cellInfo;
        } catch (Throwable t) {
            Log.w(TAG, "createGsmCellInfo failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    /** 构造 {@code CellIdentityGsm}，字段名优先用新版 mCid/mLac，回退旧版 mCidOrBsid。 */
    private static Object buildGsmIdentity(BCell cell) {
        try {
            Class<?> identityClass = Class.forName("android.telephony.CellIdentityGsm");
            Object identity = identityClass.getDeclaredConstructor().newInstance();
            setIntField(identity, "mMcc", cell.MCC);
            setIntField(identity, "mMnc", cell.MNC);
            if (!setIntField(identity, "mLac", cell.LAC)) {
                setIntField(identity, "mLacOrTac", cell.LAC);
            }
            if (!setIntField(identity, "mCid", cell.CID)) {
                setIntField(identity, "mCidOrBsid", cell.CID);
            }
            return identity;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 构造 {@code CellSignalStrengthGsm}，写入 dBm 与等级。 */
    private static Object buildGsmSignal(BCell cell) {
        try {
            Class<?> signalClass = Class.forName("android.telephony.CellSignalStrengthGsm");
            Object signal = signalClass.getDeclaredConstructor().newInstance();
            setIntField(signal, "mSignalStrength", cell.signalStrengthDbm);
            setIntField(signal, "mLevel", cell.signalLevel);
            setIntField(signal, "mBitErrorRate", -1);
            return signal;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void invokeIntSetter(Object target, String methodName, int value) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, int.class);
            method.setAccessible(true);
            method.invoke(target, value);
        } catch (Throwable ignored) {
            // 方法不存在时由调用方判定是否回退
        }
    }

    private static void invokeIntSetter(Object target, String methodName, int first, int second) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, int.class, int.class);
            method.setAccessible(true);
            method.invoke(target, first, second);
        } catch (Throwable ignored) {
            // 同上
        }
    }

    private static boolean setIntField(Object target, String fieldName, int value) {
        return setField(target, fieldName, value);
    }

    /**
     * 写入对象字段。
     *
     * @return 是否写入成功；字段在该系统版本上不存在时返回 false
     */
    private static boolean setField(Object target, String fieldName, Object value) {
        if (target == null) return false;
        try {
            Class<?> type = target.getClass();
            // 字段可能声明在父类上，需要逐级向上查找
            while (type != null) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return true;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
            // 写入失败按「字段不存在」处理
        }
        return false;
    }
}
