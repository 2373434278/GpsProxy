package top.niunaijun.blackbox.entity.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;


/**
 * 单个「用户 + 包名」维度的定位配置。
 *
 * <p>{@code BLocationManagerService} 为每个容器应用维护一份本配置，
 * 并且额外维护一份全局配置（作用于所有处于 {@code GLOBAL_MODE} 的应用）。</p>
 *
 * <p>字段顺序是落盘兼容性约定：历史字段必须在前，新增字段只能追加到末尾，
 * 否则已安装用户升级后读取旧配置文件会出现坐标错位。</p>
 */
public class BLocationConfig implements Parcelable {

    /** 生效模式，取值见 {@code BLocationManager.CLOSE_MODE / GLOBAL_MODE / OWN_MODE}。 */
    public int pattern;
    /** 单基站（主小区）。 */
    public BCell cell;
    /** 主小区+邻区的完整列表，供 {@code getAllCellInfo} 使用。 */
    public List<BCell> allCell;
    /** 邻区列表，供 {@code getNeighboringCellInfo} 使用。 */
    public List<BCell> neighboringCellInfo;
    /** 模拟坐标本体。 */
    public BLocation location;

    @Override
    public int describeContents() {
        return 0;
    }

    public BLocationConfig() {
    }

    public BLocationConfig(Parcel in) {
        refresh(in);
    }

    public void refresh(Parcel in) {
        this.pattern = in.readInt();
        this.cell = in.readParcelable(BCell.class.getClassLoader());
        this.allCell = in.createTypedArrayList(BCell.CREATOR);
        this.neighboringCellInfo = in.createTypedArrayList(BCell.CREATOR);
        this.location = in.readParcelable(BLocation.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pattern);
        dest.writeParcelable(this.cell, flags);
        dest.writeTypedList(this.allCell);
        dest.writeTypedList(this.neighboringCellInfo);
        dest.writeParcelable(this.location, flags);
    }

    /** 该配置当前是否处于可用状态：既开启了模式，也写入了坐标。 */
    public boolean isEffective() {
        return pattern != 0 && location != null && !location.isEmpty();
    }

    /**
     * 用主小区重新推导 {@link #allCell} 与 {@link #neighboringCellInfo}。
     *
     * <p>调用方只设置一个主基站时，邻区信息应当由主基站推导出来，
     * 而不是留空，否则容器内应用读取到的邻区列表为空，与真实环境不符。</p>
     */
    public void rebuildCellsFromPrimary() {
        if (cell == null || !cell.isValid()) {
            return;
        }
        List<BCell> neighbors = new ArrayList<>();
        neighbors.add(cell.neighbor(2));
        neighbors.add(cell.neighbor(9));
        neighbors.add(cell.neighbor(17));
        List<BCell> all = new ArrayList<>(neighbors.size() + 1);
        all.add(cell);
        all.addAll(neighbors);
        this.allCell = all;
        this.neighboringCellInfo = neighbors;
    }

    public static final Creator<BLocationConfig> CREATOR = new Creator<BLocationConfig>() {
        @Override
        public BLocationConfig createFromParcel(Parcel source) {
            return new BLocationConfig(source);
        }

        @Override
        public BLocationConfig[] newArray(int size) {
            return new BLocationConfig[size];
        }
    };
}
