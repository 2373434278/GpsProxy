package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.location.LocationManager;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import black.android.location.BRILocationManagerStub;
import black.android.location.provider.BRProviderProperties;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


/**
 * 容器内 {@code ILocationManager} 系统服务的代理。
 *
 * <p>容器应用调用 {@code LocationManager} 的任何 API，最终都会打到系统
 * 的 {@code ILocationManager} Binder。本类把这个 Binder 替换成动态代理，
 * 在模拟开启时返回伪造坐标，在模拟关闭时原样透传真实结果。</p>
 *
 * <p>覆盖范围：</p>
 * <ul>
 *   <li>一次性查询：{@code getLastLocation / getLastKnownLocation / getCurrentLocation}</li>
 *   <li>持续监听：{@code requestLocationUpdates / registerLocationListener}</li>
 *   <li>提供者探测：{@code getBestProvider / getAllProviders / isProviderEnabledForUser}</li>
 *   <li>能力上报：{@code getProviderProperties}（去掉网络/基站依赖，
 *       让系统认为 GPS 可在纯伪造环境下工作）</li>
 *   <li>GNSS 回调：{@code registerGnssStatusCallback / removeGpsStatusListener}
 *       直接短路，避免 GNSS 未就绪时上层等待超时</li>
 * </ul>
 */
