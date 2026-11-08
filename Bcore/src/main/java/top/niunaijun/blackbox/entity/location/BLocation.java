
package top.niunaijun.blackbox.entity.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;


/**
 * 容器内定位数据载体。
 *
 * <p>该类是宿主（GpsProxy 主进程）与容器进程之间传递模拟坐标的唯一结构体，
 * 同时会被序列化进 {@link BLocationConfig} 落盘，因此字段顺序是兼容性约定：
 * 历史字段必须保持在前面，新字段只能追加在末尾。</p>
 *
 * <p>相比只保存经纬度，这里补齐了真实 Location 常见的元数据
 * （垂直精度 / 速度精度 / 方位精度 / 卫星数 / 定位提供者），
 * 目的是让容器内应用在读取 {@link Location} 时看到与真实定位一致的字段，
 * 而不是一堆 0，从而避免部分 SDK 因精度字段异常直接放弃定位结果。</p>
 */
public class BLocation implements Parcelable {

    /** GPS 定位提供者，与 {@link LocationManager#GPS_PROVIDER} 取值一致。 */
    public static final String PROVIDER_GPS = LocationManager.GPS_PROVIDER;

    /** 网络定位提供者，与 {@link LocationManager#NETWORK_PROVIDER} 取值一致。 */
    public static final String PROVIDER_NETWORK = LocationManager.NETWORK_PROVIDER;

    /** 未指定精度时的兜底水平精度（米），与真实定位的粗精度量级一致。 */
    private static final float DEFAULT_ACCURACY = 40f;

    /** 未配置卫星数时上报的默认值，避免定位 SDK 认为信号全无。 */
    private static final int DEFAULT_SATELLITES = 10;

    private double mLatitude = 0.0;
    private double mLongitude = 0.0;
    private double mAltitude = 0.0f;
    private float mSpeed = 0.0f;
    private float mBearing = 0.0f;
    private float mAccuracy = 0.0f;

    // ---- 以下为后加的扩展字段，必须保持追加顺序 ----

    /** 垂直精度（米），Android 8.0 起才有对应字段。 */
    private float mVerticalAccuracy = 0.0f;

    /** 速度精度（米/秒）。 */
    private float mSpeedAccuracy = 0.0f;

    /** 方位精度（度）。 */
    private float mBearingAccuracy = 0.0f;

    /** 该坐标被写入时的开机时长，0 表示使用当前值。 */
    private long mElapsedRealtimeNanos = 0L;

    /** 可见卫星数，部分导航类应用会读取该字段做卫星图渲染。 */
    private int mSatellites = DEFAULT_SATELLITES;

    /** 定位提供者，默认 GPS；按网络定位请求分发时会切换为 network。 */
    private String mProvider = PROVIDER_GPS;


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // 前 6 项是历史顺序，禁止调整，否则旧配置文件会被解析成错误坐标
        dest.writeDouble(this.mLatitude);
        dest.writeDouble(this.mLongitude);
        dest.writeDouble(this.mAltitude);
        dest.writeFloat(this.mSpeed);
        dest.writeFloat(this.mBearing);
        dest.writeFloat(this.mAccuracy);
        // 扩展字段追加在后，旧版本读取到末尾会拿到 0 / null
        dest.writeFloat(this.mVerticalAccuracy);
        dest.writeFloat(this.mSpeedAccuracy);
        dest.writeFloat(this.mBearingAccuracy);
        dest.writeLong(this.mElapsedRealtimeNanos);
        dest.writeInt(this.mSatellites);
        dest.writeString(this.mProvider);
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public double getAltitude() {
        return mAltitude;
    }

    public float getSpeed() {
        return mSpeed;
    }

    public float getBearing() {
        return mBearing;
    }

    public float getAccuracy() {
        return mAccuracy;
    }

    public float getVerticalAccuracy() {
        return mVerticalAccuracy;
    }

    public float getSpeedAccuracy() {
        return mSpeedAccuracy;
    }

    public float getBearingAccuracy() {
        return mBearingAccuracy;
    }

    public long getElapsedRealtimeNanos() {
        return mElapsedRealtimeNanos;
    }

