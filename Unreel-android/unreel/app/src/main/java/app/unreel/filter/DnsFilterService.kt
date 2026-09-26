package app.unreel.filter

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import app.unreel.data.Prefs
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A DNS-only "VPN". Android sends DNS lookups to a fake server address inside the tunnel;
 * we forward each query to Cloudflare for Families (1.1.1.3), which refuses adult and
 * malware domains, and write the answer back. No other traffic enters the tunnel.
 */
class DnsFilterService : VpnService() {

    companion object {
        const val ACTION_START = "app.unreel.filter.START"
        const val ACTION_STOP = "app.unreel.filter.STOP"
        private const val TUN_ADDRESS = "10.111.222.2"
        private const val FAKE_DNS = "10.111.222.1"
        private val UPSTREAMS = listOf("1.1.1.3", "1.0.0.3")

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startService(Intent(context, DnsFilterService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DnsFilterService::class.java).setAction(ACTION_STOP))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var pool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        val builder = Builder()
            .setSession("Unreel adult content filter")
            .addAddress(TUN_ADDRESS, 32)
            .addRoute(FAKE_DNS, 32)
            .addDnsServer(FAKE_DNS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setBlocking(true)
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            // Not fatal; our upstream sockets are protected anyway.
        }
        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            stopSelf()
            return
        }
        tun = pfd
        pool = Executors.newFixedThreadPool(6)
        isRunning = true
        Thread({ readLoop(pfd) }, "unreel-dns").start()
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (isRunning) {
                val n = input.read(buffer)
                if (n <= 0) {
                    Thread.sleep(20)
                    continue
                }
                val packet = buffer.copyOf(n)
                pool?.execute { handlePacket(packet, output) }
            }
        } catch (e: Exception) {
            // Tunnel closed.
        }
    }

    private fun handlePacket(p: ByteArray, out: FileOutputStream) {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4) return // IPv4 only
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17 || p.size < ihl + 8) return // UDP only
        val totalLen = u16(p, 2).coerceAtMost(p.size)
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53 || totalLen <= ihl + 8) return
        val query = p.copyOfRange(ihl + 8, totalLen)
        val answer = resolve(query) ?: return
        val response = buildResponse(p, srcPort, answer)
        try {
            synchronized(out) { out.write(response) }
        } catch (e: Exception) {
            // Tunnel closed mid-write.
        }
    }

    private fun resolve(query: ByteArray): ByteArray? {
        for (host in UPSTREAMS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4000
                    socket.send(DatagramPacket(query, query.size, InetAddress.getByName(host), 53))
                    val buf = ByteArray(4096)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    return buf.copyOf(reply.length)
                }
            } catch (e: Exception) {
                // Try the next upstream.
            }
        }
        return null
    }

    private fun buildResponse(request: ByteArray, clientPort: Int, answer: ByteArray): ByteArray {
        val udpLen = 8 + answer.size
        val total = 20 + udpLen
        val r = ByteArray(total)
        r[0] = 0x45
        put16(r, 2, total)
        r[6] = 0x40 // don't fragment
        r[8] = 64 // TTL
        r[9] = 17 // UDP
        System.arraycopy(request, 16, r, 12, 4) // source = the fake DNS server
        System.arraycopy(request, 12, r, 16, 4) // destination = the asking app
        put16(r, 10, checksum(r, 0, 20))
        put16(r, 20, 53)
        put16(r, 22, clientPort)
        put16(r, 24, udpLen)
        // UDP checksum 0 = "not computed", valid for IPv4.
        System.arraycopy(answer, 0, r, 28, answer.size)
        return r
    }

    private fun u16(a: ByteArray, off: Int): Int =
        ((a[off].toInt() and 0xFF) shl 8) or (a[off + 1].toInt() and 0xFF)

    private fun put16(a: ByteArray, off: Int, v: Int) {
        a[off] = ((v shr 8) and 0xFF).toByte()
        a[off + 1] = (v and 0xFF).toByte()
    }

    private fun checksum(a: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len) {
            sum += u16(a, i)
            i += 2
        }
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun shutdown() {
        isRunning = false
        try {
            tun?.close()
        } catch (e: Exception) {
        }
        tun = null
        pool?.shutdownNow()
        pool = null
    }

    override fun onRevoke() {
        // Another VPN took over, or the user turned it off in system settings.
        Prefs.dnsFilter = false
        shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
