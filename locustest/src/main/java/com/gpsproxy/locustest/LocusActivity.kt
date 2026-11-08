package com.gpsproxy.locustest

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * 定位注入的断言替身。
 *
 * 不是一个「看一眼日志」的探针：给定期望坐标后，它会自己判定
 * `getLastKnownLocation` / `getCurrentLocation` / `requestLocationUpdates`
 * 三条路径拿到的值是否可信，并在屏幕上给出 PASS/FAIL。
 *
 * 期望值通过 Intent 传入（都可省略，省略则跳过对应断言）：
 * ```
 * adb shell am start -n com.gpsproxy.locustest/.LocusActivity \
 *   --ed lat 30.250213 --ed lng 120.148832 --ed tol 15 --el interval 1000
 * ```
 *
 * 节奏断言用容器服务的真实钳制区间（200ms–5000ms），而不是理想间隔：
 * `BLocationManagerService` 对静止坐标还有 3s 去重，理想值会误报。
 */
class LocusActivity : Activity(), LocationListener {

    private lateinit var text: TextView
    private lateinit var lm: LocationManager
    private val main = Handler(Looper.getMainLooper())

    private val checks = JSONArray()
    private val gpsFixes = mutableListOf<Location>()
    private val networkFixes = mutableListOf<Location>()
    private val fixTimes = mutableListOf<Long>()