    public int getSatellites() {
        return mSatellites;
    }

    public String getProvider() {
        return mProvider;
    }

    public void setAltitude(double altitude) {
        this.mAltitude = altitude;
    }

    public void setSpeed(float speed) {
        this.mSpeed = speed;
    }

    public void setBearing(float bearing) {
        this.mBearing = bearing;
    }

    public void setAccuracy(float accuracy) {
        this.mAccuracy = accuracy;
    }

    public void setVerticalAccuracy(float verticalAccuracy) {
        this.mVerticalAccuracy = verticalAccuracy;
    }

    public void setSpeedAccuracy(float speedAccuracy) {
        this.mSpeedAccuracy = speedAccuracy;
    }

    public void setBearingAccuracy(float bearingAccuracy) {
        this.mBearingAccuracy = bearingAccuracy;
    }

    public void setElapsedRealtimeNanos(long elapsedRealtimeNanos) {
        this.mElapsedRealtimeNanos = elapsedRealtimeNanos;
    }

    public void setSatellites(int satellites) {
        this.mSatellites = satellites > 0 ? satellites : DEFAULT_SATELLITES;
    }

    public void setProvider(String provider) {
        this.mProvider = provider == null || provider.isEmpty() ? PROVIDER_GPS : provider;
    }

    public BLocation() {
    }

    public BLocation(double latitude, double mLongitude) {
        this.mLatitude = latitude;
        this.mLongitude = mLongitude;
    }

    /** 拷贝构造：切换提供者 / 覆盖精度时避免改动调用方持有的原始对象。 */
    public BLocation(BLocation source) {
        if (source == null) {
            return;
        }
        this.mLatitude = source.mLatitude;
        this.mLongitude = source.mLongitude;
        this.mAltitude = source.mAltitude;
        this.mSpeed = source.mSpeed;
        this.mBearing = source.mBearing;
        this.mAccuracy = source.mAccuracy;
        this.mVerticalAccuracy = source.mVerticalAccuracy;
        this.mSpeedAccuracy = source.mSpeedAccuracy;
        this.mBearingAccuracy = source.mBearingAccuracy;
        this.mElapsedRealtimeNanos = source.mElapsedRealtimeNanos;
        this.mSatellites = source.mSatellites;
        this.mProvider = source.mProvider;
    }

    public BLocation(Parcel in) {
        this.mLatitude = in.readDouble();
        this.mLongitude = in.readDouble();
        this.mAltitude = in.readDouble();
        this.mSpeed = in.readFloat();
        this.mBearing = in.readFloat();
        this.mAccuracy = in.readFloat();
        // 旧配置文件没有这些字段，读到末尾返回 0 / null，做一次兜底即可
        this.mVerticalAccuracy = in.readFloat();
        this.mSpeedAccuracy = in.readFloat();
        this.mBearingAccuracy = in.readFloat();
        this.mElapsedRealtimeNanos = in.readLong();
        int satellites = in.readInt();
        this.mSatellites = satellites > 0 ? satellites : DEFAULT_SATELLITES;
        String provider = in.readString();
        this.mProvider = provider == null || provider.isEmpty() ? PROVIDER_GPS : provider;
    }

    public boolean isEmpty() {
        return mLatitude == 0 && mLongitude == 0;
    }

    /** 返回一个切换到指定提供者的副本，用于同一坐标分发给 gps / network 两个监听者。 */
    public BLocation withProvider(String provider) {
        BLocation copy = new BLocation(this);
        copy.setProvider(provider);
        return copy;
    }

    public static final Parcelable.Creator<BLocation> CREATOR = new Parcelable.Creator<BLocation>() {
        @Override
        public BLocation createFromParcel(Parcel source) {
            return new BLocation(source);
        }

        @Override
        public BLocation[] newArray(int size) {
            return new BLocation[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BLocation)) return false;
        BLocation other = (BLocation) o;
        return Double.compare(other.mLatitude, mLatitude) == 0
                && Double.compare(other.mLongitude, mLongitude) == 0
                && Float.compare(other.mBearing, mBearing) == 0
                && Float.compare(other.mSpeed, mSpeed) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(mLatitude);
        result = 31 * result + Double.hashCode(mLongitude);
        result = 31 * result + Float.hashCode(mSpeed);
        result = 31 * result + Float.hashCode(mBearing);
        return result;
    }

