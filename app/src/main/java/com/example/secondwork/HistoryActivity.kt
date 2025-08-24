package com.example.secondwork

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class HistoryActivity : AppCompatActivity() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var messageContainer: LinearLayout
    private lateinit var editMessage: EditText
    private lateinit var btnSend: ImageButton

    private val fileName = "process_timeline.log"
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        scrollView = findViewById(R.id.scrollView)
        messageContainer = findViewById(R.id.messageContainer)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)

        // Clear terminal on each start
        messageContainer.removeAllViews()

        btnSend.setOnClickListener {
            val input = editMessage.text.toString().trim()
            if (input.isNotEmpty()) {
                addMessage("user:~$ $input")
                appendToLog("user:~$ $input")
                editMessage.text.clear()
                handleCommand(input)
            }
        }
    }

    /** === Command logic === */
    private fun handleCommand(input: String) {
        when {
            input.lowercase() == "/help" -> showHelp()
            input.lowercase() == "/battery" -> showBatteryInfo()
            input.lowercase() == "/cpuinfo" -> showCpuInfo()
            input.lowercase() == "/raminfo" -> showRamInfo()
            input.lowercase() == "/ps" -> showProcesses()
            input.lowercase().startsWith("/kill") -> {
                val pid = input.split(" ").getOrNull(1)
                if (!pid.isNullOrEmpty()) killProcessCommand(pid)
                else addMessage("system:~$ Error: PID not specified")
            }
            input.lowercase().startsWith("/top") -> showTop(input)
            input.lowercase().startsWith("/pid") -> {
                val packageName = input.split(" ").getOrNull(1)
                if (!packageName.isNullOrEmpty()) showPID(packageName)
                else addMessage("system:~$ Error: package name not specified")
            }
            input.lowercase() == "/clear" -> clearScreen()
            else -> addMessage("system:~$ Unknown command: $input")
        }
    }

    private fun showPID(packageName: String) {
        thread {
            val processes = parseProcessList()
            val matched = processes.filter { it.packageName.equals(packageName, ignoreCase = true) }
            runOnUiThread {
                if (matched.isNotEmpty()) {
                    addMessage("system:~$ PID for $packageName:")
                    appendToLog("system:~$ PID for $packageName:")
                    matched.forEach { p ->
                        val line = "${p.name} | PID: ${p.pid} | CPU: ${"%.1f".format(p.cpu)}% | RAM: ${"%.1f".format(p.mem)} MB"
                        addMessage(line)
                        appendToLog(line)
                    }
                } else {
                    addMessage("system:~$ Process with package $packageName not found")
                }
            }
        }
    }

    /** /help */
    private fun showHelp() {
        val commands = listOf(
            "/help         - Show command list",
            "/ps           - Show processes with CPU/RAM usage",
            "/kill PID     - Kill process by PID (example: /kill 1234)",
            "/top [N]      - Show top N apps by CPU usage (default 10)",
            "/pid <package> - Show PID of app by package name",
            "/clear        - Clear terminal screen",
            "/battery      - Show battery status (level, status, temperature)",
            "/cpuinfo      - Show CPU cores frequency",
            "/raminfo      - Show RAM usage (total, used, available)"
        )

        addMessage("system:~$ Available commands:")
        appendToLog("system:~$ Available commands:")
        commands.forEach { cmd ->
            addMessage(" > $cmd")
            appendToLog(" > $cmd")
        }
    }

    /** /ps — list of processes */
    private fun showProcesses() {
        addMessage("system:~$ PID     CPU%     RAM(MB)     NAME")
        appendToLog("system:~$ PID     CPU%     RAM(MB)     NAME")
        thread {
            val processes = parseProcessList().sortedByDescending { it.cpu }
            for (p in processes) {
                val line = "${p.pid.padEnd(7)} ${"%.1f".format(p.cpu).padEnd(8)} ${"%.1f".format(p.mem).padEnd(10)} ${p.name}"
                runOnUiThread {
                    addMessage(line)
                    appendToLog(line)
                }
            }
        }
    }

    /** /top [N] — top processes by CPU */
    private fun showTop(input: String) {
        val count = input.split(" ").getOrNull(1)?.toIntOrNull() ?: 10
        addMessage("system:~$ TOP $count apps by CPU usage:")
        appendToLog("system:~$ TOP $count apps by CPU usage:")
        thread {
            val processes = parseProcessList().sortedByDescending { it.cpu }.take(count)
            val header = String.format("%-7s %-10s %-6s %-8s %-s", "PID", "USER", "CPU%", "RAM(MB)", "NAME")
            runOnUiThread {
                addMessage(header)
                appendToLog(header)
            }
            for (p in processes) {
                val line = String.format(
                    "%-7s %-10s %-6.1f %-8.1f %-s",
                    p.pid, p.user, p.cpu, p.mem, p.name
                )
                runOnUiThread {
                    addMessage(line)
                    appendToLog(line)
                }
            }
        }
    }

    /** /kill PID — terminate process */
    private fun killProcessCommand(pid: String) {
        if (!pid.matches("\\d+".toRegex())) {
            addMessage("system:~$ Error: invalid PID")
            return
        }
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -9 $pid"))
            addMessage("system:~$ Process $pid terminated")
            appendToLog("system:~$ Process $pid terminated")
        } catch (e: Exception) {
            addMessage("system:~$ Error terminating process $pid: ${e.message}")
            appendToLog("system:~$ Error terminating process $pid: ${e.message}")
        }
    }

    /** /clear — clear terminal screen */
    private fun clearScreen() {
        messageContainer.removeAllViews()
        appendToLog("system:~$ Screen cleared")
    }

    /** === Terminal functions === */
    private fun addMessage(text: String) {
        val color = when {
            text.startsWith("system:~$") -> 0xFFFF5555.toInt() // red
            text.startsWith("user:~$") -> 0xFF00FF00.toInt()   // green
            else -> 0xFFFF5555.toInt()
        }

        val messageView = TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = 14f
            typeface = resources.getFont(R.font.american_typewriter_rus_by_me)
        }

        messageContainer.addView(messageView)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showBatteryInfo() {
        thread {
            try {
                val output = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys battery")).inputStream.bufferedReader().readText()
                val level = Regex("level: (\\d+)").find(output)?.groupValues?.get(1) ?: "N/A"
                val status = Regex("status: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()?.let {
                    when(it){
                        2 -> "Charging"
                        3 -> "Discharging"
                        else -> "Unknown"
                    }
                } ?: "Unknown"
                val temp = Regex("temperature: (\\d+)").find(output)?.groupValues?.get(1)?.toDoubleOrNull()?.div(10) ?: "N/A"

                runOnUiThread { addMessage("system:~$ Battery: $level% | $status | Temperature: $temp °C") }
            } catch (e: Exception) {
                runOnUiThread { addMessage("system:~$ Error getting battery info: ${e.message}") }
            }
        }
    }
    private fun showCpuInfo() {
        thread {
            try {
                val cores = Runtime.getRuntime().availableProcessors()
                val freqs = (0 until cores).map { i ->
                    val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                        .takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()?.div(1000) ?: -1
                    "CPU$i: ${if(f > 0) "$f MHz" else "N/A"}"
                }
                runOnUiThread { addMessage("system:~$ CPU Cores: $cores | ${freqs.joinToString(" | ")}") }
            } catch (e: Exception) {
                runOnUiThread { addMessage("system:~$ Error getting CPU info: ${e.message}") }
            }
        }
    }
    private fun showRamInfo() {
        thread {
            try {
                val output = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/meminfo")).inputStream.bufferedReader().readText()
                val total = Regex("MemTotal:\\s+(\\d+) kB").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val free = Regex("MemFree:\\s+(\\d+) kB").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val avail = Regex("MemAvailable:\\s+(\\d+) kB").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val used = total - avail

                runOnUiThread {
                    addMessage("system:~$ RAM: Total: ${total/1024}MB | Used: ${used/1024}MB | Available: ${avail/1024}MB")
                }
            } catch (e: Exception) {
                runOnUiThread { addMessage("system:~$ Error getting RAM info: ${e.message}") }
            }
        }
    }



    private fun appendToLog(text: String, tag: String = "SYSTEM") {
        try {
            val docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir?.exists() == false) docsDir.mkdirs()
            val file = File(docsDir, fileName)

            val timestamp = dateFormat.format(Date())
            val line = "$timestamp [$tag] $text\n"

            FileOutputStream(file, true).use { fos -> fos.write(line.toByteArray()) }
        } catch (e: Exception) {
            addMessage("system:~$ Error writing to file: ${e.message}")
        }
    }

    private fun parseProcessList(): List<ProcessInfoExtended> {
        val list = mutableListOf<ProcessInfoExtended>()
        val command = arrayOf("su", "-c", "ps -A -o pid,user,pcpu,rss,args --sort -rss")

        try {
            Runtime.getRuntime().exec(command).inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readLine() // skip header
                    reader.lineSequence()
                        .filter { it.isNotBlank() }
                        .forEachIndexed { index, line ->
                            if (index < 50) parsePsLine(line)?.let { list.add(it) }
                        }
                }
            }
        } catch (e: Exception) {
            addMessage("system:~$ Error getting processes: ${e.message}")
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
            rom = "N/A",
            user = user,
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
}
