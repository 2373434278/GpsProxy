// IBLocationManagerService.aidl
package top.niunaijun.blackbox.core.system.location;

import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BCell;

import java.util.List;


/**
 * 容器定位代理服务接口。
 *
 * <p>所有方法由宿主主进程中的 {@code BLocationManagerService} 实现，
 * 容器进程通过 {@code BLocationManager} 以 Binder 方式调用。</p>
 */
interface IBLocationManagerService {
    /** 读取指定应用的模拟模式（CLOSE / GLOBAL / OWN）。 */
    int getPattern(int userId, String pkg);

    /** 设置指定应用的模拟模式。 */
    void setPattern(int userId, String pkg, int mode);

    /** 设置指定应用的专属主基站。 */
    void setCell(int userId, String pkg,in  BCell cell);

    /** 批量设置指定应用的基站列表，供 getAllCellInfo 使用。 */
    void setAllCell(int userId, String pkg,in  List<BCell> cell);

    /** 设置指定应用的邻区列表。 */
    void setNeighboringCell(int userId, String pkg,in  List<BCell> cells);
    /** 读取指定应用的邻区列表。 */
    List<BCell> getNeighboringCell(int userId, String pkg);

    /** 设置全局主基站，对所有处于 GLOBAL_MODE 的应用生效。 */
    void setGlobalCell(in BCell cell);

    /** 设置全局基站列表。 */
    void setGlobalAllCell(in List<BCell> cell);

    /** 设置全局邻区列表。 */
    void setGlobalNeighboringCell(in List<BCell> cell);

    /** 读取全局邻区列表。 */
    List<BCell> getGlobalNeighboringCell();

    /** 读取指定应用当前生效的主基站。 */
    BCell getCell(int userId, String pkg);

    /** 读取指定应用当前生效的基站列表。 */
    List<BCell> getAllCell(int userId, String pkg);

    /** 写入指定应用的专属坐标。 */
    void setLocation(int userId, String pkg,in  BLocation location);

    /** 读取指定应用当前生效的坐标。 */
    BLocation getLocation(int userId, String pkg);

    /** 写入全局坐标，是所有 GLOBAL_MODE 应用共享的数据源。 */
    void setGlobalLocation(in BLocation location);

    /** 读取全局坐标。 */
    BLocation getGlobalLocation();

    /**
     * 极简登记接口，保留历史签名以兼容既有调用方。
     * 新代码应使用 requestLocationUpdatesWithOptions，
     * 以便服务端按请求的提供者构造 Location。
     */
    void requestLocationUpdates(in IBinder listener, String packageName, int userId);

    /**
     * 带请求参数的登记接口。
     *
     * @param provider     请求的定位提供者，null 表示未指定
     * @param minTimeMs    请求的最小更新间隔（毫秒）
     * @param minDistanceM 请求的最小位移（米）
     */
    void requestLocationUpdatesWithOptions(in IBinder listener, String packageName, int userId,
            String provider, long minTimeMs, float minDistanceM);

    /** 注销监听。 */
    void removeUpdates(in IBinder listener);
}
