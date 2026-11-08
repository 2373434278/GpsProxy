package com.gpsproxy.dev.domain.track.usecase

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.domain.track.GpxCodec
import com.gpsproxy.dev.domain.track.TrackFileGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackUseCasesTest {

    private class FakeFiles(
        var content: String? = null,
        var writeSucceeds: Boolean = true,
    ) : TrackFileGateway {
        var written: String? = null
        var writes = 0

        override suspend fun readText(uri: String): String? = content

        override suspend fun writeText(uri: String, content: String): Boolean {
            writes++
            if (!writeSucceeds) return false
            written = content
            return true
        }
    }

    private val gpx = """
        <gpx><trk><trkseg>
          <trkpt lat="30.25" lon="120.15"/>
          <trkpt lat="30.26" lon="120.16"/>
        </trkseg></trk></gpx>
    """.trimIndent()

    @Test
    fun `import reads and parses a track`() = runBlocking {
        val files = FakeFiles(content = gpx)
        val result = ImportTrackUseCase(files, GpxCodec).import("content://track")
        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.points.size)
    }

    @Test
    fun `import maps read failure to file error`() = runBlocking {
        val result = ImportTrackUseCase(FakeFiles(content = null), GpxCodec)
            .import("content://missing")
        assertEquals(ErrorCode.FILE_0101, (result as Outcome.Failure).error.code)
    }

    @Test
    fun `import maps parse failure to route error`() = runBlocking {
        val result = ImportTrackUseCase(FakeFiles(content = "<gpx/>"), GpxCodec)
            .import("content://bad")
        assertEquals(ErrorCode.ROUTE_0401, (result as Outcome.Failure).error.code)
    }

    @Test
    fun `export writes gpx and reports success`() = runBlocking {
        val files = FakeFiles()
        val points = listOf(GeoPoint(30.25, 120.15), GeoPoint(30.26, 120.16))
        val result = ExportTrackUseCase(files, GpxCodec).export("content://out", "track", points)
        assertTrue(result is Outcome.Success)
        assertEquals(1, files.writes)
        assertTrue(requireNotNull(files.written).contains("trkpt"))
    }

    @Test
    fun `export rejects short tracks and write failures`() = runBlocking {
        val useCase = ExportTrackUseCase(FakeFiles(writeSucceeds = false), GpxCodec)
        val short = useCase.export("content://out", "track", listOf(GeoPoint(30.25, 120.15)))
        assertEquals(ErrorCode.ROUTE_0401, (short as Outcome.Failure).error.code)

        val points = listOf(GeoPoint(30.25, 120.15), GeoPoint(30.26, 120.16))
        val failed = useCase.export("content://out", "track", points)
        assertEquals(ErrorCode.FILE_0101, (failed as Outcome.Failure).error.code)
    }
}
