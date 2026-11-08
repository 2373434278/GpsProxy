package top.niunaijun.blackbox.core.system.location;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.AtomicFile;
import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import black.android.location.BRILocationListener;
import black.android.location.BRILocationListenerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BLocationConfig;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;


/**
 * 容器定位代理服务的宿主端实现。
 *
 * <p>职责分三层：</p>
 * <ol>
 *   <li><b>配置存储</b>：按 userId + 包名保存模式（关闭 / 全局 / 专属）以及
 *       坐标、基站数据，并落盘到 {@code BEnvironment.getFakeLocationConf()}。</li>
 *   <li><b>读取解析</b>：容器进程通过 Binder 查询时，按模式决定回落到全局配置
 *       还是专属配置，关闭模式一律返回 null，让代理层透传真实定位。</li>
 *   <li><b>主动分发</b>：容器应用注册定位监听后，由后台线程按固定节奏把
 *       模拟坐标回调进容器，避免应用停更。</li>
 * </ol>
 */
public class BLocationManagerService extends IBLocationManagerService.Stub implements ISystemService {
    public static final String TAG = "BLocationManagerService";

    /** 单例：该服务在宿主主进程中唯一，:black 进程通过 Binder 访问它。 */
    private static final BLocationManagerService sService = new BLocationManagerService();

    /** 监听未声明 minTime 时的默认推送间隔（毫秒）。 */
    private static final long DEFAULT_DISPATCH_INTERVAL_MS = 1000L;

    /** 按 userId 分组的应用级配置：userId -> (包名 -> 配置)。 */
    private final SparseArray<HashMap<String, BLocationConfig>> mLocationConfigs = new SparseArray<>();

    /** 全局配置：所有处于 GLOBAL_MODE 的应用共享同一份坐标与基站。 */
    private final BLocationConfig mGlobalConfig = new BLocationConfig();

    /** 已注册的定位监听：Binder 回调 -> 登记信息。 */
    private final Map<IBinder, LocationRecord> mLocationListeners = new HashMap<>();

    /** 每个监听一个常驻分发线程，用缓存线程池避免线程数受限。 */
    private final Executor mThreadPool = Executors.newCachedThreadPool();

    public static BLocationManagerService get() {
        return sService;
    }

