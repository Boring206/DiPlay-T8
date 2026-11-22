package com.shilapi.xcertplay.hud

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Ordinary readouts only. Never substitutes for the cluster's native warning states. */
data class ClusterVehicleReading(
    val gear: String? = null,
    val batteryPercent: Double? = null,
    val rangeKm: Int? = null,
    val gearAt: Long = 0,
    val batteryAt: Long = 0,
) {
    fun fresh(now: Long): ClusterVehicleReading = copy(
        gear = gear.takeIf { now - gearAt in 0..3_000 },
        batteryPercent = batteryPercent.takeIf { now - batteryAt in 0..15_000 },
        rangeKm = rangeKm.takeIf { now - batteryAt in 0..15_000 },
    )
}

object BydClusterVehicleReader {
    fun observe(context: Context, changed: (ClusterVehicleReading) -> Unit): () -> Unit {
        val app = context.applicationContext
        val shell = BydAdbShell("DiPlay-Cluster-Vehicle")
        val worker = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "diplay-cluster-vehicle").apply { isDaemon = true }
        }
        var reading = ClusterVehicleReading()
        var nextBatteryAt = 0L
        val poll = worker.scheduleWithFixedDelay({
            // Timestamp the beginning of each read, so a slow response cannot revive old data.
            val gearAt = SystemClock.elapsedRealtime()
            val gear = runCatching {
                if (BydOutputSettings.legacyVehicleProbe(app) &&
                    BydVehicleFieldStore.load(app)?.gearSupported != true) null
                else BydWheelSpeed.gear(shell.run(app, BydWheelSpeed.gearCommand(app)))?.letter?.toString()
            }.getOrNull()
            reading = reading.copy(gear = gear, gearAt = gearAt)
            if (gearAt >= nextBatteryAt) {
                val batteryAt = SystemClock.elapsedRealtime()
                val battery = runCatching { readBattery(app) { shell.run(app, it) } }
                    .getOrDefault(null to null)
                reading = reading.copy(batteryPercent = battery.first, rangeKm = battery.second, batteryAt = batteryAt)
                nextBatteryAt = batteryAt + 10_000
            }
            changed(reading.fresh(SystemClock.elapsedRealtime()))
        }, 0, 1, TimeUnit.SECONDS)
        return {
            poll.cancel(false)
            worker.execute { shell.close() }
            worker.shutdown()
        }
    }

    private fun readBattery(context: Context, shell: (String) -> String?): Pair<Double?, Int?> {
        if (BydOutputSettings.legacyVehicleProbe(context)) {
            val fields = BydVehicleFieldStore.load(context)
            return decodeBattery(
                fields?.result(BydVehicleField.SOC)?.takeIf { it.supported }?.address?.command()?.let(shell),
                fields?.result(BydVehicleField.RANGE)?.takeIf { it.supported }?.address?.command()?.let(shell),
            )
        }
        return readBattery(shell)
    }

    internal fun readBattery(shell: (String) -> String?): Pair<Double?, Int?> {
        val protocol = BydBattery.protocol(shell) ?: return null to null
        return decodeBattery(BydBattery.percentReply(shell, protocol), BydBattery.rangeReply(shell, protocol))
    }

    private fun decodeBattery(percentReply: String?, rangeReply: String?): Pair<Double?, Int?> =
        BydParcel.value(percentReply)?.let { java.lang.Float.intBitsToFloat(it).toDouble() }
            ?.takeIf { it.isFinite() && it in 0.0..100.0 } to
            BydParcel.value(rangeReply)?.takeIf { it in 0..3000 }
}
