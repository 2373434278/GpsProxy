package com.gpsproxy.dev.data

import com.gpsproxy.dev.domain.place.Poi
import com.gpsproxy.dev.domain.place.PoiCategory

/**
 * 内置演示 POI 数据集（GCJ-02）。
 *
 * 保留在数据层：它是"没有配置地图 Key 时也能用"的兜底数据，
 * 不属于领域规则。UI 会如实标注数据来源为「内置示例数据」。
 *
 * 配置 `amap.web.key` 后，真实搜索优先，本数据仅在失败时兜底。
 */
object LocalPoiData {

    val all: List<Poi> = listOf(
        Poi("西湖风景名胜区", "西湖区龙井路1号", 30.250213, 120.148832, PoiCategory.SCENIC, "杭州"),
        Poi("断桥残雪", "西湖区北山街与白堤交叉口", 30.262410, 120.148720, PoiCategory.SCENIC, "杭州"),
        Poi("雷峰塔", "西湖区南山路15号", 30.231150, 120.148870, PoiCategory.SCENIC, "杭州"),
        Poi("灵隐寺", "西湖区法云弄1号", 30.240890, 120.099540, PoiCategory.SCENIC, "杭州"),
        Poi("西溪国家湿地公园", "西湖区天目山路518号", 30.269230, 120.062360, PoiCategory.SCENIC, "杭州"),
        Poi("河坊街", "上城区河坊街", 30.241530, 120.168920, PoiCategory.SCENIC, "杭州"),
        Poi("九溪烟树", "西湖区九溪路", 30.202010, 120.118030, PoiCategory.SCENIC, "杭州"),
        Poi("杭州植物园", "西湖区桃源岭1号", 30.256170, 120.110640, PoiCategory.SCENIC, "杭州"),
        Poi("湖滨银泰 in77", "上城区延安路258号", 30.256870, 120.166530, PoiCategory.MALL, "杭州"),
        Poi("杭州大厦", "拱墅区武林广场21号", 30.273930, 120.162110, PoiCategory.MALL, "杭州"),
        Poi("武林广场", "拱墅区武林广场", 30.274130, 120.164520, PoiCategory.MALL, "杭州"),
        Poi("杭州东站", "上城区全福桥路2号", 30.291430, 120.212640, PoiCategory.TRANSIT, "杭州"),
        Poi("杭州站（城站）", "上城区环城东路1号", 30.243790, 120.182290, PoiCategory.TRANSIT, "杭州"),
        Poi("杭州萧山国际机场", "萧山区机场路", 30.236140, 120.434820, PoiCategory.TRANSIT, "杭州"),
        Poi("黄龙体育中心", "西湖区黄龙路1号", 30.270120, 120.135550, PoiCategory.TRANSIT, "杭州"),
        Poi("浙江大学玉泉校区", "西湖区浙大路38号", 30.263090, 120.123310, PoiCategory.SCENIC, "杭州"),
        Poi("钱江新城", "上城区钱江路", 30.246080, 120.209920, PoiCategory.MALL, "杭州"),
        Poi("阿里巴巴西溪园区", "余杭区文一西路969号", 30.280170, 120.026080, PoiCategory.MALL, "杭州"),
        Poi("浙江省人民医院", "拱墅区上塘路158号", 30.290760, 120.164780, PoiCategory.HOSPITAL, "杭州"),
        Poi("浙江大学医学院附属第一医院", "上城区庆春路79号", 30.256940, 120.177680, PoiCategory.HOSPITAL, "杭州"),
        Poi("楼外楼（孤山路店）", "西湖区孤山路30号", 30.253400, 120.144600, PoiCategory.FOOD, "杭州"),
        Poi("新白鹿餐厅（湖滨店）", "上城区延安路258号", 30.256100, 120.165900, PoiCategory.FOOD, "杭州"),
        Poi("杭州武林万怡酒店", "拱墅区湖墅南路28号", 30.278300, 120.156400, PoiCategory.HOTEL, "杭州"),
        Poi("天安门广场", "东城区东长安街", 39.908722, 116.397499, PoiCategory.SCENIC, "北京"),
        Poi("故宫博物院", "东城区景山前街4号", 39.916345, 116.397155, PoiCategory.SCENIC, "北京"),
        Poi("北京南站", "丰台区永外大街车站路12号", 39.865240, 116.378610, PoiCategory.TRANSIT, "北京"),
        Poi("三里屯太古里", "朝阳区三里屯路19号", 39.933580, 116.455550, PoiCategory.MALL, "北京"),
        Poi("北京首都国际机场", "顺义区首都机场路", 40.079900, 116.603100, PoiCategory.TRANSIT, "北京"),
        Poi("外滩", "黄浦区中山东一路", 31.240000, 121.490000, PoiCategory.SCENIC, "上海"),
        Poi("东方明珠广播电视塔", "浦东新区世纪大道1号", 31.239680, 121.499760, PoiCategory.SCENIC, "上海"),
        Poi("上海虹桥站", "闵行区申贵路1500号", 31.194810, 121.320970, PoiCategory.TRANSIT, "上海"),
        Poi("上海中心大厦", "浦东新区银城中路501号", 31.233600, 121.505700, PoiCategory.MALL, "上海"),
        Poi("上海迪士尼乐园", "浦东新区川沙新镇黄赵路310号", 31.144000, 121.657000, PoiCategory.SCENIC, "上海"),
        Poi("深圳北站", "龙华区致远中路28号", 22.609610, 114.029570, PoiCategory.TRANSIT, "深圳"),
        Poi("世界之窗", "南山区深南大道9037号", 22.540340, 113.973720, PoiCategory.SCENIC, "深圳"),
        Poi("深圳湾公园", "南山区滨海大道", 22.490000, 113.950000, PoiCategory.SCENIC, "深圳"),
        Poi("华强北商业区", "福田区华强北路", 22.548000, 114.088000, PoiCategory.MALL, "深圳"),
        Poi("春熙路", "锦江区春熙路", 30.658000, 104.080000, PoiCategory.MALL, "成都"),
        Poi("宽窄巷子", "青羊区长顺上街", 30.669000, 104.055000, PoiCategory.SCENIC, "成都"),
        Poi("成都东站", "成华区邛崃山路333号", 30.630000, 104.141000, PoiCategory.TRANSIT, "成都"),
        Poi("广州塔", "海珠区阅江西路222号", 23.106600, 113.324500, PoiCategory.SCENIC, "广州"),
        Poi("珠江新城", "天河区珠江新城", 23.120000, 113.324000, PoiCategory.MALL, "广州"),
        Poi("广州南站", "番禺区石壁街道南站北路", 22.989000, 113.269000, PoiCategory.TRANSIT, "广州"),
        Poi("黄鹤楼", "武昌区蛇山西山坡特1号", 30.544900, 114.302500, PoiCategory.SCENIC, "武汉"),
        Poi("大雁塔", "雁塔区雁塔路", 34.218600, 108.964000, PoiCategory.SCENIC, "西安"),
        Poi("南京新街口", "秦淮区中山南路", 32.041700, 118.784000, PoiCategory.MALL, "南京"),
        Poi("拙政园", "姑苏区东北街178号", 31.324000, 120.629000, PoiCategory.SCENIC, "苏州"),
        Poi("重庆解放碑", "渝中区民族路", 29.558600, 106.577100, PoiCategory.MALL, "重庆"),
    )
}