    @Override
    public String toString() {
        return "BLocation{" +
                "latitude: " + mLatitude +
                ", longitude: " + mLongitude +
                ", altitude: " + mAltitude +
                ", speed: " + mSpeed +
                ", bearing: " + mBearing +
                ", accuracy: " + mAccuracy +
                ", provider: " + mProvider +
                ", satellites: " + mSatellites +
                '}';
    }

    public Location convert2SystemLocation() {
        return convert2SystemLocation(this.mProvider);
    }

    /**
     * 转换成容器进程可以直接回调给应用的 {@link Location}。
     *
     * @param provider 定位提供者，传 null 时使用对象自带的提供者；
     *                 gps / network 之外的取值回落到 GPS。
     */
    public Location convert2SystemLocation(String provider) {
        String target = provider == null || provider.isEmpty() ? mProvider : provider;
        if (target == null || target.isEmpty()) {
            target = PROVIDER_GPS;
        }
        Location location = new Location(target);
        location.setLatitude(mLatitude);
        location.setLongitude(mLongitude);
        location.setSpeed(mSpeed);
        location.setBearing(mBearing);
        location.setAltitude(mAltitude);
        location.setAccuracy(mAccuracy > 0 ? mAccuracy : DEFAULT_ACCURACY);
        location.setTime(System.currentTimeMillis());
        long elapsed = mElapsedRealtimeNanos > 0 ? mElapsedRealtimeNanos : SystemClock.elapsedRealtimeNanos();
        location.setElapsedRealtimeNanos(elapsed);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 精度细分字段缺失时用水平精度推导，避免出现 0 精度导致 SDK 丢弃结果
            float vertical = mVerticalAccuracy > 0 ? mVerticalAccuracy : location.getAccuracy() * 2f;
            float speedAccuracy = mSpeedAccuracy > 0 ? mSpeedAccuracy : 1.0f;
            float bearingAccuracy = mBearingAccuracy > 0 ? mBearingAccuracy : 10.0f;
            location.setVerticalAccuracyMeters(vertical);
            location.setSpeedAccuracyMetersPerSecond(speedAccuracy);
            location.setBearingAccuracyDegrees(bearingAccuracy);
        }
        Bundle extraBundle = new Bundle();
        int satelliteCount = mSatellites > 0 ? mSatellites : DEFAULT_SATELLITES;
        extraBundle.putInt("satellites", satelliteCount);
        extraBundle.putInt("satellitesvalue", satelliteCount);
        location.setExtras(extraBundle);
        // 显式清掉模拟标记：本通道不经过系统测试提供者，正常情况下 isMock 本来就是 false，
        // 但个别 ROM 会在 Binder 传递时保留 mock 位，导致应用读到 isMock=true 而拒绝坐标。
        clearMockFlag(location);
        return location;
    }

    /**
     * 清除 {@link Location} 上的模拟位置标记。
     *
     * <p>{@code setIsFromMockProvider} 与 {@code setMock} 都是隐藏 API，
     * 且不同版本只保留其中之一，因此依次尝试，全部失败也不影响坐标本身。</p>
     */
    private static void clearMockFlag(Location location) {
        try {
            java.lang.reflect.Method setMock = Location.class.getDeclaredMethod("setMock", boolean.class);
            setMock.setAccessible(true);
            setMock.invoke(location, false);
            return;
        } catch (Throwable ignored) {
            // 该版本没有 setMock，继续尝试下一个
        }
        try {
            java.lang.reflect.Method setFromMock = Location.class
                    .getDeclaredMethod("setIsFromMockProvider", boolean.class);
            setFromMock.setAccessible(true);
            setFromMock.invoke(location, false);
        } catch (Throwable ignored) {
            // 两者都不可用时放弃：新建的 Location 默认就是非模拟，不会影响结果
        }
    }
}