    private var expectedLat = Double.NaN
    private var expectedLng = Double.NaN
    private var toleranceMeters = DEFAULT_TOLERANCE_METERS
    private var requestedIntervalMs = DEFAULT_INTERVAL_MS
    private var evaluated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            textSize = 13f
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(text) })

        expectedLat = intent?.getDoubleExtra("lat", Double.NaN) ?: Double.NaN
        expectedLng = intent?.getDoubleExtra("lng", Double.NaN) ?: Double.NaN
        toleranceMeters = intent?.getDoubleExtra("tol", DEFAULT_TOLERANCE_METERS)
            ?: DEFAULT_TOLERANCE_METERS
        requestedIntervalMs = intent?.getLongExtra("interval", DEFAULT_INTERVAL_MS)
            ?: DEFAULT_INTERVAL_MS

        lm = getSystemService(LocationManager::class.java)
        if (!hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_PERMISSION)
        } else {
            start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) start()
        else show("权限被拒绝：无法验证定位链路")
    }

    private fun start() {
        show("期望 lat=${fmt(expectedLat)} lng=${fmt(expectedLng)} " +
            "tol=${toleranceMeters.toInt()}m interval=${requestedIntervalMs}ms\n采集中…")

        dumpListenerInterface()

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            checkLastKnown(provider)
            requestUpdates(provider)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            currentLocationOnce(LocationManager.GPS_PROVIDER)
        } else {
            record("currentLocation:gps", true, "API ${Build.VERSION.SDK_INT} < 30，跳过")
        }

        val window = (requestedIntervalMs * 6).coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)
        main.postDelayed({ evaluate() }, window)
    }

    // ---- 三条注入路径 ----

    private fun checkLastKnown(provider: String) {
        val label = "lastKnown:$provider"
        try {
            val location = lm.getLastKnownLocation(provider)
            if (location == null) {
                record(label, false, "返回 null")
            } else {
                val failures = locationFailures(location)
                record(label, failures.isEmpty(), failures.ifEmpty { listOf(summary(location)) }.joinToString("; "))
            }
        } catch (error: SecurityException) {
            record(label, false, "SecurityException")
        } catch (error: Throwable) {
            record(label, false, "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun requestUpdates(provider: String) {
        try {
            lm.requestLocationUpdates(provider, requestedIntervalMs, 0f, this)
        } catch (error: Throwable) {
            record("updates:$provider", false, "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun currentLocationOnce(provider: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            lm.getCurrentLocation(provider, null, mainExecutor) { location ->
                val label = "current:$provider"
                if (location == null) {
                    record(label, false, "返回 null")
                } else {
                    val failures = locationFailures(location)
                    record(label, failures.isEmpty(), failures.ifEmpty { listOf(summary(location)) }.joinToString("; "))
                }
                render()
            }
        } catch (error: Throwable) {
            record("current:$provider", false, "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        if (evaluated) return
        val bucket = if (location.provider == LocationManager.GPS_PROVIDER) gpsFixes else networkFixes
        bucket += location
        fixTimes += SystemClock.elapsedRealtime()
        render()
    }

    // ---- 断言 ----

    /** 返回该条定位违反的断言；空列表表示全部通过。只做单帧字段校验，无副作用。 */
    private fun locationFailures(location: Location): List<String> {
        val failures = mutableListOf<String>()

        if (!expectedLat.isNaN() && !expectedLng.isNaN()) {
            val distance = distanceMeters(location)
            if (distance > toleranceMeters) {
                failures += "偏离期望 %.1fm > %.1fm".format(distance, toleranceMeters)
            }
        }
        if (!location.hasAccuracy() || location.accuracy <= 0f) failures += "accuracy 缺失/为 0"
        if (location.altitude == 0.0) failures += "altitude 未写入"
        if (location.speed.isNaN() || location.speed.isInfinite()) failures += "speed 非法"
        if (location.bearing.isNaN() || location.bearing.isInfinite()) failures += "bearing 非法"
        if (location.provider !in KNOWN_PROVIDERS) failures += "provider=${location.provider} 不在预期集合"

        // 消费方普遍用 time 判重，因此要求新鲜。
        val age = System.currentTimeMillis() - location.time
        if (abs(age) > FRESHNESS_WINDOW_MS) failures += "time 不新鲜（${age}ms）"

        return failures
    }

    private fun distanceMeters(location: Location): Float {
        val results = FloatArray(1)
        Location.distanceBetween(expectedLat, expectedLng, location.latitude, location.longitude, results)
        return results[0]
    }

    private fun evaluate() {
        if (evaluated) return
        evaluated = true
        lm.removeUpdates(this)

        val allFixes = gpsFixes + networkFixes
        record("updates:received", allFixes.isNotEmpty(), "${allFixes.size} 次回调")

        if (allFixes.isNotEmpty()) {
            val allFailures = allFixes.map(::locationFailures).flatten().toMutableList()
            // time 单调：按到达顺序比较，回退会让消费方的判重逻辑丢弃后续帧。
            for (index in 1 until allFixes.size) {
                if (allFixes[index].time < allFixes[index - 1].time) {
                    allFailures += "time 回退（第 $index 帧）"
                }
            }
            record("updates:payload", allFailures.isEmpty(), allFailures.distinct().joinToString("; ").ifBlank { "全部字段可信" })
        }

        val observed = observedIntervalMs()
        if (observed == null) {
            record("updates:cadence", true, "样本不足，跳过节奏断言")
        } else {
            // 真实钳制：LocationRecord.effectiveIntervalMs() = 200ms..5000ms。
            val inClamp = observed in MIN_CLAMP_MS.toDouble()..MAX_CLAMP_MS.toDouble()
            record(
                "updates:cadence",
                inClamp,
                "实测 %.0fms（请求 %dms，钳制 %d–%dms）".format(observed, requestedIntervalMs, MIN_CLAMP_MS, MAX_CLAMP_MS),
            )
        }

        render()
        emitVerdict()
    }

    private fun observedIntervalMs(): Double? {
        if (fixTimes.size < 2) return null
        val first = fixTimes.first()
        val last = fixTimes.last()
        return (last - first).toDouble() / (fixTimes.size - 1)
    }

    // ---- 输出 ----

    private fun record(name: String, pass: Boolean, detail: String) {
        checks.put(
            JSONObject()
                .put("name", name)
                .put("pass", pass)
                .put("detail", detail),
        )
        render()
    }

    private fun allPass(): Boolean {
        for (index in 0 until checks.length()) {
            if (!checks.getJSONObject(index).getBoolean("pass")) return false
        }
        return checks.length() > 0
    }

    private fun render() {
        val builder = StringBuilder()
        builder.append("期望 lat=${fmt(expectedLat)} lng=${fmt(expectedLng)} tol=${toleranceMeters.toInt()}m\n")
        builder.append("请求间隔 ${requestedIntervalMs}ms\n\n")
        for (index in 0 until checks.length()) {
            val check = checks.getJSONObject(index)
            val mark = if (check.getBoolean("pass")) "[PASS]" else "[FAIL]"
            builder.append(mark).append(' ').append(check.getString("name"))
                .append(" — ").append(check.getString("detail")).append('\n')
        }
        val gps = gpsFixes.lastOrNull()
        if (gps != null) {
            builder.append("\n最近 GPS 回调：").append(summary(gps)).append('\n')
        }
        show(builder.toString())
    }

    /** 单行机器可读结论，供 `adb logcat -s LocusTest | grep LOCUS_VERDICT` 取用。 */
    private fun emitVerdict() {
        val verdict = JSONObject()
            .put("pass", allPass())
            .put("expected", JSONObject().put("lat", expectedLat).put("lng", expectedLng))
            .put("toleranceMeters", toleranceMeters)
            .put("requestedIntervalMs", requestedIntervalMs)
            .put("observedIntervalMs", observedIntervalMs() ?: JSONObject.NULL)
            .put("fixes", gpsFixes.size + networkFixes.size)
            .put("checks", checks)
        Log.i(TAG, "LOCUS_VERDICT $verdict")
    }

    private fun dumpListenerInterface() {
        try {
            val iface = Class.forName("android.location.ILocationListener")
            for (method in iface.declaredMethods) {
                Log.i(TAG, "ILocationListener.${method.name}(${method.parameterTypes.joinToString { it.name }})")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "iface dump failed: $error")
        }
    }

    private fun summary(location: Location): String =
        "%.6f,%.6f acc=%.1f alt=%.1f spd=%.2f brg=%.1f p=%s".format(
            location.latitude,
            location.longitude,
            location.accuracy,
            location.altitude,
            location.speed,
            location.bearing,
            location.provider,
        )

    private fun fmt(value: Double): String = if (value.isNaN()) "?" else "%.6f".format(value)

    private fun show(message: String) {
        text.text = message
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        lm.removeUpdates(this)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LocusTest"
        const val REQ_PERMISSION = 1
        const val DEFAULT_INTERVAL_MS = 1000L
        const val DEFAULT_TOLERANCE_METERS = 20.0
        const val MIN_WINDOW_MS = 6_000L
        const val MAX_WINDOW_MS = 20_000L
        const val FRESHNESS_WINDOW_MS = 60_000L

        /** 与 LocationRecord.effectiveIntervalMs() 的钳制区间一致。 */
        const val MIN_CLAMP_MS = 200L
        const val MAX_CLAMP_MS = 5_000L

        val KNOWN_PROVIDERS = setOf("gps", "network", "fused", "passive")
    }
}
