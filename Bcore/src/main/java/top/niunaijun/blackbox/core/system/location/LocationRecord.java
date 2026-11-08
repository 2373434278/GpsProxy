package top.niunaijun.blackbox.core.system.location;


/**
 * 一条「容器内定位监听」的登记信息。
 *
 * <p>容器应用调用 {@code LocationManager#requestLocationUpdates} 时，
 * 会把 Binder 回调对象交给 {@code BLocationManagerService} 登记成本类实例，
 * 之后服务端按固定节奏把模拟坐标回调进去。</p>
 *
 * <p>早期版本只记录包名与 userId，导致服务端无法区分
 * 「请求 GPS 的监听」与「请求网络定位的监听」，只能下发 GPS 提供者，
 * 部分只允许网络定位的应用会直接丢弃结果。这里补上请求参数，
 * 分发时可以按需构造对应提供者的 {@code Location}。</p>
 */
public class LocationRecord {

    /** 请求方包名。 */
    public String packageName;
    /** 请求方用户空间（多开用户区分）。 */
    public int userId;
    /** 请求的定位提供者；为空表示未指定，按 GPS 处理。 */
    public String provider;
    /** 请求的最小更新间隔（毫秒），仅用于诊断展示。 */
    public long minTimeMs;
    /** 请求的最小位移（米），仅用于诊断展示。 */
    public float minDistanceM;
    /** 上一次实际下发时间戳，用于避免高频重复回调打满 Binder。 */
    public long lastDispatchAt;
    /** 累计下发次数，诊断页与日志使用。 */
    public long dispatchCount;

    public LocationRecord(String packageName, int userId) {
        this(packageName, userId, null, 0L, 0f);
    }

    public LocationRecord(String packageName, int userId, String provider, long minTimeMs, float minDistanceM) {
        this.packageName = packageName;
        this.userId = userId;
        this.provider = provider;
        this.minTimeMs = minTimeMs;
        this.minDistanceM = minDistanceM;
    }

    /** 记录一次成功下发，返回累计次数。 */
    public long markDispatched(long now) {
        this.lastDispatchAt = now;
        this.dispatchCount++;
        return dispatchCount;
    }

    /** 请求的实际生效间隔：尊重调用方设置，但限制在 200ms 与 5s 之间防止刷爆。 */
    public long effectiveIntervalMs(long fallbackMs) {
        long interval = minTimeMs > 0 ? minTimeMs : fallbackMs;
        return Math.max(200L, Math.min(interval, 5000L));
    }
}