    /**
     * 取出（必要时创建）指定应用的配置。
     *
     * <p>新建时默认 CLOSE_MODE：未显式开启的应用必须透传真实定位，
     * 否则容器内所有应用都会被静默伪造坐标。</p>
     */
    private BLocationConfig getOrCreateConfig(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            HashMap<String, BLocationConfig> pkgs = mLocationConfigs.get(userId);
            if (pkgs == null) {
                pkgs = new HashMap<>();
                mLocationConfigs.put(userId, pkgs);
            }
            BLocationConfig config = pkgs.get(pkg);
            if (config == null) {
                config = new BLocationConfig();
                config.pattern = BLocationManager.CLOSE_MODE;
                pkgs.put(pkg, config);
            }
            return config;
        }
    }

    /**
     * 读取当前生效的配置：GLOBAL_MODE 返回全局配置，OWN_MODE 返回专属配置，
     * CLOSE_MODE 返回 null 表示不该伪造。
     */
    private BLocationConfig resolveEffective(int userId, String pkg) {
        BLocationConfig config = getOrCreateConfig(userId, pkg);
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                return config;
            case BLocationManager.GLOBAL_MODE:
                return mGlobalConfig;
            case BLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    public int getPattern(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            BLocationConfig config = getOrCreateConfig(userId, pkg);
            return config.pattern;
        }
    }

    /**
     * 只读取模式，不创建配置条目。
     *
     * <p>分发线程会周期性轮询模式，用 {@link #getPattern} 会为每个被轮询的
     * 包名在内存里留下空配置。未登记的应用按 CLOSE 处理即可。</p>
     */
    private int peekPattern(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            HashMap<String, BLocationConfig> pkgs = mLocationConfigs.get(userId);
            if (pkgs == null) {
                return BLocationManager.CLOSE_MODE;
            }
            BLocationConfig config = pkgs.get(pkg);
            return config == null ? BLocationManager.CLOSE_MODE : config.pattern;
        }
    }

    @Override
    public void setPattern(int userId, String pkg, int pattern) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).pattern = pattern;
            save();
        }
    }

    @Override
    public void setCell(int userId, String pkg, BCell cell) {
        synchronized (mLocationConfigs) {
            BLocationConfig config = getOrCreateConfig(userId, pkg);
            config.cell = cell;
            // 只给主基站时自动推导邻区，避免容器内邻区列表为空显得异常
            config.rebuildCellsFromPrimary();
            save();
        }
    }

    @Override
    public void setAllCell(int userId, String pkg, List<BCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public void setNeighboringCell(int userId, String pkg, List<BCell> cells) {
        // 历史实现写入的是 allCell，这里修正为只写邻区字段，两者语义不同
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).neighboringCellInfo = cells;
            save();
        }
    }

    @Override
    public List<BCell> getNeighboringCell(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            return getOrCreateConfig(userId, pkg).neighboringCellInfo;
        }
    }

    @Override
    public void setGlobalCell(BCell cell) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.cell = cell;
            mGlobalConfig.rebuildCellsFromPrimary();
            save();
        }
    }

    @Override
    public void setGlobalAllCell(List<BCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.allCell = cells;
            save();
        }
    }

    @Override
    public void setGlobalNeighboringCell(List<BCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.neighboringCellInfo = cells;
            save();
        }
    }

    @Override
    public List<BCell> getGlobalNeighboringCell() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.neighboringCellInfo;
        }
    }

    @Override
    public BCell getCell(int userId, String pkg) {
        BLocationConfig config = resolveEffective(userId, pkg);
        return config == null ? null : config.cell;
    }

    @Override
    public List<BCell> getAllCell(int userId, String pkg) {
        BLocationConfig config = resolveEffective(userId, pkg);
        return config == null ? null : config.allCell;
    }

    @Override
    public void setLocation(int userId, String pkg, BLocation location) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).location = location;
            save();
        }
    }

    @Override
    public BLocation getLocation(int userId, String pkg) {
        BLocationConfig config = resolveEffective(userId, pkg);
        return config == null ? null : config.location;
    }

    @Override
    public void setGlobalLocation(BLocation location) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.location = location;
            save();
        }
    }

    @Override
    public BLocation getGlobalLocation() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.location;
        }
    }

    @Override
    public void requestLocationUpdates(IBinder listener, String packageName, int userId) throws RemoteException {
        requestLocationUpdatesWithOptions(listener, packageName, userId, null, 0L, 0f);
    }

    @Override
    public void requestLocationUpdatesWithOptions(IBinder listener, String packageName, int userId,
            String provider, long minTimeMs, float minDistanceM) throws RemoteException {
        Slog.d(TAG, "requestLocationUpdates: pkg=" + packageName + " userId=" + userId
                + " provider=" + provider + " minTime=" + minTimeMs + " listener=" + listener);
        if (listener == null || !listener.pingBinder()) {
            return;
        }
        if (mLocationListeners.containsKey(listener))
            return;
        // 监听方进程被回收时清理登记，避免线程空转
        listener.linkToDeath(new DeathRecipient() {
            @Override
            public void binderDied() {
                listener.unlinkToDeath(this, 0);
                mLocationListeners.remove(listener);
            }
        }, 0);
        LocationRecord record = new LocationRecord(packageName, userId, provider, minTimeMs, minDistanceM);
        mLocationListeners.put(listener, record);
        addTask(listener);
    }

    @Override
    public void removeUpdates(IBinder listener) throws RemoteException {
        if (listener == null || !listener.pingBinder()) {
            return;
        }
        mLocationListeners.remove(listener);
    }

    /**
     * 为单个监听启动常驻分发线程。
     *
     * <p>分发策略：</p>
     * <ul>
     *   <li>模拟坐标缺失（关闭模式）时回落到真实 last-known 坐标，
     *       保证容器内应用不会因为拿不到位置而停止刷新。</li>
     *   <li>坐标未变化且距上次下发不足 3 秒时跳过，减少 Binder 压力。</li>
     *   <li>按监听登记的 provider 构造 Location，GPS 请求拿到 gps 提供者，
     *       网络请求拿到 network 提供者。</li>
     * </ul>
     */
    private void addTask(IBinder locationListener) {
        mThreadPool.execute(() -> {
            BLocation lastLocation = null;
            long l = System.currentTimeMillis();
            while (locationListener.pingBinder()) {
                IInterface iInterface = BRILocationListenerStub.get().asInterface(locationListener);
                LocationRecord locationRecord = mLocationListeners.get(locationListener);
                BLocation location = locationRecord == null ? null
                        : getLocation(locationRecord.userId, locationRecord.packageName);
                if (locationRecord == null || location == null) {
                    if (locationRecord != null) {
                        // 已开启模拟但宿主还没推第一帧坐标时，绝不能回落真实定位：
                        // 那会把设备的真实位置泄露给容器内应用，正是本通道要避免的。
                        // 这里只等待，等坐标到达后由下一轮循环正常下发。
                        boolean fakeEnabled = peekPattern(locationRecord.userId, locationRecord.packageName)
                                != BLocationManager.CLOSE_MODE;
                        if (fakeEnabled) {
                            if (System.currentTimeMillis() - l > 5000) {
                                l = System.currentTimeMillis();
                                Slog.d(TAG, "fake enabled but no fake location yet for "
                                        + locationRecord.packageName);
                            }
                            sleepQuietly(500);
                            continue;
                        }
                        // fake 确实关闭时才回落真实 last-known 坐标，避免容器内应用停更
                        final LocationRecord record = locationRecord;
                        final android.location.Location real = realLastLocation(record.provider);
                        if (real != null) {
                            Slog.d(TAG, "real fallback dispatch to " + record.packageName);
                            BlackBoxCore.get().getHandler().post(() -> {
                                try {
                                    dispatchLocation(iInterface, Collections.singletonList(real));
                                    record.markDispatched(System.currentTimeMillis());
                                } catch (Throwable t) {
                                    Slog.e(TAG, "dispatch real failed", t);
                                }
                            });
                        } else if (System.currentTimeMillis() - l > 5000) {
                            l = System.currentTimeMillis();
                            Slog.d(TAG, "no real location for " + record.packageName);
                        }
                    }
                    sleepQuietly(1000);
                    continue;
                }
                // 按调用方请求的更新间隔推送，未声明时默认 1s
                long interval = locationRecord.effectiveIntervalMs(DEFAULT_DISPATCH_INTERVAL_MS);
                if (location.equals(lastLocation) && (System.currentTimeMillis() - l) < Math.max(3000L, interval)) {
                    sleepQuietly(interval);
                    continue;
                }
                lastLocation = location;
                l = System.currentTimeMillis();
                // 提供者按监听登记值下发；未登记时沿用坐标自带提供者
                String provider = locationRecord.provider;
                final android.location.Location systemLocation = location.convert2SystemLocation(provider);
                final LocationRecord record = locationRecord;
                Slog.d(TAG, "dispatch fake location to " + record.packageName);
                BlackBoxCore.get().getHandler().post(() -> {
                    try {
                        dispatchLocation(iInterface, Collections.singletonList(systemLocation));
                        record.markDispatched(System.currentTimeMillis());
                    } catch (Throwable t) {
                        Slog.e(TAG, "dispatch failed", t);
                    }
                });
                sleepQuietly(interval);
            }
            mLocationListeners.remove(locationListener);
            Slog.d(TAG, "location listener task exit");
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // 线程被中断时由外层 while 条件自然退出
        }
    }

    /**
     * 把位置回调进容器内的监听者。
     *
     * <p>签名随系统版本变化：Android 14 起为
     * {@code onLocationChanged(List<Location>, IRemoteCallback)}，
     * 旧版本为 {@code onLocationChanged(Location)}；这里按参数表动态适配，
     * 避免不同 ROM 上回调直接失效。</p>
     */
    private static void dispatchLocation(IInterface listener, List<android.location.Location> locations) throws Exception {
        for (java.lang.reflect.Method m : listener.getClass().getMethods()) {
            if (!m.getName().equals("onLocationChanged")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == android.location.Location.class) {
                m.invoke(listener, locations.get(0));
                return;
            }
            if (p.length == 2 && List.class.isAssignableFrom(p[0])) {
                m.invoke(listener, locations, null);
                return;
            }
        }
        Slog.w(TAG, "no compatible onLocationChanged signature found");
    }

    /**
     * 读取宿主设备真实的最后已知位置，用于模拟关闭时回落到真实坐标。
     *
     * @param provider 优先尝试的提供者，失败时依次回退 GPS / NETWORK
     */
    private static android.location.Location realLastLocation(String provider) {
        try {
            android.content.Context context = BlackBoxCore.getContext();
            if (context == null) return null;
            android.location.LocationManager lm = (android.location.LocationManager)
                    context.getSystemService(android.content.Context.LOCATION_SERVICE);
            if (lm == null) return null;
            List<String> providers = new ArrayList<>();
            if (provider != null && !provider.isEmpty()) {
                providers.add(provider);
            }
            providers.add(android.location.LocationManager.GPS_PROVIDER);
            providers.add(android.location.LocationManager.NETWORK_PROVIDER);
            android.location.Location best = null;
            for (String name : providers) {
                try {
                    android.location.Location loc = lm.getLastKnownLocation(name);
                    if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                        best = loc;
                    }
                } catch (SecurityException | IllegalArgumentException ignored) {
                    // 无权限或该 ROM 不暴露此提供者时跳过
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 落盘配置。
     *
     * <p>先写全局配置，再写各 userId 的映射表；锁顺序固定为
     * {@code mGlobalConfig -> mLocationConfigs}，与 loadConfig 保持一致，避免死锁。</p>
     */
    public void save() {
        synchronized (mGlobalConfig) {
            synchronized (mLocationConfigs) {
                Parcel parcel = Parcel.obtain();
                AtomicFile atomicFile = new AtomicFile(BEnvironment.getFakeLocationConf());
                FileOutputStream fileOutputStream = null;
                try {
                    mGlobalConfig.writeToParcel(parcel, 0);

                    parcel.writeInt(mLocationConfigs.size());
                    for (int i = 0; i < mLocationConfigs.size(); i++) {
                        int tmpUserId = mLocationConfigs.keyAt(i);
                        HashMap<String, BLocationConfig> configArrayMap = mLocationConfigs.valueAt(i);
                        parcel.writeInt(tmpUserId);
                        parcel.writeMap(configArrayMap);
                    }
                    parcel.setDataPosition(0);
                    fileOutputStream = atomicFile.startWrite();
                    FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                    atomicFile.finishWrite(fileOutputStream);
                } catch (Throwable e) {
                    e.printStackTrace();
                    atomicFile.failWrite(fileOutputStream);
                } finally {
                    parcel.recycle();
                    CloseUtils.close(fileOutputStream);
                }
            }
        }
    }

    /**
     * 从磁盘恢复配置。
     *
     * <p>解析失败时直接删除配置文件：残留半截数据会导致每次启动都抛异常，
     * 丢失配置比反复崩溃更容易恢复。</p>
     */
    public void loadConfig() {
        Parcel parcel = Parcel.obtain();
        InputStream is = null;
        try {
            File fakeLocationConf = BEnvironment.getFakeLocationConf();
            if (!fakeLocationConf.exists()) {
                return;
            }
            is = new FileInputStream(fakeLocationConf);
            byte[] bytes = FileUtils.toByteArray(is);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            synchronized (mGlobalConfig) {
                mGlobalConfig.refresh(parcel);
            }

            synchronized (mLocationConfigs) {
                mLocationConfigs.clear();
                int size = parcel.readInt();
                for (int i = 0; i < size; i++) {
                    int userId = parcel.readInt();
                    HashMap<String, BLocationConfig> configArrayMap = parcel.readHashMap(BLocationConfig.class.getClassLoader());
                    mLocationConfigs.put(userId, configArrayMap);
                    Slog.d(TAG, "load userId: " + userId + ", config: " + configArrayMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Slog.d(TAG, "bad config");
            FileUtils.deleteDir(BEnvironment.getFakeLocationConf());
        } finally {
            parcel.recycle();
            CloseUtils.close(is);
        }
    }

    @Override
    public void systemReady() {
        loadConfig();
        // 进程重启后已有监听需要重新拉起分发线程
        for (IBinder iBinder : mLocationListeners.keySet()) {
            addTask(iBinder);
        }
    }
}
