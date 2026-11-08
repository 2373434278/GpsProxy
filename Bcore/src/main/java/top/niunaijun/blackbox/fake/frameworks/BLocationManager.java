package top.niunaijun.blackbox.fake.frameworks;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BLocation;


/**
 * 容器进程侧的定位代理门面。
 *
 * <p>该类运行在容器（沙箱）进程中，把定位相关调用通过 Binder 转发到
 * 宿主主进程的 {@code BLocationManagerService}。所有方法都做了异常隔离：
 * 服务不可达（Binder 断开、宿主被杀）时返回安全默认值，
 * 让上层 Hook 逻辑可以无脑透传真实定位，而不是直接崩溃。</p>
 */
public class BLocationManager extends BlackManager<IBLocationManagerService> {
    private static final BLocationManager sLocationManager = new BLocationManager();

    /** 关闭模拟：透传真实定位。 */
    public static final int CLOSE_MODE = 0;
    /** 全局模式：使用宿主下发的全局坐标。 */
    public static final int GLOBAL_MODE = 1;
    /** 专属模式：使用为本应用单独配置的坐标。 */
    public static final int OWN_MODE = 2;

    public static BLocationManager get() {
        return sLocationManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.LOCATION_MANAGER;
    }

    /** 当前容器应用是否开启了定位模拟。 */
    public static boolean isFakeLocationEnable() {
        return get().getPattern(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) != CLOSE_MODE;
    }

    public static void disableFakeLocation(int userId,String pkg){
        get().setPattern(userId,pkg,CLOSE_MODE);
    }

    public void setPattern(int userId, String pkg, int pattern) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setPattern(userId, pkg, pattern);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int getPattern(int userId, String pkg) {
        IBLocationManagerService service = getService();
        if (service == null) return CLOSE_MODE;
        try {
            return service.getPattern(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return CLOSE_MODE;
    }

    public void setCell(int userId, String pkg, BCell cell) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setCell(userId, pkg, cell);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setAllCell(int userId, String pkg, List<BCell> cells) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setAllCell(userId, pkg, cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<BCell> getNeighboringCell(int userId, String pkg) {
        IBLocationManagerService service = getService();
        if (service == null) return null;
        try {
            return service.getNeighboringCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BCell> getGlobalNeighboringCell() {
        IBLocationManagerService service = getService();
        if (service == null) return null;
        try {
            return service.getGlobalNeighboringCell();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setNeighboringCell(int userId, String pkg, List<BCell> cells) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setNeighboringCell(userId, pkg, cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalCell(BCell cell) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setGlobalCell(cell);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalAllCell(List<BCell> cells) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setGlobalAllCell(cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalNeighboringCell(List<BCell> cells) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setGlobalNeighboringCell(cells);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BCell getCell(int userId, String pkg) {
        IBLocationManagerService service = getService();
        if (service == null) return null;
        try {
            return service.getCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BCell> getAllCell(int userId, String pkg) {
        IBLocationManagerService service = getService();
        if (service == null) return new ArrayList<>();
        try {
            return service.getAllCell(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void setLocation(int userId, String pkg, BLocation location) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.setLocation(userId, pkg, location);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BLocation getLocation(int userId, String pkg) {
        IBLocationManagerService service = getService();
        if (service == null) return null;
        try {
            return service.getLocation(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 写入全局坐标。
     *
     * @return 写入是否成功；服务不可达或 Binder 异常时返回 false，
     *         调用方（宿主推送逻辑）据此清理失效 Binder 缓存并重试。
     */
    public boolean setGlobalLocation(BLocation location) {
        try {
            IBLocationManagerService service = getService();
            if (service == null) {
                return false;
            }
            service.setGlobalLocation(location);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public BLocation getGlobalLocation() {
        IBLocationManagerService service = getService();
        if (service == null) return null;
        try {
            return service.getGlobalLocation();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** 极简登记：不携带请求参数，服务端按 GPS 下发。 */
    public void requestLocationUpdates(IBinder listener) {
        requestLocationUpdates(listener, null, 0L, 0f);
    }

    /**
     * 带请求参数的登记。
     *
     * @param provider     容器应用请求的提供者，null 表示未指定
     * @param minTimeMs    请求的最小更新间隔（毫秒）
     * @param minDistanceM 请求的最小位移（米）
     */
    public void requestLocationUpdates(IBinder listener, String provider, long minTimeMs, float minDistanceM) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.requestLocationUpdatesWithOptions(
                    listener,
                    BActivityThread.getAppPackageName(),
                    BActivityThread.getUserId(),
                    provider,
                    minTimeMs,
                    minDistanceM);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void removeUpdates(IBinder listener) {
        IBLocationManagerService service = getService();
        if (service == null) return;
        try {
            service.removeUpdates(listener);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
