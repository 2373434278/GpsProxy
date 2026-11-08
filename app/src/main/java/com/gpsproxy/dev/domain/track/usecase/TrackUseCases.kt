package com.gpsproxy.dev.domain.track.usecase

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.core.result.failure
import com.gpsproxy.dev.domain.track.GpxCodec
import com.gpsproxy.dev.domain.track.GpxTrack
import com.gpsproxy.dev.domain.track.TrackFileGateway

/**
 * 从用户选择的文件导入 GPX 轨迹。
 *
 * 读文件与解析分开映射错误码：读不到是 [ErrorCode.FILE_0101]，
 * 读到了但格式不合法是 [ErrorCode.ROUTE_0401]，用户能据此决定是重选文件还是换文件。
 */
class ImportTrackUseCase(
    private val files: TrackFileGateway,
    private val codec: GpxCodec = GpxCodec,
) {

    suspend fun import(uri: String): Outcome<GpxTrack> {
        val raw = files.readText(uri)
            ?: return failure(ErrorCode.FILE_0101, "无法读取轨迹文件")
        val track = codec.parse(raw)
            ?: return failure(ErrorCode.ROUTE_0401, "GPX 解析失败或有效轨迹点不足")
        return Outcome.Success(track)
    }
}

/** 把一段轨迹写成 GPX 并保存到用户选择的位置。 */
class ExportTrackUseCase(
    private val files: TrackFileGateway,
    private val codec: GpxCodec = GpxCodec,
) {

    suspend fun export(
        uri: String,
        name: String,
        points: List<GeoPoint>,
        elevations: List<Double?>? = null,
    ): Outcome<Unit> {
        if (points.size < 2) {
            return failure(ErrorCode.ROUTE_0401, "没有可导出的轨迹点")
        }
        val content = codec.write(name, points, elevations)
        return if (files.writeText(uri, content)) {
            Outcome.Success(Unit)
        } else {
            failure(ErrorCode.FILE_0101, "写入轨迹文件失败")
        }
    }
}
