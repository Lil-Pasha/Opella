
package com.example.secondwork

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class TaskManagerActivity : AppCompatActivity() {

    private lateinit var tvCpuUsage: TextView
    private var lastCpu = 0f
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ProcessAdapter
    private lateinit var lineChart: LineChart

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val cpuEntries = ArrayList<Entry>()
    private var xValue = 0f
    private val iconCache = mutableMapOf<String, Drawable?>()

    private val updateRunnable = object : Runnable {
        override fun run() {
            refreshProcessData()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_manager)

        tvCpuUsage = findViewById(R.id.tvCpuUsage)
        recycler = findViewById(R.id.recyclerProcesses)

        // Создаём адаптер с лямбдой клика по элементу
        adapter = ProcessAdapter { process ->
            showKillDialog(process)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lineChart = findViewById(R.id.cpuChart)
        setupChart()

        handler.post(updateRunnable)
    }

    private fun setupChart() {
        val dataSet = LineDataSet(cpuEntries, "CPU Usage")
        dataSet.color = Color.GREEN
        dataSet.valueTextColor = Color.WHITE
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 3f
        dataSet.setDrawCircles(true)
        dataSet.setDrawValues(false)

        lineChart.data = LineData(dataSet)
        lineChart.description.isEnabled = false
        lineChart.axisRight.isEnabled = false
        lineChart.axisLeft.isEnabled = false
        lineChart.xAxis.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.setTouchEnabled(false)
        lineChart.setScaleEnabled(false)
        lineChart.invalidate()
    }

    private fun addCpuEntry(totalCpu: Double) {
        val safeCpu = totalCpu.coerceAtLeast(0.0).toFloat()
        cpuEntries.add(Entry(xValue, safeCpu))

        val diff = safeCpu - lastCpu
        val diffText = when {
            diff > 0 -> "↑ +${"%.1f".format(diff)}%"
            diff < 0 -> "↓ ${"%.1f".format(diff)}%"
            else -> "≈ 0.0%"
        }
        lastCpu = safeCpu

        tvCpuUsage.text = "CPU: ${"%.1f".format(safeCpu)}% ($diffText)"
        tvCpuUsage.setTextColor(
            when {
                diff > 0 -> Color.RED
                diff < 0 -> Color.GREEN
                else -> Color.YELLOW
            }
        )

        xValue += 1f
        if (cpuEntries.size > 50) {
            cpuEntries.removeAt(0)
            for (i in cpuEntries.indices) {
                cpuEntries[i] = Entry(i.toFloat(), cpuEntries[i].y)
            }
            xValue = cpuEntries.size.toFloat()
        }

        lineChart.data?.let { lineData ->
            val dataSet = lineData.getDataSetByIndex(0) as? LineDataSet
            dataSet?.values = cpuEntries
            lineData.notifyDataChanged()
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
        }
    }

    private fun refreshProcessData() {
        executor.execute {
            try {
                val allProcesses = parseProcessList()
                val userApps = allProcesses.filter { process ->
                    !process.packageName.isNullOrEmpty() &&
                            !process.packageName.startsWith("com.android") &&
                            !process.packageName.startsWith("com.google") &&
                            !process.packageName.startsWith("android") &&
                            process.name.isNotBlank()
                }.map { process ->
                    val icon = iconCache.getOrPut(process.packageName ?: "") {
                        try {
                            packageManager.getApplicationIcon(process.packageName!!)
                        } catch (e: Exception) {
                            getDrawable(R.drawable.default_icon)
                        }
                    }
                    process.copy(icon = icon)
                }

                val totalCpu = userApps.sumOf { it.cpu }

                runOnUiThread {
                    adapter.submitList(userApps)
                    addCpuEntry(totalCpu)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseProcessList(): List<ProcessInfoExtended> {
        val list = mutableListOf<ProcessInfoExtended>()
        val command = arrayOf("su", "-c", "ps -A -o pid,user,pcpu,rss,args --sort -rss")

        Runtime.getRuntime().exec(command).inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readLine() // заголовок
                reader.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEachIndexed { index, line ->
                        if (index < 50) parsePsLine(line)?.let { list.add(it) }
                    }
            }
        }
        return list
    }

    private fun parsePsLine(line: String): ProcessInfoExtended? {
        val parts = line.trim().split("\\s+".toRegex(), limit = 5)
        if (parts.size < 5) return null

        val pid = parts[0]
        val user = parts[1]
        val cpu = parts[2].toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val rssKB = parts[3].toDoubleOrNull() ?: 0.0
        val memMB = rssKB / 1024.0
        val args = parts[4]

        val packageName = extractPackageName(args)
        val processName = getAppNameFromPackage(packageName)

        return ProcessInfoExtended(
            pid = pid,
            name = processName,
            cpu = cpu,
            mem = memMB,
            storage = 0.0,
            user = user,
            rom = "N/A",
            packageName = packageName,
            icon = null
        )
    }

    private fun extractPackageName(args: String): String {
        val regex = "([a-z][a-z0-9_]+\\.)+[a-z][a-z0-9_]+".toRegex()
        return regex.find(args)?.value ?: ""
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun showKillDialog(process: ProcessInfoExtended) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_kill_app, null)

        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.dialogMessage)

        title.text = "> TERMINATE PROCESS"
        message.text = "Хотите завершить ${process.name} (PID: ${process.pid})?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Завершить") { _, _ ->
                killProcess(process.pid, process.packageName)
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
    }

    private fun killProcess(pid: String, packageName: String?) {
        try {
            packageName?.let {
                val forceStopCmd = "am force-stop $it"
                Runtime.getRuntime().exec(arrayOf("su", "-c", forceStopCmd))
            }
            val killCmd = "kill -9 $pid"
            Runtime.getRuntime().exec(arrayOf("su", "-c", killCmd))

            Toast.makeText(this, "Процесс $packageName завершен", Toast.LENGTH_SHORT).show()
            refreshProcessData()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка завершения процесса", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        executor.shutdownNow()
    }
}
