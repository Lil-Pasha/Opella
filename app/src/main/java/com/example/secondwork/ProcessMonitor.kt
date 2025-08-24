package com.example.secondwork

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.*
import java.io.RandomAccessFile

object ProcessMonitor {

    // читаем общее время CPU
    private fun readTotalCpuTime(): Long {
        val reader = RandomAccessFile("/proc/stat", "r")
        val load = reader.readLine()
        reader.close()
        val toks = load.split("\\s+".toRegex()).drop(1)
        return toks.take(7).sumOf { it.toLong() } // user+nice+system+idle+iowait+irq+softirq
    }

    // читаем CPU-время процесса
    private fun readProcCpuTime(pid: Int): Long {
        val reader = RandomAccessFile("/proc/$pid/stat", "r")
        val toks = reader.readLine().split(" ")
        reader.close()
        val utime = toks[13].toLong()
        val stime = toks[14].toLong()
        return utime + stime
    }

    // получить % CPU для процесса (с задержкой intervalMs)
    suspend fun getCpuUsageForProcess(pid: Int, intervalMs: Long = 1000): Float {
        val total1 = readTotalCpuTime()
        val proc1 = readProcCpuTime(pid)
        delay(intervalMs)
        val total2 = readTotalCpuTime()
        val proc2 = readProcCpuTime(pid)

        val totalDiff = total2 - total1
        val procDiff = proc2 - proc1

        return if (totalDiff > 0) (procDiff * 100f / totalDiff) else 0f
    }

    // получить RAM процесса
    fun getMemUsageForProcess(context: Context, pid: Int): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = am.getProcessMemoryInfo(intArrayOf(pid))[0]
        return memInfo.totalPss / 1024 // MB
    }
}
