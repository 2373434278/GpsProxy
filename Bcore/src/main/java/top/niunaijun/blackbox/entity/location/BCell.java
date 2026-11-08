package top.niunaijun.blackbox.entity.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 容器内基站信息载体。
 *
 * <p>容器应用通过 {@code TelephonyManager#getCellLocation} /
 * {@code getAllCellInfo} / {@code getNeighboringCellInfo} 读取基站时会命中
 * {@code ITelephonyManagerProxy}，代理层再从 {@code BLocationManager} 取出本类实例。</p>
 *
 * <p>早期版本只保存 MCC/MNC/LAC/CID 四个字段，很多地图与风控 SDK 会读取
 * 信号强度、注册状态与网络制式，字段缺失会被判定为「异常基站」，
 * 因此这里补齐了这些常用字段。字段顺序是落盘兼容性约定：历史字段必须在前，
 * 新增字段只能追加到末尾。</p>
 */
public class BCell implements Parcelable {

    /** 移动国家码，例如中国 460。 */
    public int MCC;
    /** 移动网络码，中国移动 0/2/7，中国联通 1/6，中国电信 3/11。 */
    public int MNC;
    /** 位置区码。 */
    public int LAC;
    /** 小区标识。 */
    public int CID;
    /**
     * 基站类型，取值见 {@link #PHONE_TYPE_NONE} / {@link #PHONE_TYPE_GSM} /
     * {@link #PHONE_TYPE_CDMA}。
     */
    public int TYPE;

    // ---- 以下为扩展字段，必须保持追加顺序 ----

    /** 主扰码，WCDMA/TD-SCDMA 使用，LTE 场景填 -1 表示无效。 */
    public int PSC = -1;
    /** 无线网络控制器标识，CDMA/WCDMA 使用。 */
    public int RNC = -1;
    /** 信号强度，单位 dBm，真实值通常在 -50（强）到 -113（弱）之间。 */
    public int signalStrengthDbm = -85;
    /** 信号等级 0-4，与 {@code CellSignalStrength#getLevel()} 对齐。 */
    public int signalLevel = 3;
    /** 是否已注册到该基站，未注册的小区一般出现在邻区列表里。 */
    public boolean registered = true;
    /** 移动网络制式，取值见 {@link #NETWORK_TYPE_*}。 */
    public int networkType = NETWORK_TYPE_UNKNOWN;

    public static final int NETWORK_TYPE_UNKNOWN = 0;

    public static final int NETWORK_TYPE_GPRS = 1;

    public static final int NETWORK_TYPE_EDGE = 2;

    public static final int NETWORK_TYPE_UMTS = 3;

    public static final int NETWORK_TYPE_CDMA = 4;

    public static final int NETWORK_TYPE_EVDO_0 = 5;

    public static final int NETWORK_TYPE_EVDO_A = 6;

    public static final int NETWORK_TYPE_1xRTT = 7;

    public static final int NETWORK_TYPE_LTE = 13;

    public static final int NETWORK_TYPE_NR = 20;

    public static final int PHONE_TYPE_NONE = 0;

    public static final int PHONE_TYPE_GSM = 1;

    public static final int PHONE_TYPE_CDMA = 2;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // 前 5 项为历史顺序，禁止调整
        dest.writeInt(this.MCC);
        dest.writeInt(this.MNC);
        dest.writeInt(this.LAC);
        dest.writeInt(this.CID);
        dest.writeInt(this.TYPE);
        // 扩展字段追加在后
        dest.writeInt(this.PSC);
        dest.writeInt(this.RNC);
        dest.writeInt(this.signalStrengthDbm);
        dest.writeInt(this.signalLevel);
        dest.writeInt(this.registered ? 1 : 0);
        dest.writeInt(this.networkType);
    }

    public BCell() {
    }

    public BCell(int MCC, int MNC, int LAC, int CID) {
        this.TYPE = PHONE_TYPE_GSM;
        this.MCC = MCC;
        this.CID = CID;
        this.MNC = MNC;
        this.LAC = LAC;
    }

    /** 拷贝构造，用于在邻区列表里复用主基站的运营商信息时避免共享引用。 */
    public BCell(BCell source) {
        if (source == null) {
            return;
        }
        this.MCC = source.MCC;
        this.MNC = source.MNC;
        this.LAC = source.LAC;
        this.CID = source.CID;
        this.TYPE = source.TYPE;
        this.PSC = source.PSC;
        this.RNC = source.RNC;
        this.signalStrengthDbm = source.signalStrengthDbm;
        this.signalLevel = source.signalLevel;
        this.registered = source.registered;
        this.networkType = source.networkType;
    }

    public BCell(Parcel in) {
        this.MCC = in.readInt();
        this.MNC = in.readInt();
        this.LAC = in.readInt();
        this.CID = in.readInt();
        this.TYPE = in.readInt();
        this.PSC = in.readInt();
        this.RNC = in.readInt();
        this.signalStrengthDbm = in.readInt();
        this.signalLevel = in.readInt();
        this.registered = in.readInt() != 0;
        this.networkType = in.readInt();
    }

    /** 判断基站参数是否足够下发；全 0 会被系统判为无效基站。 */
    public boolean isValid() {
        return MCC > 0 && MNC >= 0 && LAC > 0 && CID > 0;
    }

    /** 返回当前基站的邻区版本：同运营商、相邻 CID、信号更弱。 */
    public BCell neighbor(int cidOffset) {
        BCell neighbor = new BCell(this);
        neighbor.CID = this.CID + cidOffset;
        neighbor.registered = false;
        // dBm 越负信号越弱，因此邻区在主小区基础上继续下调 8~16 dBm
        neighbor.signalStrengthDbm = Math.max(-113, this.signalStrengthDbm - (8 + Math.abs(cidOffset) % 9));
        neighbor.signalLevel = Math.max(1, this.signalLevel - 1);
        return neighbor;
    }

    @Override
    public String toString() {
        return "BCell{" +
                "mcc=" + MCC +
                ", mnc=" + MNC +
                ", lac=" + LAC +
                ", cid=" + CID +
                ", dbm=" + signalStrengthDbm +
                ", registered=" + registered +
                ", networkType=" + networkType +
                '}';
    }

    public static final Parcelable.Creator<BCell> CREATOR = new Parcelable.Creator<BCell>() {
        @Override
        public BCell createFromParcel(Parcel source) {
            return new BCell(source);
        }

        @Override
        public BCell[] newArray(int size) {
            return new BCell[size];
        }
    };
}