public class ILocationManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ILocationManagerProxy";

    /** 容器内 fused 提供者名称，部分 ROM 用它区分系统融合定位。 */
    private static final String FUSED_PROVIDER = "fused";

    public ILocationManagerProxy() {
        super(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRILocationManagerStub.get().asInterface(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // 系统侧会校验调用方包名，容器应用的真实包名需要替换成宿主签名可用的包名
        MethodParameterUtils.replaceFirstAppPkg(args);


        String packageName = BActivityThread.getAppPackageName();
        if (packageName != null && packageName.equals("com.google.android.gms")) {

            // GMS 在容器内缺少完整运行环境，把定位请求真的交给系统会触发空指针崩溃。
            // 但只在模拟关闭时拦截：模拟开启时坐标由下面的 Hook 直接产出，
            // 根本不会走到系统实现，此时拦截反而会让容器内的地图类应用拿不到位置。
            if (!BLocationManager.isFakeLocationEnable()
                    && (method.getName().equals("getLastLocation")
                        || method.getName().equals("getLastKnownLocation")
                        || method.getName().equals("requestLocationUpdates"))) {
                Log.w(TAG, "Blocking location request from Google Play Services to prevent crash");
                return null;
            }
        }

        return super.invoke(proxy, method, args);
    }

    /**
     * GNSS 状态回调直接返回成功。
     *
     * <p>容器内没有真实 GNSS 硬件会话，交给系统会一直等不到首个回调，
     * 部分导航应用会因此卡在启动页。</p>
     */
    @ProxyMethod("registerGnssStatusCallback")
    public static class RegisterGnssStatusCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {

            return true;
        }
    }

    @ProxyMethod("getLastLocation")
    public static class GetLastLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation location = BLocationManager.get()
                        .getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (location != null) {
                    // 一次性查询默认使用 GPS 提供者，与真实 getLastLocation 的常见返回一致
                    return location.convert2SystemLocation(BLocation.PROVIDER_GPS);
                }
            }


            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied, returning null for getLastLocation");
                    return null;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getLastKnownLocation")
    public static class GetLastKnownLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation location = BLocationManager.get()
                        .getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (location != null) {
                    // 该接口按调用方指定的 provider 返回，args[0] 即提供者名
                    String provider = extractProvider(args);
                    return location.convert2SystemLocation(provider);
                }
            }


            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied, returning null for getLastKnownLocation");
                    return null;
                }
                throw e;
            }
        }
    }

    /**
     * 持续定位请求。
     *
     * <p>模拟开启时不能把请求真的交给系统（会持续回真实坐标，与伪造值打架），
     * 因此改为在宿主侧登记监听，由 {@code BLocationManagerService} 主动推送。
     * 同时把调用方请求的 provider / minTime / minDistance 一并登记，
     * 让推送出去的 Location 与请求参数匹配。</p>
     */
    @ProxyMethod("requestLocationUpdates")
    @ProxyMethods({"registerLocationListener"})
    public static class RequestLocationUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                // Binder 签名随 Android 版本变化，listener 位置不固定，遍历查找
                for (Object arg : args) {
                    if (arg instanceof IInterface) {
                        String provider = extractProvider(args);
                        Long minTime = extractLong(args);
                        Float minDistance = extractFloat(args);
                        BLocationManager.get().requestLocationUpdates(
                                ((IInterface) arg).asBinder(),
                                provider,
                                minTime == null ? 0L : minTime,
                                minDistance == null ? 0f : minDistance);
                        return 0;
                    }
                }
                Log.w(TAG, "fake mode: no listener found in requestLocationUpdates, blocking real updates: "
                        + describeArgs(args));
                return 0;
            }


            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Log.w(TAG, "Location permission denied for requestLocationUpdates, returning 0");
                    return 0;
                }
                throw e;
            }
        }
    }

    /**
     * 一次性定位请求。
     *
     * <p>{@code getCurrentLocation} 的返回值是 void，坐标必须通过参数里的
     * 回调对象投递出去。早期实现直接 {@code return location}，
     * 调用方永远等不到回调，导航类应用会卡在「正在定位」。</p>
     *
     * <p>回调形态随版本变化：Android 11+ 是 {@code LocationConsumer}
     * （函数式接口，{@code accept(Location)}），Android 12+ 还有
     * {@code ILocationCallback}（Binder，{@code onLocationChanged(List)}）。
     * 这里按参数类型逐个尝试，找到就走，找不到也不回退真实定位，
     * 否则用户拿到的会是真实坐标而不是模拟坐标。</p>
     */
    @ProxyMethod("getCurrentLocation")
    public static class GetCurrentLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation location = BLocationManager.get()
                        .getLocation(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (location != null) {
                    android.location.Location systemLocation =
                            location.convert2SystemLocation(extractProvider(args));
                    if (deliverToCallback(args, systemLocation)) {
                        return null;
                    }
                    Log.w(TAG, "getCurrentLocation: fake enabled but no callback found: " + describeArgs(args));
                    return null;
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeUpdates")
    @ProxyMethods({"removeLocationListener"})
    public static class RemoveUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg instanceof IInterface) {
                    BLocationManager.get().removeUpdates(((IInterface) arg).asBinder());
                    return 0;
                }
            }
            return method.invoke(who, args);
        }
    }

    /**
     * 修正提供者能力描述。
     *
     * <p>系统默认认为 GPS 需要网络辅助与基站信息。伪造环境下两者都不存在，
     * 应用探测到「GPS 依赖网络但网络不可用」会直接放弃定位，
     * 因此这里把这两个依赖标记为 false。若未配置基站，还额外清掉基站依赖。</p>
     */
    @ProxyMethod("getProviderProperties")
    public static class GetProviderProperties extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object providerProperties = method.invoke(who, args);
            if (providerProperties != null && BLocationManager.isFakeLocationEnable()) {
                BRProviderProperties.get(providerProperties)._set_mHasNetworkRequirement(false);
                BCell cell = BLocationManager.get()
                        .getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName());
                if (cell == null) {
                    BRProviderProperties.get(providerProperties)._set_mHasCellRequirement(false);
                }
            }
            return providerProperties;
        }
    }

    @ProxyMethod("removeGpsStatusListener")
    public static class RemoveGpsStatusListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {

            return 0;
        }
    }

    @ProxyMethod("getBestProvider")
    public static class GetBestProvider extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                // 伪造环境下只保证 GPS 可用，其它提供者的返回结果不可信
                return LocationManager.GPS_PROVIDER;
            }
            return method.invoke(who, args);
        }
    }

    /**
     * 提供者列表。
     *
     * <p>真实系统在不同 ROM 上返回的列表差异很大，部分定制系统会隐藏 GPS。
     * 伪造模式统一暴露 GPS + Network，保证应用一定能选中一个可用提供者。</p>
     */
    @ProxyMethod("getAllProviders")
    public static class GetAllProviders extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                return Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER);
            }
            return method.invoke(who, args);
        }
    }

    /**
     * 提供者启用状态。
     *
     * <p>模拟开启时一律视为 GPS 已启用；否则应用会提示「请打开 GPS」
     * 并拒绝发起定位，伪造坐标再准也没用。</p>
     */
    @ProxyMethod("isProviderEnabledForUser")
    @ProxyMethods({"isProviderEnabled", "isLocationEnabledForUser", "isLocationEnabled"})
    public static class isProviderEnabledForUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String provider = extractProvider(args);
            if (BLocationManager.isFakeLocationEnable()) {
                if (provider == null) {
                    // 无 provider 参数的接口（isLocationEnabled*）返回整体可用
                    return true;
                }
                return Objects.equals(provider, LocationManager.GPS_PROVIDER)
                        || Objects.equals(provider, LocationManager.NETWORK_PROVIDER)
                        || Objects.equals(provider, FUSED_PROVIDER);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setExtraLocationControllerPackageEnabled")
    public static class setExtraLocationControllerPackageEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    // ---------------- 参数解析辅助 ----------------

    /**
     * 把一次性定位结果投递给 {@code getCurrentLocation} 的回调参数。
     *
     * <p>系统在该接口上用过三种回调形态，需要全部覆盖：</p>
     * <ul>
     *   <li>Android 11+：{@code java.util.function.Consumer<Location>}，
     *       由 LocationManager 内部包成 LocationConsumer，调 {@code accept}；</li>
     *   <li>Android 12+：{@code android.location.ILocationCallback} Binder，
     *       调 {@code onLocationChanged(List<Location>)}；</li>
     *   <li>部分 ROM 直接传 {@code LocationListener}，调
     *       {@code onLocationChanged(Location)}。</li>
     * </ul>
     *
     * @return 是否成功投递；false 表示没找到可识别的回调参数
     */
    private static boolean deliverToCallback(Object[] args, android.location.Location location) {
        if (args == null) return false;
        for (Object arg : args) {
            if (arg == null) continue;
            if (deliverConsumer(arg, location)) return true;
            if (deliverBinderCallback(arg, location)) return true;
            if (deliverListener(arg, location)) return true;
        }
        return false;
    }

    /** Consumer / LocationConsumer：直接调用 accept(Location)。 */
    private static boolean deliverConsumer(Object target, android.location.Location location) {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals("accept")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0].isAssignableFrom(android.location.Location.class)) {
                try {
                    m.invoke(target, location);
                    return true;
                } catch (Throwable ignored) {
                    // 参数类型匹配但调用失败时继续尝试其它回调形态
                }
            }
        }
        return false;
    }

    /**
     * 把 Binder 还原成接口对象。
     *
     * <p>Binder 回调的接口名在各版本不同（{@code ILocationCallback} /
     * {@code ILocationListener} 等），无法硬编码，因此按接口名的包内
     * {@code $Stub} 类反射调用 {@code asInterface}。失败时返回 null，
     * 由调用方跳过这个参数。</p>
     */
    private static Object asInterfaceQuietly(android.os.IBinder binder) {
        String[] candidates = {
                "android.location.ILocationCallback$Stub",
                "android.location.ILocationListener$Stub",
                "android.location.IGnssStatusListener$Stub",
        };
        for (String className : candidates) {
            try {
                Class<?> stub = Class.forName(className);
                Method asInterface = stub.getMethod("asInterface", android.os.IBinder.class);
                Object iface = asInterface.invoke(null, binder);
                if (iface != null) return iface;
            } catch (Throwable ignored) {
                // 该 ROM 不存在此接口，继续尝试下一个
            }
        }
        return null;
    }

    /** ILocationCallback 等 Binder 回调：调用 onLocationChanged(List)。 */
    private static boolean deliverBinderCallback(Object target, android.location.Location location) {
        Object iface = target;
        if (target instanceof android.os.IBinder) {
            iface = asInterfaceQuietly((android.os.IBinder) target);
            if (iface == null) return false;
        }
        for (Method m : iface.getClass().getMethods()) {
            if (!m.getName().equals("onLocationChanged")) continue;
            Class<?>[] p = m.getParameterTypes();
            try {
                if (p.length == 1 && p[0] == android.location.Location.class) {
                    m.invoke(iface, location);
                    return true;
                }
                if (p.length == 1 && List.class.isAssignableFrom(p[0])) {
                    m.invoke(iface, Collections.singletonList(location));
                    return true;
                }
                if (p.length == 2 && List.class.isAssignableFrom(p[0])) {
                    m.invoke(iface, Collections.singletonList(location), null);
                    return true;
                }
            } catch (Throwable ignored) {
                // 继续尝试下一个候选
            }
        }
        return false;
    }

    /** LocationListener：调用 onLocationChanged(Location)。 */
    private static boolean deliverListener(Object target, android.location.Location location) {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals("onLocationChanged")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == android.location.Location.class) {
                try {
                    m.invoke(target, location);
                    return true;
                } catch (Throwable ignored) {
                    // 继续尝试下一个候选
                }
            }
        }
        return false;
    }

    /**
     * 从参数表中提取定位提供者。
     *
     * <p>不同 Android 版本的 {@code requestLocationUpdates} 参数表差异极大
     * （provider 可能在位置 0、1，也可能被 {@code ProviderRequest} 包裹），
     * 因此不做位置假设，只把形如 gps / network / fused 的字符串当作提供者。</p>
     */
    private static String extractProvider(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                if (isKnownProvider(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isKnownProvider(String value) {
        return LocationManager.GPS_PROVIDER.equals(value)
                || LocationManager.NETWORK_PROVIDER.equals(value)
                || LocationManager.PASSIVE_PROVIDER.equals(value)
                || FUSED_PROVIDER.equals(value);
    }

    /** 提取第一个 long 参数，通常是 minTime（毫秒）。 */
    private static Long extractLong(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
            if (arg instanceof Integer) {
                return ((Integer) arg).longValue();
            }
        }
        return null;
    }

    /** 提取第一个 float 参数，通常是 minDistance（米）。 */
    private static Float extractFloat(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Float) {
                return (Float) arg;
            }
            if (arg instanceof Double) {
                return ((Double) arg).floatValue();
            }
        }
        return null;
    }

    /** 供诊断日志使用：把参数表压成可读字符串，避免打印超长 Binder 对象。 */
    static String describeArgs(Object[] args) {
        List<String> parts = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                if (arg == null) {
                    parts.add("null");
                } else if (arg instanceof IInterface) {
                    parts.add("IInterface");
                } else {
                    parts.add(String.valueOf(arg));
                }
            }
        }
        return parts.toString();
    }
}
