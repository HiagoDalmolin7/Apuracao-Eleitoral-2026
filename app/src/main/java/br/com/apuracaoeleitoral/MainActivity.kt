package br.com.apuracaoeleitoral

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.content.SharedPreferences
import java.net.URL
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.lifecycle.Lifecycle
import android.util.Base64
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Cores identificadoras dos candidatos. Números e percentuais permanecem pretos.
private val Verde = Color(0xFF198754)
private val Vermelho = Color(0xFFD32F2F)
private val Azul = Color(0xFF234E8A)
private val Fundo = Color(0xFFF5F7FA)
private val Tinta = Color(0xFF172235)
private val Secundario = Color(0xFF667085)
private val Borda = Color(0xFFE4E9F0)
private val Aviso = Color(0xFF966100)
private val PtBr = Locale("pt", "BR")

private val UFS = listOf(
    "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
    "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
)

private fun quantity(raw: String): Double = raw.replace(".", "").replace(",", "")
    .trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

private fun percentInput(raw: String): Double = raw.trim().replace(",", ".")
    .toDoubleOrNull() ?: 0.0

private fun count(n: Double): String = NumberFormat.getIntegerInstance(PtBr)
    .format(n.coerceAtLeast(0.0).toLong())

private fun percentage(n: Double): String = String.format(PtBr, "%.2f%%", n)

private data class StateData(
    val uf: String,
    val urnas: String = "",
    val a: String = "",
    val b: String = "",
    val eleitores: String = "",
    val abst: String = "",
    val brancos: String = "",
    val nulos: String = ""
)

private data class Estimate(
    val a: Double = 0.0,
    val b: Double = 0.0,
    val abst: Double = 0.0,
    val brancos: Double = 0.0,
    val nulos: Double = 0.0,
    val urnas: Double = 0.0,
    val eleitores: Double = 0.0,
    val valid: Boolean = false,
    val warning: String = "",
    val observedA: Double = 0.0,
    val observedB: Double = 0.0
) {
    val validos: Double get() = a + b
    val comparecimento: Double get() = validos + brancos + nulos
}

private fun estimate(s: StateData): Estimate {
    val apuracao = percentInput(s.urnas)
    val p = apuracao / 100.0
    val electorate = quantity(s.eleitores)
    val va = quantity(s.a)
    val vb = quantity(s.b)
    val ab = quantity(s.abst)
    val br = quantity(s.brancos)
    val nu = quantity(s.nulos)

    if (p <= 0.0 || p > 1.0) {
        return Estimate(
            urnas = apuracao,
            eleitores = electorate,
            observedA = va,
            observedB = vb,
            warning = if (va + vb + ab + br + nu > 0) "Informe entre 0,01% e 100% de urnas apuradas." else ""
        )
    }

    val result = Estimate(
        a = va / p,
        b = vb / p,
        abst = ab / p,
        brancos = br / p,
        nulos = nu / p,
        urnas = apuracao,
        eleitores = electorate,
        valid = va + vb > 0,
        observedA = va,
        observedB = vb
    )

    val calculatedPeople = result.comparecimento + result.abst
    val issue = when {
        electorate <= 0.0 -> "Eleitorado não informado: sem conferência de limite."
        calculatedPeople > electorate * 1.001 -> "Atenção: votos + abstenções projetados superam o eleitorado informado."
        calculatedPeople < electorate * 0.85 -> "Atenção: votos + abstenções projetados estão abaixo de 85% do eleitorado. Confira se os dados estão completos."
        else -> ""
    }
    return result.copy(warning = issue)
}

private fun loadState(json: String?, uf: String): StateData = runCatching {
    val obj = JSONObject(json ?: "{}")
    StateData(
        uf = uf,
        urnas = obj.optString("urnas"),
        a = obj.optString("a"),
        b = obj.optString("b"),
        eleitores = obj.optString("eleitores"),
        abst = obj.optString("abst"),
        brancos = obj.optString("brancos"),
        nulos = obj.optString("nulos")
    )
}.getOrElse { StateData(uf) }

private fun StateData.asJson(): String = JSONObject()
    .put("urnas", urnas).put("a", a).put("b", b)
    .put("eleitores", eleitores).put("abst", abst)
    .put("brancos", brancos).put("nulos", nulos).toString()


// === TSE 2026 • Eleição federal 6258 • Presidente, segundo turno ===
// EA20: https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados
// 27 UFs + ZZ (exterior) + BR (consolidado oficial). Os dois últimos NÃO são UFs.
private val TSE_LOCATIONS = UFS + listOf("ZZ", "BR")
private const val TSE_BASE = "https://resultados.tse.jus.br/oficial/ele2026/6258/dados"
private const val TSE_INTERVAL_MS = 60_000L

private data class TseReport(
    val location: String,
    val candidate22: Long,
    val candidate13: Long,
    val sectionsDone: Long,
    val sectionsTotal: Long,
    val electorate: Long,
    val electorateCounted: Long,
    val totalBallots: Long,
    val officialValidVotes: Long,
    val technicalNullVotes: Long,
    val turnout: Long,
    val abstentions: Long,
    val blank: Long,
    val nullVotes: Long,
    val generated: String,
    val generationId: String
) {
    val hasVotes get() = sectionsDone > 0L && candidate22 + candidate13 > 0L
    val sectionsPercent get() = if (sectionsTotal > 0L) sectionsDone * 100.0 / sectionsTotal else 0.0
    val validVotes get() = candidate22 + candidate13
}

private fun JSONObject.longField(key: String): Long = optString(key, "0")
    .replace(".", "").trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L

// Parser conservador: somente eleição, cargo, abrangência e candidaturas esperados.
private fun parseTseReport(json: String, region: String): TseReport? = runCatching {
    val root = JSONObject(json)
    require(root.optString("ele") == "6258" && root.optString("t") == "2")
    require(root.optString("cdabr").equals(region, ignoreCase = true))
    val positions = root.getJSONArray("carg")
    val president = (0 until positions.length()).map { positions.getJSONObject(it) }
        .first { it.optString("cd").toIntOrNull() == 1 }
    var votes22: Long? = null
    var votes13: Long? = null
    val blocks = president.getJSONArray("agr")
    for (i in 0 until blocks.length()) {
        val group = blocks.getJSONObject(i)
        val parties = group.optJSONArray("par") ?: continue
        for (j in 0 until parties.length()) {
            val candidates = parties.getJSONObject(j).optJSONArray("cand") ?: continue
            for (k in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(k)
                when (candidate.optString("n")) {
                    "22" -> votes22 = candidate.longField("vap")
                    "13" -> votes13 = candidate.longField("vap")
                }
            }
        }
    }
    // Não mapear votos por ordem: interromper importação se não achar os dois números.
    require(votes22 != null && votes13 != null)
    val sections = root.getJSONObject("s")
    val voters = root.getJSONObject("e")
    val ballots = root.getJSONObject("v")
    val done = sections.longField("st")
    val total = sections.longField("ts")
    require(total > 0L && done in 0..total)
    require(votes22!! + votes13!! <= ballots.longField("tv") || ballots.longField("tv") == 0L)
    TseReport(
        location = region,
        candidate22 = votes22!!,
        candidate13 = votes13!!,
        sectionsDone = done,
        sectionsTotal = total,
        electorate = voters.longField("te"),
        electorateCounted = voters.longField("est"),
        totalBallots = ballots.longField("tv"),
        officialValidVotes = ballots.longField("vv"),
        technicalNullVotes = ballots.longField("vnt"),
        turnout = voters.longField("c"),
        abstentions = voters.longField("a"),
        blank = ballots.longField("vb"),
        nullVotes = ballots.longField("vn"),
        generated = listOf(root.optString("dg"), root.optString("hg"))
            .filter { it.isNotBlank() }.joinToString(" às "),
        generationId = root.optString("idg")
    )
}.getOrNull()

private fun stateFromOfficial(report: TseReport?): StateData {
    if (report == null) return StateData("")
    return StateData(
        uf = report.location,
        urnas = String.format(Locale.US, "%.5f", report.sectionsPercent),
        a = report.candidate22.toString(),
        b = report.candidate13.toString(),
        eleitores = report.electorate.toString(),
        abst = report.abstentions.toString(),
        brancos = report.blank.toString(),
        nulos = report.nullVotes.toString()
    )
}

private data class DownloadResult(
    val region: String,
    val report: TseReport? = null,
    val json: String? = null,
    val notModified: Boolean = false,
    val errorCode: Int = 0,
    val etag: String? = null,
    val lastModified: String? = null
)

private fun officialUrl(region: String): String {
    val code = region.lowercase(Locale.ROOT)
    return "$TSE_BASE/$code/$code-c0001-e006258-u.json"
}

private fun fetchTseRegion(prefs: SharedPreferences, region: String): DownloadResult {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(officialUrl(region)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ApuracaoEleitoral2026-Android/2.0")
            if (prefs.contains("tse_json_$region")) {
                prefs.getString("tse_etag_$region", null)?.let { setRequestProperty("If-None-Match", it) }
                prefs.getString("tse_modified_$region", null)?.let { setRequestProperty("If-Modified-Since", it) }
            }
        }
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return DownloadResult(region, notModified = true)
        }
        if (code != HttpURLConnection.HTTP_OK) return DownloadResult(region, errorCode = code)
        val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = parseTseReport(raw, region)
            ?: return DownloadResult(region, errorCode = -2) // dados inesperados: preservar cache
        return DownloadResult(
            region = region,
            report = parsed,
            json = raw,
            etag = connection.getHeaderField("ETag"),
            lastModified = connection.getHeaderField("Last-Modified")
        )
    } catch (_: Exception) {
        return DownloadResult(region, errorCode = -1)
    } finally {
        connection?.disconnect()
    }
}

// === Segundo turno estadual, independente da eleição presidencial ===
// Fonte: TSE EA20. Presidente: 6258/cargo 1; governador: 6260/cargo 3.
// Os seis segundos turnos estaduais foram divulgados pelo TSE em 14/10/2026.
private val GOVERNOR_UFS = listOf("AC", "AM", "DF", "ES", "RN", "TO")
private val GOVERNOR_BLUE = Color(0xFF245AA6)
private val GOVERNOR_PURPLE = Color(0xFF9055A2)

private data class GovernorCandidate(
    val number: String,
    val name: String,
    val party: String,
    val votes: Long
)

private data class GovernorReport(
    val uf: String,
    val candidates: List<GovernorCandidate>,
    val sectionsDone: Long,
    val sectionsTotal: Long,
    val electorate: Long,
    val turnout: Long,
    val abstentions: Long,
    val blank: Long,
    val nullVotes: Long,
    val officialValidVotes: Long,
    val generated: String
) {
    val sectionsPercent: Double get() = if (sectionsTotal > 0L) 100.0 * sectionsDone / sectionsTotal else 0.0
    val hasVotes: Boolean get() = sectionsDone > 0L && candidates.sumOf { it.votes } > 0L
    val validVotes: Long get() = candidates.sumOf { it.votes }
    val projected: List<Double> get() = candidates.map { c ->
        if (!hasVotes || sectionsPercent <= 0) 0.0 else c.votes * 100.0 / sectionsPercent
    }
}

private fun governorUrl(uf: String): String {
    val code = uf.lowercase(Locale.ROOT)
    return "https://resultados.tse.jus.br/oficial/ele2026/6260/dados/$code/$code-c0003-e006260-u.json"
}

private fun parseGovernorReport(raw: String, uf: String): GovernorReport? = runCatching {
    val root = JSONObject(raw)
    require(root.optString("ele") == "6260" && root.optString("t") == "2")
    require(root.optString("cdabr").equals(uf, ignoreCase = true))
    val offices = root.getJSONArray("carg")
    val governor = (0 until offices.length()).map { offices.getJSONObject(it) }
        .first { it.optString("cd").toIntOrNull() == 3 }
    val parties = governor.getJSONArray("agr")
    val candidates = mutableListOf<GovernorCandidate>()
    for (i in 0 until parties.length()) {
        val alliance = parties.getJSONObject(i)
        val memberParties = alliance.optJSONArray("par") ?: continue
        for (j in 0 until memberParties.length()) {
            val party = memberParties.getJSONObject(j)
            val nominations = party.optJSONArray("cand") ?: continue
            for (k in 0 until nominations.length()) {
                val c = nominations.getJSONObject(k)
                val number = c.optString("n")
                if (number.isNotBlank()) candidates.add(GovernorCandidate(
                    number = number,
                    name = c.optString("nmu").ifBlank { c.optString("nm") },
                    party = party.optString("sg"),
                    votes = c.longField("vap")
                ))
            }
        }
    }
    // Nunca associar candidatos pela ordem dos blocos ou nomes fictícios.
    require(candidates.size == 2 && candidates.map { it.number }.distinct().size == 2)
    val s = root.getJSONObject("s")
    val e = root.getJSONObject("e")
    val v = root.getJSONObject("v")
    val sectionsTotal = s.longField("ts")
    val sectionsDone = s.longField("st")
    require(sectionsTotal > 0L && sectionsDone in 0..sectionsTotal)
    val totalValid = candidates.sumOf { it.votes }
    require(totalValid <= v.longField("tv") || v.longField("tv") == 0L)
    GovernorReport(
        uf, candidates.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE },
        sectionsDone, sectionsTotal, e.longField("te"), e.longField("c"),
        e.longField("a"), v.longField("vb"), v.longField("vn"),
        v.longField("vv"),
        listOf(root.optString("dg"), root.optString("hg"))
            .filter { it.isNotBlank() }.joinToString(" às ")
    )
}.getOrNull()

private data class GovernorDownload(
    val uf: String,
    val report: GovernorReport? = null,
    val json: String? = null,
    val notModified: Boolean = false,
    val status: Int = 0,
    val etag: String? = null,
    val modified: String? = null
)

private fun fetchGovernor(prefs: SharedPreferences, uf: String): GovernorDownload {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(governorUrl(uf)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ApuracaoEleitoral2026-Android/3.0")
            if (prefs.contains("tse_gov_json_$uf")) {
                prefs.getString("tse_gov_etag_$uf", null)?.let { setRequestProperty("If-None-Match", it) }
                prefs.getString("tse_gov_modified_$uf", null)?.let { setRequestProperty("If-Modified-Since", it) }
            }
        }
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return GovernorDownload(uf, notModified = true)
        if (code != HttpURLConnection.HTTP_OK) return GovernorDownload(uf, status = code)
        val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val report = parseGovernorReport(text, uf)
            ?: return GovernorDownload(uf, status = -2) // não substituir cache em caso de mudança de formato
        return GovernorDownload(uf, report, text,
            etag = connection.getHeaderField("ETag"), modified = connection.getHeaderField("Last-Modified"))
    } catch (_: Exception) {
        return GovernorDownload(uf, status = -1)
    } finally {
        connection?.disconnect()
    }
}

// === Histórico de evolução (gravado localmente; nenhum dado histórico é inventado) ===
private data class EvolutionPoint(
    val moment: Long,
    val observedA: Double,
    val observedB: Double,
    val projectedA: Double,
    val projectedB: Double,
    val percentage: Double
)

private fun readEvolution(prefs: SharedPreferences, id: String): List<EvolutionPoint> = runCatching {
    val data = JSONArray(prefs.getString("evolution_2026_$id", "[]") ?: "[]")
    (0 until data.length()).mapNotNull { i ->
        runCatching {
            val o = data.getJSONObject(i)
            EvolutionPoint(
                o.getLong("t"), o.getDouble("oa"), o.getDouble("ob"),
                o.getDouble("pa"), o.getDouble("pb"), o.optDouble("pct", 0.0)
            )
        }.getOrNull()
    }.filter { it.moment > 0L && it.observedA >= 0 && it.observedB >= 0 }
}.getOrDefault(emptyList())

private fun addEvolution(
    prefs: SharedPreferences,
    id: String,
    old: List<EvolutionPoint>,
    point: EvolutionPoint?
): List<EvolutionPoint> {
    if (point == null || point.observedA + point.observedB <= 0.0) return old
    val previous = old.lastOrNull()
    // O TSE pode devolver o mesmo arquivo em consultas consecutivas. Não criar pontos falsos.
    if (previous != null && previous.observedA == point.observedA &&
        previous.observedB == point.observedB && previous.projectedA == point.projectedA &&
        previous.projectedB == point.projectedB && previous.percentage == point.percentage) return old
    var all = old + point
    // Histórico durável e compacto, mantendo os registros antigos em amostragem progressiva.
    if (all.size > 900) all = listOf(all.first()) + all.drop(1).filterIndexed { index, _ -> index % 2 == 0 }
    val json = JSONArray()
    all.forEach { p ->
        json.put(JSONObject().put("t", p.moment).put("oa", p.observedA)
            .put("ob", p.observedB).put("pa", p.projectedA)
            .put("pb", p.projectedB).put("pct", p.percentage))
    }
    prefs.edit().putString("evolution_2026_$id", json.toString()).apply()
    return all
}

private fun presidentEvolution(
    official: Map<String, TseReport>,
    manual: Map<String, StateData>?,
    moment: Long
): EvolutionPoint? {
    val inputs = if (manual != null) manual else
        (UFS + "ZZ").associateWith { uf -> official[uf]?.let(::stateFromOfficial) ?: StateData(uf) }
    val regional = inputs.values.map(::estimate).filter { it.valid }
    val projA = regional.sumOf { it.a }
    val projB = regional.sumOf { it.b }
    val countedFromRegionsA = regional.sumOf { it.observedA }
    val countedFromRegionsB = regional.sumOf { it.observedB }
    val brazil = if (manual == null) official["BR"] else null
    val observedA = brazil?.takeIf { it.hasVotes }?.candidate22?.toDouble() ?: countedFromRegionsA
    val observedB = brazil?.takeIf { it.hasVotes }?.candidate13?.toDouble() ?: countedFromRegionsB
    val apurado = if (brazil != null && brazil.sectionsTotal > 0L) brazil.sectionsPercent else {
        // No manual, sem número de seções por UF, não há % nacional ponderado confiável.
        0.0
    }
    if (observedA + observedB <= 0.0) return null
    return EvolutionPoint(moment, observedA, observedB, projA, projB, apurado)
}

private fun governorEvolution(report: GovernorReport, moment: Long): EvolutionPoint? {
    if (!report.hasVotes || report.candidates.size != 2) return null
    return EvolutionPoint(
        moment, report.candidates[0].votes.toDouble(), report.candidates[1].votes.toDouble(),
        report.projected[0], report.projected[1], report.sectionsPercent
    )
}

private fun clockTime(whenMs: Long): String = if (whenMs <= 0L) "—"
    else SimpleDateFormat("dd/MM HH:mm:ss", PtBr).format(Date(whenMs))

private fun nameForRegion(uf: String): String = if (uf == "ZZ") "Exterior (ZZ)" else uf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retoma a sessão SOMENTE se o usuário havia ativado a notificação.
        val prefs = getSharedPreferences("apuracao_v2", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notificacao_projecao_ativa", false) && notificationsAllowed(this)) {
            runCatching { startProjectionService(this) }.onFailure {
                prefs.edit().putBoolean("notificacao_projecao_ativa", false).apply()
            }
        }
        setContent { ElectionApp() }
    }

    override fun onStart() {
        super.onStart()
        ProjectionNotificationService.appVisible = true
    }

    override fun onStop() {
        ProjectionNotificationService.appVisible = false
        super.onStop()
    }
}


// === Notificação persistente opcional da projeção ===
// Enquanto o app estiver visível, a tela consulta o TSE e o serviço usa o cache.
// Em segundo plano, este serviço dataSync mantém uma consulta a cada ~60 segundos.
private const val PROJECTION_CHANNEL = "apuracao_projecao_2026"
private const val PROJECTION_NOTIFICATION_ID = 2026
private const val PROJECTION_STOP_ACTION = "br.com.apuracaoeleitoral.STOP_PROJECTION"
private const val PROJECTION_REFRESH_ACTION = "br.com.apuracaoeleitoral.REFRESH_PROJECTION"

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun startProjectionService(context: Context) {
    context.startForegroundService(Intent(context, ProjectionNotificationService::class.java))
}

private fun stopProjectionService(context: Context) {
    context.stopService(Intent(context, ProjectionNotificationService::class.java))
}

private fun refreshProjectionNotification(context: Context) {
    // Apenas atualizar quando o serviço já foi solicitado pelo usuário.
    if (context.getSharedPreferences("apuracao_v2", Context.MODE_PRIVATE)
            .getBoolean("notificacao_projecao_ativa", false)) {
        runCatching {
            context.startService(Intent(context, ProjectionNotificationService::class.java)
                .setAction(PROJECTION_REFRESH_ACTION))
        }
    }
}

class ProjectionNotificationService : Service() {
    companion object {
        @Volatile var appVisible: Boolean = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val backoffUntil = mutableMapOf<String, Long>()
    private lateinit var preferences: SharedPreferences
    private lateinit var manager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = getSharedPreferences("apuracao_v2", Context.MODE_PRIVATE)
        manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(PROJECTION_CHANNEL, "Projeção da apuração", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Mostra a projeção atual enquanto a apuração está sendo acompanhada." }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PROJECTION_STOP_ACTION) {
            endSession()
            return START_NOT_STICKY
        }
        if (!preferences.getBoolean("notificacao_projecao_ativa", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Iniciar a notificação IMEDIATAMENTE, antes de executar qualquer requisição.
        val initial = makeNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(PROJECTION_NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(PROJECTION_NOTIFICATION_ID, initial)
        }
        if (pollingJob == null || pollingJob?.isActive != true) {
            pollingJob = scope.launch {
                while (isActive) {
                    if (!appVisible) syncInBackground()
                    manager.notify(PROJECTION_NOTIFICATION_ID, makeNotification())
                    delay(if (appVisible) 15_000L else TSE_INTERVAL_MS)
                }
            }
        } else if (intent?.action == PROJECTION_REFRESH_ACTION) {
            scope.launch { manager.notify(PROJECTION_NOTIFICATION_ID, makeNotification()) }
        }
        // Não reiniciar silenciosamente se o sistema encerrar o serviço.
        return START_NOT_STICKY
    }

    private suspend fun syncInBackground() {
        // Nunca consultar resultados presidenciais enquanto o usuário estiver em modo manual.
        if (preferences.getString("eleicao_2026", "presidente") == "governador") {
            val uf = preferences.getString("governador_uf_2026", "AC") ?: "AC"
            if (uf !in GOVERNOR_UFS) return
            val id = "gov_$uf"
            if (System.currentTimeMillis() < (backoffUntil[id] ?: 0L)) return
            val result = fetchGovernor(preferences, uf)
            if (result.report != null && result.json != null) {
                preferences.edit().putString("tse_gov_json_$uf", result.json)
                    .putLong("tse_gov_last_ok", System.currentTimeMillis()).apply()
                val old = readEvolution(preferences, id)
                addEvolution(preferences, id, old, governorEvolution(result.report, System.currentTimeMillis()))
            } else if (result.status == 404 || result.status == 403 || result.status == 429) {
                backoffUntil[id] = System.currentTimeMillis() + 300_000L
            }
            return
        }
        if (preferences.getBoolean("modo_manual", false)) return
        // Consulta em pequenos lotes, reutilizando a lógica, os validadores e o cache existentes.
        val semaphore = Semaphore(3)
        val regions = TSE_LOCATIONS.filter {
            System.currentTimeMillis() >= (backoffUntil[it] ?: 0L)
        }
        if (regions.isEmpty()) return
        val results = coroutineScope {
            regions.map { uf -> async { semaphore.withPermit { fetchTseRegion(preferences, uf) } } }.awaitAll()
        }
        var newData = false
        results.forEach { result ->
            if (result.report != null && result.json != null) {
                val editor = preferences.edit().putString("tse_json_${result.region}", result.json)
                result.etag?.let { editor.putString("tse_etag_${result.region}", it) }
                result.lastModified?.let { editor.putString("tse_modified_${result.region}", it) }
                editor.apply()
                newData = true
            } else if (result.errorCode == 403 || result.errorCode == 404 || result.errorCode == 429) {
                backoffUntil[result.region] = System.currentTimeMillis() +
                    if (result.errorCode == 404) 240_000L else 300_000L
            }
        }
        if (newData) {
            preferences.edit().putLong("tse_last_ok", System.currentTimeMillis()).apply()
            val official = TSE_LOCATIONS.mapNotNull { uf ->
                preferences.getString("tse_json_$uf", null)?.let { json ->
                    parseTseReport(json, uf)?.let { uf to it }
                }
            }.toMap()
            addEvolution(preferences, "presidente_tse", readEvolution(preferences, "presidente_tse"),
                presidentEvolution(official, null, System.currentTimeMillis()))
        }
    }

    private fun makeNotification(): Notification {
        val (heading, line) = currentProjection()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ProjectionNotificationService::class.java).setAction(PROJECTION_STOP_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, PROJECTION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(heading)
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(Notification.Action.Builder(null, "Encerrar", stop).build())
            .build()
    }

    private fun currentProjection(): Pair<String, String> {
        if (preferences.getString("eleicao_2026", "presidente") == "governador") {
            val uf = preferences.getString("governador_uf_2026", "AC") ?: "AC"
            val report = preferences.getString("tse_gov_json_$uf", null)
                ?.let { parseGovernorReport(it, uf) }
            if (report == null || !report.hasVotes || report.candidates.size != 2) {
                return "Projeção • Governador $uf" to "Aguardando votos oficiais"
            }
            val a = report.projected[0]
            val b = report.projected[1]
            val total = a + b
            if (total <= 0) return "Projeção • Governador $uf" to "Aguardando apuração"
            return "Projeção • Governador $uf" to
                "${report.candidates[0].name}: ${percentage(a / total * 100)}  •  ${report.candidates[1].name}: ${percentage(b / total * 100)}"
        }
        val manual = preferences.getBoolean("modo_manual", false)
        val states = (UFS + "ZZ").map { uf ->
            if (manual) loadState(preferences.getString("uf_$uf", null), uf)
            else preferences.getString("tse_json_$uf", null)
                ?.let { parseTseReport(it, uf) }?.let { stateFromOfficial(it) } ?: StateData(uf)
        }
        val good = states.map(::estimate).filter { it.valid }
        val a = good.sumOf { it.a }
        val b = good.sumOf { it.b }
        val total = a + b
        if (total <= 0) return "Projeção • Presidente" to
            (if (manual) "Sem dados manuais para projetar" else "Aguardando votos do TSE")
        return "Projeção • Presidente" to
            "Flávio Bolsonaro: ${percentage(a / total * 100)}  •  Lula: ${percentage(b / total * 100)}"
    }

    private fun endSession() {
        preferences.edit().putBoolean("notificacao_projecao_ativa", false).apply()
        pollingJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Android 15+: o serviço dataSync precisa parar imediatamente quando atingir a cota.
    override fun onTimeout(startId: Int, fgsType: Int) {
        endSession()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectionApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as ComponentActivity
    // Cache separado dos dados manuais anteriores: não apagamos o que já estava salvo.
    val prefs = remember { context.getSharedPreferences("apuracao_v2", 0) }
    var reports by remember {
        mutableStateOf(TSE_LOCATIONS.mapNotNull { region ->
            val raw = prefs.getString("tse_json_$region", null)
            if (raw != null) parseTseReport(raw, region)?.let { region to it } else null
        }.toMap())
    }
    var loading by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableLongStateOf(0L) }
    var lastGood by remember { mutableLongStateOf(prefs.getLong("tse_last_ok", 0L)) }
    var unavailable by remember { mutableStateOf(emptySet<String>()) }
    var needsRetry by remember { mutableIntStateOf(0) }
    // Eleição selecionada e UF de governador têm estado próprio: não misturam resultado presidencial.
    var electionMode by remember { mutableStateOf(prefs.getString("eleicao_2026", "presidente") ?: "presidente") }
    var selectedGovernorUF by remember {
        mutableStateOf(prefs.getString("governador_uf_2026", "AC")?.takeIf { it in GOVERNOR_UFS } ?: "AC")
    }
    var governorReports by remember {
        mutableStateOf(GOVERNOR_UFS.mapNotNull { uf ->
            prefs.getString("tse_gov_json_$uf", null)?.let { text ->
                parseGovernorReport(text, uf)?.let { report -> uf to report }
            }
        }.toMap())
    }
    var governorLoading by remember { mutableStateOf(false) }
    var governorLastGood by remember { mutableLongStateOf(prefs.getLong("tse_gov_last_ok", 0L)) }
    var governorFailed by remember { mutableStateOf(emptySet<String>()) }
    var governorRetry by remember { mutableIntStateOf(0) }
    // Histórico separado por eleição, modo manual e cada unidade federativa.
    var autoEvolution by remember { mutableStateOf(readEvolution(prefs, "presidente_tse")) }
    var manualEvolution by remember { mutableStateOf(readEvolution(prefs, "presidente_manual")) }
    var governorEvolutionHistory by remember {
        mutableStateOf(GOVERNOR_UFS.associateWith { readEvolution(prefs, "gov_$it") })
    }
    var selectedUF by remember { mutableStateOf("SC") }
    var tab by remember { mutableIntStateOf(0) }
    var showOptions by remember { mutableStateOf(false) }
    // O modo manual utiliza as mesmas chaves da versão original (uf_SC, uf_GO...).
    // Os arquivos oficiais ficam SOMENTE em tse_json_*, sem sobrescrever a digitação.
    var manualMode by remember { mutableStateOf(prefs.getBoolean("modo_manual", false)) }
    var notificationOn by remember { mutableStateOf(prefs.getBoolean("notificacao_projecao_ativa", false)) }
    var notificationError by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            prefs.edit().putBoolean("notificacao_projecao_ativa", true).apply()
            notificationOn = true
            notificationError = false
            runCatching { startProjectionService(context) }.onFailure {
                notificationOn = false
                prefs.edit().putBoolean("notificacao_projecao_ativa", false).apply()
                notificationError = true
            }
        } else {
            notificationOn = false
            prefs.edit().putBoolean("notificacao_projecao_ativa", false).apply()
            notificationError = true
        }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationOn = prefs.getBoolean("notificacao_projecao_ativa", false)
                // O serviço pode ter atualizado o cache enquanto o app estava em segundo plano.
                reports = TSE_LOCATIONS.mapNotNull { uf ->
                    prefs.getString("tse_json_$uf", null)?.let { json ->
                        parseTseReport(json, uf)?.let { uf to it }
                    }
                }.toMap()
                governorReports = GOVERNOR_UFS.mapNotNull { uf ->
                    prefs.getString("tse_gov_json_$uf", null)?.let { json ->
                        parseGovernorReport(json, uf)?.let { uf to it }
                    }
                }.toMap()
                autoEvolution = readEvolution(prefs, "presidente_tse")
                governorEvolutionHistory = GOVERNOR_UFS.associateWith { readEvolution(prefs, "gov_$it") }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    fun changeNotifications(enabled: Boolean) {
        notificationError = false
        if (!enabled) {
            prefs.edit().putBoolean("notificacao_projecao_ativa", false).apply()
            notificationOn = false
            stopProjectionService(context)
        } else if (notificationsAllowed(context)) {
            prefs.edit().putBoolean("notificacao_projecao_ativa", true).apply()
            notificationOn = true
            runCatching { startProjectionService(context) }.onFailure {
                notificationOn = false
                notificationError = true
                prefs.edit().putBoolean("notificacao_projecao_ativa", false).apply()
            }
        } else if (Build.VERSION.SDK_INT >= 33) {
            permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var manualStates by remember {
        mutableStateOf((UFS + "ZZ").associateWith { uf ->
            loadState(prefs.getString("uf_$uf", null), uf)
        })
    }
    fun updateManual(updated: StateData) {
        manualStates = manualStates + (updated.uf to updated)
        prefs.edit().putString("uf_${updated.uf}", updated.asJson()).apply()
        refreshProjectionNotification(context)
    }

    // Atualiza a cada 60 segundos enquanto o aplicativo está aberto em primeiro plano.
    // Pausa automaticamente quando a atividade deixa de estar visível.
    LaunchedEffect(needsRetry, manualMode, electionMode) {
        if (manualMode || electionMode != "presidente") {
            // Sem requisições ao TSE neste modo; a coleta é cancelada ao mudar o interruptor.
            loading = false
            return@LaunchedEffect
        }
        val limit = Semaphore(3)
        val retryAfter = mutableMapOf<String, Long>()
        while (isActive) {
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(1_500L)
                continue
            }
            val due = TSE_LOCATIONS.filter { System.currentTimeMillis() >= (retryAfter[it] ?: 0L) }
            if (due.isEmpty()) { delay(TSE_INTERVAL_MS); continue }
            loading = true
            val answers = withContext(Dispatchers.IO) {
                coroutineScope {
                    due.map { region ->
                        async { limit.withPermit { fetchTseRegion(prefs, region) } }
                    }.awaitAll()
                }
            }
            var updated = reports
            val edit = prefs.edit()
            var successful = 0
            val failures = mutableSetOf<String>()
            var throttled = false
            for (answer in answers) {
                when {
                    answer.report != null && answer.json != null -> {
                        // Somente arquivos oficialmente válidos sobrescrevem o último registro.
                        updated = updated + (answer.region to answer.report)
                        edit.putString("tse_json_${answer.region}", answer.json)
                        if (answer.etag != null) edit.putString("tse_etag_${answer.region}", answer.etag)
                        if (answer.lastModified != null) edit.putString("tse_modified_${answer.region}", answer.lastModified)
                        successful++
                        retryAfter.remove(answer.region)
                    }
                    answer.notModified && updated.containsKey(answer.region) -> {
                        successful++
                        retryAfter.remove(answer.region)
                    }
                    else -> {
                        failures.add(answer.region)
                        if (answer.errorCode == 403 || answer.errorCode == 429) {
                            throttled = true
                            retryAfter[answer.region] = System.currentTimeMillis() + 300_000L
                        } else if (answer.errorCode == 404) {
                            // Não repetir 404 a cada minuto antes da publicação do TSE.
                            retryAfter[answer.region] = System.currentTimeMillis() + 240_000L
                        }
                    }
                }
            }
            lastCheck = System.currentTimeMillis()
            if (successful > 0) {
                lastGood = lastCheck
                edit.putLong("tse_last_ok", lastGood)
            }
            edit.apply()
            reports = updated
            refreshProjectionNotification(context)
            unavailable = failures + retryAfter.keys.filter { System.currentTimeMillis() < (retryAfter[it] ?: 0L) }
            loading = false
            // Bloqueio temporário 403/429: recuar em vez de sobrecarregar o servidor.
            delay(if (throttled) 300_000L else TSE_INTERVAL_MS)
        }
    }

    // A eleição estadual possui seu próprio endpoint, cache e rotina de consulta.
    // Apenas uma rotina (Presidência ou Governador) faz requisições por vez.
    LaunchedEffect(governorRetry, electionMode) {
        if (electionMode != "governador") {
            governorLoading = false
            return@LaunchedEffect
        }
        val semaphore = Semaphore(2)
        val retryAfter = mutableMapOf<String, Long>()
        while (isActive) {
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(1_500L)
                continue
            }
            val due = GOVERNOR_UFS.filter { System.currentTimeMillis() >= (retryAfter[it] ?: 0L) }
            if (due.isEmpty()) { delay(TSE_INTERVAL_MS); continue }
            governorLoading = true
            val fresh = withContext(Dispatchers.IO) {
                coroutineScope {
                    due.map { uf -> async { semaphore.withPermit { fetchGovernor(prefs, uf) } } }.awaitAll()
                }
            }
            var revised = governorReports
            var success = false
            val bad = mutableSetOf<String>()
            val changes = prefs.edit()
            var throttled = false
            for (answer in fresh) {
                when {
                    answer.report != null && answer.json != null -> {
                        revised = revised + (answer.uf to answer.report)
                        changes.putString("tse_gov_json_${answer.uf}", answer.json)
                        answer.etag?.let { changes.putString("tse_gov_etag_${answer.uf}", it) }
                        answer.modified?.let { changes.putString("tse_gov_modified_${answer.uf}", it) }
                        retryAfter.remove(answer.uf)
                        success = true
                    }
                    answer.notModified && revised.containsKey(answer.uf) -> {
                        retryAfter.remove(answer.uf)
                        success = true
                    }
                    else -> {
                        bad.add(answer.uf)
                        if (answer.status == 403 || answer.status == 429) {
                            retryAfter[answer.uf] = System.currentTimeMillis() + 300_000L
                            throttled = true
                        } else if (answer.status == 404) {
                            retryAfter[answer.uf] = System.currentTimeMillis() + 240_000L
                        }
                    }
                }
            }
            governorReports = revised
            refreshProjectionNotification(context)
            if (success) {
                governorLastGood = System.currentTimeMillis()
                changes.putLong("tse_gov_last_ok", governorLastGood)
            }
            changes.apply()
            governorFailed = bad + retryAfter.keys.filter { System.currentTimeMillis() < (retryAfter[it] ?: 0L) }
            governorLoading = false
            delay(if (throttled) 300_000L else TSE_INTERVAL_MS)
        }
    }

    // Registrar somente observações efetivamente recebidas. Após fechar e reabrir,
    // o cache existente vira a primeira observação se o histórico ainda estiver vazio.
    LaunchedEffect(reports) {
        autoEvolution = addEvolution(
            prefs, "presidente_tse", autoEvolution,
            presidentEvolution(reports, null, System.currentTimeMillis())
        )
    }
    LaunchedEffect(governorReports) {
        var changed = governorEvolutionHistory
        governorReports.forEach { (uf, report) ->
            val before = changed[uf].orEmpty()
            val after = addEvolution(prefs, "gov_$uf", before,
                governorEvolution(report, System.currentTimeMillis()))
            if (after !== before) changed = changed + (uf to after)
        }
        governorEvolutionHistory = changed
    }
    LaunchedEffect(manualStates) {
        // Esperar o usuário terminar de digitar, sem gravar cada tecla como uma nova atualização.
        delay(1_200L)
        manualEvolution = addEvolution(
            prefs, "presidente_manual", manualEvolution,
            presidentEvolution(emptyMap(), manualStates, System.currentTimeMillis())
        )
    }

    val officialStates = remember(reports) {
        (UFS + "ZZ").associateWith { region ->
            stateFromOfficial(reports[region]).let { if (it.uf.isBlank()) it.copy(uf = region) else it }
        }
    }
    // A mesma lógica de projeção e o mesmo mapa são utilizados em ambos os modos.
    val all = if (manualMode) manualStates else officialStates
    val projections = remember(all) { all.mapValues { estimate(it.value) } }
    val projected = projections.values.filter { it.valid }
    val projectedA = projected.sumOf { it.a }
    val projectedB = projected.sumOf { it.b }
    val mainReport = reports["BR"]
    val exterior = reports["ZZ"]
    val status = when {
        manualMode -> "Modo manual • alterações salvas automaticamente"
        loading && lastCheck == 0L -> "Conectando ao TSE..."
        lastGood == 0L -> "Aguardando dados oficiais do TSE"
        unavailable.isNotEmpty() -> "Última consulta: ${clockTime(lastCheck)} • ${unavailable.size} fonte(s) indisponível(is)"
        else -> "Sincronizado: ${clockTime(lastGood)} • a cada 60 s"
    }
    val labelsA = "Flávio Bolsonaro"
    val labelsB = "Lula"

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Azul, background = Fundo, surface = Color.White,
            onSurface = Tinta, onBackground = Tinta
        ), typography = Typography()
    ) {
        if (showOptions) {
            ElectionOptionsSheet(
                electionMode = electionMode,
                selectedGovernor = selectedGovernorUF,
                automatic = !manualMode,
                notificationEnabled = notificationOn,
                notificationError = notificationError,
                status = if (electionMode == "governador") {
                    when {
                        governorLoading -> "Sincronizando resultados estaduais..."
                        governorLastGood > 0L -> "Última atualização: ${clockTime(governorLastGood)}"
                        else -> "Aguardando dados oficiais do TSE"
                    }
                } else status,
                onDismiss = { showOptions = false },
                onPresident = {
                    electionMode = "presidente"
                    tab = 0
                    prefs.edit().putString("eleicao_2026", "presidente").apply()
                    refreshProjectionNotification(context)
                },
                onGovernor = { uf ->
                    electionMode = "governador"
                    selectedGovernorUF = uf
                    tab = 0
                    prefs.edit().putString("eleicao_2026", "governador")
                        .putString("governador_uf_2026", uf).apply()
                    refreshProjectionNotification(context)
                },
                onAutomaticChange = { enabled ->
                    manualMode = !enabled
                    prefs.edit().putBoolean("modo_manual", !enabled).apply()
                    refreshProjectionNotification(context)
                },
                onNotificationChange = { changeNotifications(it) },
                onRefresh = {
                    if (electionMode == "governador") governorRetry++
                    else if (!manualMode) needsRetry++
                    showOptions = false
                }
            )
        }
        Scaffold(
            containerColor = Fundo,
            topBar = {
                // Header compacto: todas as configurações foram movidas para um menu modal.
                // A área superior respeita o recorte da câmera e a barra de status do Android.
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        title = {
                            Column(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable { showOptions = true }
                                    .padding(vertical = 3.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Apuração Eleitoral 2026", fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp, color = Tinta, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (electionMode == "governador") "Governador • $selectedGovernorUF • TSE"
                                        else if (manualMode) "Presidente • Brasil • Manual"
                                        else "Presidente • Brasil • TSE",
                                        fontSize = 11.sp, color = Secundario, maxLines = 1
                                    )
                                    Text("  ⌄", fontSize = 14.sp, color = Azul)
                                }
                            }
                        },
                        actions = {
                            if (electionMode == "governador" || !manualMode) {
                                if (if (electionMode == "governador") governorLoading else loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(horizontal = 10.dp).size(18.dp),
                                        strokeWidth = 2.dp, color = Azul
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (electionMode == "governador") governorRetry++ else needsRetry++
                                        },
                                        modifier = Modifier.semantics { contentDescription = "Atualizar resultados agora" }
                                    ) {
                                        Text("↻", fontSize = 26.sp, color = Azul)
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showOptions = true },
                                modifier = Modifier.padding(end = 7.dp)
                                    .semantics { contentDescription = "Abrir menu de opções" }
                            ) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(Color(0xFFF0F3F8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⋮", fontSize = 25.sp, color = Tinta, lineHeight = 28.sp)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                    HorizontalDivider(color = Borda, thickness = 0.8.dp)
                }
            },
            bottomBar = {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Painel", "Estados", "Mapa", "Gráfico", "Estat.").forEachIndexed { index, label ->
                            BottomTab(label, index, tab == index, { tab = index }, Modifier.weight(1f))
                        }
                    }
                }
            }
        ) { padding ->
            if (electionMode == "governador") {
                val stateReport = governorReports[selectedGovernorUF]
                val govStatus = when {
                    governorLoading && governorLastGood == 0L -> "Conectando aos resultados estaduais do TSE..."
                    governorLastGood == 0L -> "Aguardando arquivos oficiais estaduais do TSE"
                    governorFailed.isNotEmpty() -> "Última sincronização: ${clockTime(governorLastGood)} • fontes indisponíveis: ${governorFailed.sorted().joinToString()}"
                    else -> "Dados TSE: ${clockTime(governorLastGood)} • atualização a cada 60 s"
                }
                when (tab) {
                    0 -> GovernorDashboard(
                        Modifier.padding(padding), selectedGovernorUF, stateReport,
                        governorReports, govStatus,
                        onChoose = { uf -> selectedGovernorUF = uf; prefs.edit().putString("governador_uf_2026", uf).apply() },
                        onDetails = { tab = 1 }
                    )
                    1 -> GovernorRegions(
                        Modifier.padding(padding), selectedGovernorUF, governorReports,
                        govStatus, governorFailed,
                        onSelect = { uf -> selectedGovernorUF = uf; prefs.edit().putString("governador_uf_2026", uf).apply() }
                    )
                    2 -> GovernorMap(
                        Modifier.padding(padding), selectedGovernorUF, governorReports,
                        onSelect = { uf -> selectedGovernorUF = uf; prefs.edit().putString("governador_uf_2026", uf).apply() },
                        onDetails = { tab = 1 }
                    )
                    3 -> EvolutionPage(
                        modifier = Modifier.padding(padding),
                        title = "Governador • $selectedGovernorUF",
                        points = governorEvolutionHistory[selectedGovernorUF].orEmpty(),
                        nameA = stateReport?.candidates?.getOrNull(0)?.name ?: "Candidato 1",
                        nameB = stateReport?.candidates?.getOrNull(1)?.name ?: "Candidato 2",
                        colorA = GOVERNOR_BLUE, colorB = GOVERNOR_PURPLE,
                        description = "Dados oficiais do TSE. As projeções usam o percentual de seções apuradas."
                    )
                    else -> GovernorStatistics(Modifier.padding(padding), selectedGovernorUF, stateReport, govStatus)
                }
            } else if (manualMode) {
                when (tab) {
                    0 -> ManualDashboard(
                        modifier = Modifier.padding(padding),
                        candidateA = labelsA, candidateB = labelsB,
                        states = manualStates, computed = projections,
                        onChoose = { selectedUF = it; tab = 1 },
                        onStates = { tab = 1 }
                    )
                    1 -> ManualRegions(
                        modifier = Modifier.padding(padding),
                        candidateA = labelsA, candidateB = labelsB,
                        selected = selectedUF, onSelect = { selectedUF = it },
                        state = manualStates.getValue(selectedUF),
                        projection = projections.getValue(selectedUF),
                        all = manualStates,
                        onUpdate = { updateManual(it) }
                    )
                    2 -> MapPage(
                        modifier = Modifier.padding(padding),
                        candidateA = labelsA, candidateB = labelsB,
                        computed = projections.filterKeys { it in UFS },
                        editState = { selectedUF = it; tab = 1 }, manualMode = true
                    )
                    3 -> EvolutionPage(
                        modifier = Modifier.padding(padding),
                        title = "Presidente • modo manual",
                        points = manualEvolution,
                        nameA = labelsA, nameB = labelsB,
                        colorA = Verde, colorB = Vermelho,
                        description = "Histórico dos valores digitados no aparelho. Não são dados oficiais do TSE.",
                        onClear = {
                            prefs.edit().remove("evolution_2026_presidente_manual").apply()
                            manualEvolution = emptyList()
                        }
                    )
                    else -> ManualStatistics(
                        modifier = Modifier.padding(padding),
                        states = manualStates, projections = projections
                    )
                }
            } else {
                when (tab) {
                    0 -> OfficialDashboard(
                        modifier = Modifier.padding(padding),
                        candidateA = labelsA, candidateB = labelsB,
                        national = mainReport, exterior = exterior,
                        projectedA = projectedA, projectedB = projectedB,
                        stateEstimates = projections, status = status,
                        failed = unavailable,
                        onChoose = { selectedUF = it; tab = 1 },
                        onStates = { tab = 1 }
                    )
                    1 -> OfficialRegions(
                        modifier = Modifier.padding(padding),
                        candidateA = labelsA, candidateB = labelsB,
                        selected = selectedUF, onSelect = { selectedUF = it },
                        report = reports[selectedUF], estimate = projections.getValue(selectedUF),
                        all = reports, failures = unavailable, status = status
                    )
                    2 -> MapPage(
                        modifier = Modifier.padding(padding),
                        candidateA = labelsA, candidateB = labelsB,
                        computed = projections.filterKeys { it in UFS },
                        editState = { selectedUF = it; tab = 1 }, manualMode = false
                    )
                    3 -> EvolutionPage(
                        modifier = Modifier.padding(padding),
                        title = "Presidente • TSE",
                        points = autoEvolution,
                        nameA = labelsA, nameB = labelsB,
                        colorA = Verde, colorB = Vermelho,
                        description = "Votos apurados oficialmente e projeção por UF, incluindo exterior quando disponível. Projeção não oficial."
                    )
                    else -> OfficialStatistics(
                        modifier = Modifier.padding(padding),
                        national = mainReport,
                        exterior = exterior,
                        sources = reports,
                        projected = projections,
                        status = status,
                        failed = unavailable
                    )
                }
            }
        }
    }
}

// Menu compacto de preferências acessível pelos três pontos ou pelo subtítulo.
// Todas as opções permanecem disponíveis, mas fora da área dos resultados.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectionOptionsSheet(
    electionMode: String,
    selectedGovernor: String,
    automatic: Boolean,
    notificationEnabled: Boolean,
    notificationError: Boolean,
    status: String,
    onDismiss: () -> Unit,
    onPresident: () -> Unit,
    onGovernor: (String) -> Unit,
    onAutomaticChange: (Boolean) -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = Color(0xFFF7F9FC),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Opções da apuração", color = Tinta, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Personalize a eleição e a atualização dos dados",
                        fontSize = 12.sp, color = Secundario)
                }
                TextButton(onClick = onDismiss) { Text("Concluir", color = Azul) }
            }

            Surface(shape = RoundedCornerShape(18.dp), color = Color.White,
                border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ELEIÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Secundario, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (electionMode == "presidente") {
                            Button(onClick = onPresident, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Presidente", maxLines = 1, fontSize = 13.sp)
                            }
                        } else {
                            OutlinedButton(onClick = onPresident, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Presidente", maxLines = 1, fontSize = 13.sp)
                            }
                        }
                        if (electionMode == "governador") {
                            Button(onClick = { onGovernor(selectedGovernor) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Governador", maxLines = 1, fontSize = 13.sp)
                            }
                        } else {
                            OutlinedButton(onClick = { onGovernor(selectedGovernor) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Governador", maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }
                    if (electionMode == "governador") {
                        HorizontalDivider(color = Borda)
                        Text("Segundo turno por estado", fontSize = 12.sp, color = Secundario)
                        GOVERNOR_UFS.chunked(3).forEach { group ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.forEach { uf ->
                                    if (uf == selectedGovernor) {
                                        Button(onClick = { onGovernor(uf) }, modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp)) {
                                            Text(uf, fontSize = 13.sp)
                                        }
                                    } else {
                                        OutlinedButton(onClick = { onGovernor(uf) }, modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp)) {
                                            Text(uf, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(18.dp), color = Color.White,
                border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("PREFERÊNCIAS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Secundario, letterSpacing = 1.sp)
                    if (electionMode == "presidente") {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Dados automáticos do TSE", fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = Tinta)
                                Text(if (automatic) "Fonte oficial selecionada" else "Digitação manual habilitada",
                                    fontSize = 11.sp, color = Secundario)
                            }
                            Switch(checked = automatic, onCheckedChange = onAutomaticChange,
                                colors = SwitchDefaults.colors(checkedTrackColor = Azul))
                        }
                        HorizontalDivider(color = Borda)
                    } else {
                        Text("Governadores: dados oficiais do TSE, sem preenchimento manual.",
                            fontSize = 12.sp, color = Secundario)
                        HorizontalDivider(color = Borda)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Notificação da projeção", fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, color = Tinta)
                            Text("Acompanhar em segundo plano", fontSize = 11.sp, color = Secundario)
                        }
                        Switch(checked = notificationEnabled, onCheckedChange = onNotificationChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = Azul))
                    }
                    if (notificationError) {
                        Text("Para ativar, permita as notificações nas configurações do Android.",
                            fontSize = 11.sp, color = Vermelho)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(18.dp), color = Color.White,
                border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SINCRONIZAÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Secundario, letterSpacing = 1.sp)
                    Text(status, fontSize = 12.sp, color = Secundario)
                    if (electionMode == "governador" || automatic) {
                        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("↻  Atualizar dados agora", color = Azul, fontSize = 13.sp)
                        }
                    } else {
                        Text("Os dados manuais são salvos automaticamente no celular.",
                            fontSize = 12.sp, color = Secundario)
                    }
                }
            }
            Text("Aplicativo independente • Resultados oficiais do TSE e projeções matemáticas não oficiais.",
                fontSize = 11.sp, color = Secundario, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ManualDashboard(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    states: Map<String, StateData>, computed: Map<String, Estimate>,
    onChoose: (String) -> Unit, onStates: () -> Unit
) {
    val valid = computed.filterValues { it.valid }
    val a = valid.values.sumOf { it.a }
    val b = valid.values.sumOf { it.b }
    val sum = a + b
    val observed = states.values.sumOf { quantity(it.a) + quantity(it.b) }
    ContentColumn(modifier) {
        Note("MODO MANUAL • Todos os valores desta tela vêm da sua digitação, não do TSE. A projeção matemática é independente e não oficial.")
        SurfaceCard {
            Text("Projeção proporcional", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("${valid.size} de 28 regiões preenchidas (27 UFs + exterior)", color = Secundario, fontSize = 12.sp)
            if (sum > 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CandidateInfo(candidateA, a, a / sum, Verde, Modifier.weight(1f))
                    CandidateInfo(candidateB, b, b / sum, Vermelho, Modifier.weight(1f))
                }
                RatioBar(a, b, thickness = 14)
                SummaryLine("Diferença projetada", "${count(abs(a-b))} votos")
                SummaryLine("Votos válidos informados", count(observed))
            } else Text("Digite votos e o percentual de urnas de pelo menos uma região para calcular.", color = Secundario)
            if (valid.size < 28) Text(
                "Faltam ${28-valid.size} regiões na projeção. A soma acima é parcial; não representa necessariamente o Brasil inteiro.",
                color = Aviso, fontSize = 12.sp
            )
        }
        SurfaceCard {
            Text("Exterior (ZZ)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            val abroad = computed.getValue("ZZ")
            if (abroad.valid) {
                SummaryLine(candidateA, count(abroad.a))
                SummaryLine(candidateB, count(abroad.b))
                SummaryLine("Votos válidos projetados", count(abroad.validos))
            } else Text("Ainda sem dados manuais do exterior.", color = Secundario, fontSize = 13.sp)
            TextButton(onClick = { onChoose("ZZ") }) { Text("Preencher exterior") }
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Estados", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(onClick = onStates) { Text("Preencher UFs") }
            }
            val entered = UFS.filter { computed.getValue(it).valid }
            if (entered.isEmpty()) Text("Nenhum estado com projeção manual.", color = Secundario)
            else entered.take(6).forEach { uf -> StateSummaryRow(uf, computed.getValue(uf)) { onChoose(uf) } }
        }
        Note("O modo manual fica salvo no aparelho e não sobrescreve o histórico recebido do TSE. Ao ligar o modo automático, os valores oficiais serão mostrados novamente.")
    }
}

@Composable
private fun ManualRegions(
    modifier: Modifier, candidateA: String, candidateB: String,
    selected: String, onSelect: (String) -> Unit,
    state: StateData, projection: Estimate,
    all: Map<String, StateData>, onUpdate: (StateData) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    ContentColumn(modifier) {
        Text("Entrada manual", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Os campos são editáveis e salvos automaticamente no celular. O TSE não é consultado neste modo.", fontSize = 12.sp, color = Secundario)
        SurfaceCard {
            Text("Selecionar estado ou exterior", fontWeight = FontWeight.Medium)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(nameForRegion(selected), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("⌄")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (UFS + "ZZ").forEach { region ->
                        val hasValues = all[region]?.let { quantity(it.a) + quantity(it.b) > 0 } == true
                        DropdownMenuItem(
                            text = { Text(nameForRegion(region) + if (hasValues) " • preenchido" else "") },
                            onClick = { onSelect(region); expanded = false }
                        )
                    }
                }
            }
        }
        SurfaceCard {
            Text("Votos e apuração • ${nameForRegion(selected)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            NumberField("Urnas apuradas (%)", state.urnas, { onUpdate(state.copy(urnas = it)) }, decimal = true)
            NumberField("Votos de $candidateA", state.a, { onUpdate(state.copy(a = it)) })
            NumberField("Votos de $candidateB", state.b, { onUpdate(state.copy(b = it)) })
            HorizontalDivider(color = Borda)
            Text("Eleitorado e outros votos", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            NumberField("Eleitores totais", state.eleitores, { onUpdate(state.copy(eleitores = it)) })
            NumberField("Abstenções observadas", state.abst, { onUpdate(state.copy(abst = it)) })
            NumberField("Votos brancos", state.brancos, { onUpdate(state.copy(brancos = it)) })
            NumberField("Votos nulos", state.nulos, { onUpdate(state.copy(nulos = it)) })
            Text("Informe os votos já contabilizados, não os votos projetados. A porcentagem de urnas serve para extrapolar os resultados.", color = Secundario, fontSize = 12.sp)
        }
        SurfaceCard {
            Text("Projeção de ${nameForRegion(selected)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (projection.valid) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CandidateInfo(candidateA, projection.a, projection.a / projection.validos, Verde, Modifier.weight(1f))
                    CandidateInfo(candidateB, projection.b, projection.b / projection.validos, Vermelho, Modifier.weight(1f))
                }
                RatioBar(projection.a, projection.b)
                SummaryLine("Votos válidos projetados", count(projection.validos))
                SummaryLine("Votos válidos digitados", count(projection.observedA + projection.observedB))
            } else Text("Preencha os votos e o percentual de urnas para projetar.", color = Secundario)
            if (projection.warning.isNotBlank()) Note(projection.warning)
            OutlinedButton(onClick = { confirmReset = true }) { Text("Limpar dados desta região") }
        }
        SurfaceCard {
            Text("Resumo dos estados", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            (UFS + "ZZ").forEach { uf ->
                val entry = all.getValue(uf)
                Column(Modifier.clickable { onSelect(uf) }.padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(nameForRegion(uf), fontWeight = FontWeight.Medium)
                        Text(if (quantity(entry.a) + quantity(entry.b) > 0) "${entry.urnas.ifBlank { "0" }}% das urnas" else "—", fontSize = 12.sp, color = Secundario)
                    }
                    if (quantity(entry.a) + quantity(entry.b) > 0) RatioBar(quantity(entry.a), quantity(entry.b), thickness = 7)
                    HorizontalDivider(color = Borda)
                }
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Limpar ${nameForRegion(selected)}?") },
        text = { Text("Os números manuais desta região serão apagados. Os dados oficiais do TSE não serão alterados.") },
        confirmButton = { TextButton(onClick = { onUpdate(StateData(selected)); confirmReset = false }) { Text("Limpar") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun ManualStatistics(modifier: Modifier, states: Map<String, StateData>, projections: Map<String, Estimate>) {
    val entered = states.values.filter { quantity(it.a) + quantity(it.b) > 0 }
    val valid = projections.values.filter { it.valid }
    ContentColumn(modifier) {
        Text("Estatísticas manuais", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Somente dados digitados no aparelho. O exterior é contado à parte das 27 UFs.", fontSize = 12.sp, color = Secundario)
        SurfaceCard {
            Text("Dados observados", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            SummaryLine("Regiões com votos preenchidos", "${entered.size} / 28")
            SummaryLine("Eleitorado informado", count(states.values.sumOf { quantity(it.eleitores) }))
            SummaryLine("Votos de Flávio Bolsonaro", count(states.values.sumOf { quantity(it.a) }))
            SummaryLine("Votos de Lula", count(states.values.sumOf { quantity(it.b) }))
            SummaryLine("Brancos", count(states.values.sumOf { quantity(it.brancos) }))
            SummaryLine("Nulos", count(states.values.sumOf { quantity(it.nulos) }))
            SummaryLine("Abstenções", count(states.values.sumOf { quantity(it.abst) }))
        }
        SurfaceCard {
            Text("Projeções matemáticas", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            SummaryLine("Regiões projetadas", "${valid.size} / 28")
            SummaryLine("Votos válidos projetados", count(valid.sumOf { it.validos }))
            SummaryLine("Brancos projetados", count(valid.sumOf { it.brancos }))
            SummaryLine("Nulos projetados", count(valid.sumOf { it.nulos }))
            SummaryLine("Abstenções projetadas", count(valid.sumOf { it.abst }))
            val abroad = projections.getValue("ZZ")
            SummaryLine("Exterior • votos válidos projetados", if (abroad.valid) count(abroad.validos) else "—")
        }
        Note("Projeção manual: votos de cada candidato divididos pelo percentual de urnas apuradas. A parcela de votos por seção pode variar, especialmente quando cidades têm urnas de tamanhos diferentes.")
    }
}

@Composable
private fun BottomTab(
    name: String,
    index: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (active) Azul else Secundario
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.width(54.dp).height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) Color(0xFFEAF0FB) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            BottomTabSymbol(index, tint)
        }
        Text(
            text = name,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Ícones vetoriais desenhados diretamente no Compose: sem biblioteca extra.
@Composable
private fun BottomTabSymbol(index: Int, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val stroke = 2f * u
        when (index) {
            0 -> {
                // Painel: grade de quatro cartões.
                for (row in 0..1) for (column in 0..1) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset((3 + column * 10).toFloat() * u, (3 + row * 10).toFloat() * u),
                        size = Size(8f * u, 8f * u),
                        cornerRadius = CornerRadius(2f * u),
                        style = Stroke(width = stroke)
                    )
                }
            }
            1 -> {
                // Estados: lista organizada.
                for (row in 0..2) {
                    val y = (5 + row * 7).toFloat() * u
                    drawCircle(tint, radius = 1.7f * u, center = Offset(4f * u, y))
                    drawLine(tint, Offset(9f * u, y), Offset(21f * u, y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
            2 -> {
                // Mapa: três faixas dobradas.
                val folds = Path().apply {
                    moveTo(2f * u, 5f * u); lineTo(9f * u, 2f * u)
                    lineTo(16f * u, 5f * u); lineTo(22f * u, 2f * u)
                    lineTo(22f * u, 19f * u); lineTo(15f * u, 22f * u)
                    lineTo(8f * u, 19f * u); lineTo(2f * u, 22f * u); close()
                }
                drawPath(folds, tint, style = Stroke(width = stroke))
                drawLine(tint, Offset(9f * u, 2f * u), Offset(8f * u, 19f * u), strokeWidth = stroke)
                drawLine(tint, Offset(16f * u, 5f * u), Offset(15f * u, 22f * u), strokeWidth = stroke)
            }
            3 -> {
                // Gráfico: linha de evolução temporal.
                val line = Path().apply {
                    moveTo(2f * u, 20f * u)
                    lineTo(7f * u, 13f * u)
                    lineTo(12f * u, 16f * u)
                    lineTo(17f * u, 6f * u)
                    lineTo(22f * u, 9f * u)
                }
                drawPath(line, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawLine(tint, Offset(2f * u, 3f * u), Offset(2f * u, 22f * u), strokeWidth = stroke)
                drawLine(tint, Offset(2f * u, 22f * u), Offset(22f * u, 22f * u), strokeWidth = stroke)
            }
            else -> {
                // Estatísticas: barras de alturas diferentes.
                val heights = listOf(8f, 14f, 19f)
                heights.forEachIndexed { i, h ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset((3 + i * 7).toFloat() * u, (22f - h) * u),
                        size = Size(4f * u, h * u),
                        cornerRadius = CornerRadius(1.3f * u)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Borda),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Note(message: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF8E9)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("ⓘ", fontSize = 18.sp, color = Aviso)
        Text(message, fontSize = 12.sp, color = Color(0xFF725016), lineHeight = 17.sp)
    }
}

@Composable
private fun CandidateInfo(name: String, votes: Double, share: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Text(name, maxLines = 2, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)
        }
        Text(percentage(share * 100), color = Tinta, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text("${count(votes)} votos", fontSize = 12.sp, color = Secundario)
    }
}

@Composable
private fun RatioBar(a: Double, b: Double, modifier: Modifier = Modifier, thickness: Int = 12) {
    val sum = a + b
    val proportion = if (sum > 0) (a / sum).toFloat().coerceIn(0f, 1f) else 0f
    Row(
        modifier.fillMaxWidth().height(thickness.dp).clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFE7EBF0)),
        horizontalArrangement = Arrangement.spacedBy(if (sum > 0 && a > 0 && b > 0) 2.dp else 0.dp)
    ) {
        if (sum > 0) {
            if (a > 0) Box(Modifier.weight(max(0.0001f, proportion)).fillMaxHeight().background(Verde))
            if (b > 0) Box(Modifier.weight(max(0.0001f, 1f - proportion)).fillMaxHeight().background(Vermelho))
        }
    }
}


// Sempre usa a abrangência nacional BR do EA20 para os totais oficiais.
// Esse agregado já inclui as UFs e o exterior: nunca somar ZZ outra vez.
// O eleitorado total existe antes da apuração; demais métricas podem começar zeradas.
@Composable
private fun NationalTotalsCard(national: TseReport?) {
    SurfaceCard {
        Text("Brasil • totais oficiais do TSE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (national == null) {
            Text(
                "Aguardando arquivo nacional oficial (BR). Os valores ainda não estão disponíveis.",
                color = Secundario, fontSize = 13.sp
            )
        } else {
            SummaryLine("Eleitorado nacional", count(national.electorate.toDouble()))
            SummaryLine("Seções eleitorais", count(national.sectionsTotal.toDouble()))
            SummaryLine(
                "Seções totalizadas",
                "${count(national.sectionsDone.toDouble())} / ${count(national.sectionsTotal.toDouble())}"
            )
            SummaryLine("Progresso da apuração", percentage(national.sectionsPercent))
            if (national.sectionsDone > 0L) {
                HorizontalDivider(color = Borda)
                SummaryLine("Eleitores totalizados", count(national.electorateCounted.toDouble()))
                if (national.electorate > 0L) {
                    SummaryLine(
                        "Eleitorado ainda não totalizado",
                        count((national.electorate - national.electorateCounted).coerceAtLeast(0L).toDouble())
                    )
                }
                SummaryLine("Comparecimento contabilizado", count(national.turnout.toDouble()))
                SummaryLine("Abstenções contabilizadas", count(national.abstentions.toDouble()))
                if (national.electorateCounted > 0L) {
                    SummaryLine(
                        "Comparecimento nas seções totalizadas",
                        percentage(100.0 * national.turnout / national.electorateCounted)
                    )
                    SummaryLine(
                        "Abstenção nas seções totalizadas",
                        percentage(100.0 * national.abstentions / national.electorateCounted)
                    )
                }
                HorizontalDivider(color = Borda)
                SummaryLine("Total de votos contabilizados", count(national.totalBallots.toDouble()))
                SummaryLine("Votos válidos (TSE)", count(national.officialValidVotes.toDouble()))
                SummaryLine("Votos em branco", count(national.blank.toDouble()))
                SummaryLine("Votos nulos", count(national.nullVotes.toDouble()))
                if (national.technicalNullVotes > 0L) {
                    SummaryLine("Votos nulos técnicos", count(national.technicalNullVotes.toDouble()))
                }
            } else {
                Text(
                    "Aguardando início da totalização: comparecimento, abstenções e votos " +
                        "ainda não foram contabilizados.",
                    color = Secundario, fontSize = 12.sp
                )
            }
            Text(
                "Fonte: TSE, arquivo nacional BR (inclui o exterior). " +
                    "Comparecimento e abstenções são parciais enquanto a apuração não termina.",
                color = Secundario, fontSize = 11.sp
            )
            if (national.generated.isNotBlank()) {
                Text("Dados TSE: ${national.generated}", color = Secundario, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun OfficialDashboard(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    national: TseReport?, exterior: TseReport?,
    projectedA: Double, projectedB: Double,
    stateEstimates: Map<String, Estimate>, status: String,
    failed: Set<String>, onChoose: (String) -> Unit, onStates: () -> Unit
) {
    val validOfficial = national?.takeIf { it.hasVotes }
    val validProjection = stateEstimates.values.count { it.valid }
    val allProjected = projectedA + projectedB
    val projectedLabel = if (validProjection == 28) "Brasil + exterior" else "$validProjection de 28 regiões"
    ContentColumn(modifier) {
        Note("DADOS OFICIAIS TSE • Os votos apurados são do TSE. As projeções são cálculos independentes, sujeitos a mudanças conforme chegam novas urnas.")
        Text(status, color = Secundario, fontSize = 12.sp)
        SurfaceCard {
            Text("Resultado oficial apurado", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (validOfficial == null) {
                Text("Aguardando divulgação de votos do segundo turno pelo TSE.", color = Secundario)
                Text("Dados ainda não publicados não são contabilizados como zero.", fontSize = 12.sp, color = Secundario)
            } else {
                Text("${percentage(validOfficial.sectionsPercent)} das seções totalizadas", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { (validOfficial.sectionsPercent / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                    color = Azul, trackColor = Color(0xFFE8EDF4)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CandidateInfo(candidateA, validOfficial.candidate22.toDouble(),
                        validOfficial.candidate22.toDouble() / validOfficial.validVotes, Verde, Modifier.weight(1f))
                    CandidateInfo(candidateB, validOfficial.candidate13.toDouble(),
                        validOfficial.candidate13.toDouble() / validOfficial.validVotes, Vermelho, Modifier.weight(1f))
                }
                RatioBar(validOfficial.candidate22.toDouble(), validOfficial.candidate13.toDouble(), thickness = 14)
                SummaryLine("Votos válidos contabilizados", count(validOfficial.validVotes.toDouble()))
                SummaryLine("Brancos / nulos", "${count(validOfficial.blank.toDouble())} / ${count(validOfficial.nullVotes.toDouble())}")
                Text("Arquivo TSE: ${validOfficial.generated}", fontSize = 11.sp, color = Secundario)
            }
        }
        NationalTotalsCard(national)
        SurfaceCard {
            Text("Projeção proporcional", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Abrangência: $projectedLabel", color = Secundario, fontSize = 12.sp)
            if (allProjected <= 0.0) {
                Text("Sem votos suficientes para estimar o restante.", color = Secundario)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CandidateInfo(candidateA, projectedA, projectedA / allProjected, Verde, Modifier.weight(1f))
                    CandidateInfo(candidateB, projectedB, projectedB / allProjected, Vermelho, Modifier.weight(1f))
                }
                RatioBar(projectedA, projectedB, thickness = 14)
                SummaryLine("Diferença estimada", "${count(abs(projectedA-projectedB))} votos")
                if (validProjection < 28) Text(
                    "A projeção ainda não inclui ${28 - validProjection} região(ões). Não representa uma estimativa nacional completa.",
                    fontSize = 12.sp, color = Aviso
                )
            }
            Text("Método: votos do candidato na região ÷ fração de seções totalizadas na mesma região. A participação de cada urna pode variar.",
                fontSize = 12.sp, color = Secundario)
        }
        SurfaceCard {
            Text("Exterior (ZZ)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (exterior?.hasVotes == true) {
                SummaryLine("Seções totalizadas", percentage(exterior.sectionsPercent))
                SummaryLine(candidateA, count(exterior.candidate22.toDouble()))
                SummaryLine(candidateB, count(exterior.candidate13.toDouble()))
                SummaryLine("Votos válidos", count(exterior.validVotes.toDouble()))
            } else {
                Text("Aguardando votos oficiais do exterior.", fontSize = 13.sp, color = Secundario)
            }
            TextButton(onClick = { onChoose("ZZ") }) { Text("Detalhes do exterior") }
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Apuração por estado", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onStates) { Text("Ver 27 UFs") }
            }
            val available = UFS.filter { stateEstimates.getValue(it).valid }
            if (available.isEmpty()) Text("Aguardando dados estaduais do TSE.", color = Secundario)
            else available.take(6).forEach { uf ->
                StateSummaryRow(uf, stateEstimates.getValue(uf)) { onChoose(uf) }
            }
        }
        if (failed.isNotEmpty()) Note("Algumas consultas não foram concluídas. Últimos dados válidos permanecem salvos; consulte o horário do arquivo TSE para avaliar a atualidade.")
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Borda), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 12.sp, color = Secundario)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StateSummaryRow(uf: String, data: Estimate, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(uf, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${percentage(data.urnas)} das urnas", fontSize = 12.sp, color = Secundario)
        }
        RatioBar(data.a, data.b, thickness = 8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(percentage(if (data.validos > 0) 100 * data.a / data.validos else 0.0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
            Text(percentage(if (data.validos > 0) 100 * data.b / data.validos else 0.0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tinta)
        }
        HorizontalDivider(color = Borda)
    }
}

@Composable
private fun NumberField(title: String, value: String, onChange: (String) -> Unit, decimal: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, color = Secundario, fontWeight = FontWeight.Medium, maxLines = 2)
        OutlinedTextField(
            value = value,
            onValueChange = { entered ->
                if (entered.all { it.isDigit() || it == ',' || it == '.' }) onChange(entered)
            },
            singleLine = true,
            placeholder = { Text("0", fontSize = 14.sp) },
            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold, color = Tinta, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun OfficialRegions(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    selected: String, onSelect: (String) -> Unit,
    report: TseReport?, estimate: Estimate,
    all: Map<String, TseReport>, failures: Set<String>, status: String
) {
    var expanded by remember { mutableStateOf(false) }
    ContentColumn(modifier) {
        Text("Apuração por região", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Dados importados automaticamente do TSE • 27 UFs e exterior", color = Secundario, fontSize = 12.sp)
        Text(status, color = Secundario, fontSize = 12.sp)
        SurfaceCard {
            Text("Escolher região", fontWeight = FontWeight.Medium)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(nameForRegion(selected), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("⌄")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (UFS + "ZZ").forEach { region ->
                        DropdownMenuItem(text = { Text(nameForRegion(region) +
                            if (all[region]?.hasVotes == true) " • atualizado" else "") },
                            onClick = { onSelect(region); expanded = false })
                    }
                }
            }
            if (selected == "ZZ") Text("Total do exterior: abrangência ZZ do TSE. Não há mapa por país.", fontSize = 12.sp, color = Secundario)
        }
        SurfaceCard {
            Text(nameForRegion(selected), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (report == null) {
                Text("Arquivo da região ainda não disponível. A consulta será repetida automaticamente.", color = Secundario)
            } else {
                Text("${percentage(report.sectionsPercent)} das seções totalizadas", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { (report.sectionsPercent / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                    color = Azul, trackColor = Color(0xFFE8EDF4)
                )
                if (report.hasVotes) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CandidateInfo(candidateA, report.candidate22.toDouble(), report.candidate22.toDouble() / report.validVotes, Verde, Modifier.weight(1f))
                        CandidateInfo(candidateB, report.candidate13.toDouble(), report.candidate13.toDouble() / report.validVotes, Vermelho, Modifier.weight(1f))
                    }
                    RatioBar(report.candidate22.toDouble(), report.candidate13.toDouble(), thickness = 13)
                } else Text("Sem votos divulgados até o momento.", color = Secundario)
                SummaryLine("Seções contabilizadas", "${count(report.sectionsDone.toDouble())} / ${count(report.sectionsTotal.toDouble())}")
                SummaryLine("Eleitorado", count(report.electorate.toDouble()))
                SummaryLine("Comparecimento observado", count(report.turnout.toDouble()))
                SummaryLine("Abstenções observadas", count(report.abstentions.toDouble()))
                SummaryLine("Brancos e nulos", "${count(report.blank.toDouble())} / ${count(report.nullVotes.toDouble())}")
                Text("Dados TSE gerados em ${report.generated}", fontSize = 11.sp, color = Secundario)
            }
            if (selected in failures) Note("Não foi possível atualizar esta região agora. Se houver números acima, eles são do último arquivo salvo.")
        }
        SurfaceCard {
            Text("Projeção de ${nameForRegion(selected)}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (estimate.valid) {
                SummaryLine(candidateA, count(estimate.a))
                SummaryLine(candidateB, count(estimate.b))
                RatioBar(estimate.a, estimate.b)
                SummaryLine("Votos válidos projetados", count(estimate.validos))
                if (estimate.warning.isNotBlank()) Note(estimate.warning)
            } else Text("Projeção indisponível até a divulgação dos primeiros votos.", color = Secundario)
            Text("Projeção calculada, não divulgada pelo TSE.", fontSize = 12.sp, color = Secundario)
        }
        SurfaceCard {
            Text("Resumo das regiões", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            (UFS + "ZZ").forEach { region ->
                val item = all[region]
                Column(Modifier.fillMaxWidth().clickable { onSelect(region) }.padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(nameForRegion(region), fontWeight = FontWeight.Medium)
                        Text(if (item?.hasVotes == true) percentage(item.sectionsPercent) else "—", fontSize = 12.sp, color = Secundario)
                    }
                    if (item?.hasVotes == true) RatioBar(item.candidate22.toDouble(), item.candidate13.toDouble(), thickness = 7)
                }
                HorizontalDivider(color = Borda)
            }
        }
    }
}

@Composable
private fun OfficialStatistics(
    modifier: Modifier,
    national: TseReport?, exterior: TseReport?,
    sources: Map<String, TseReport>,
    projected: Map<String, Estimate>,
    status: String,
    failed: Set<String>
) {
    val regionsWithVotes = (UFS + "ZZ").count { sources[it]?.hasVotes == true }
    val projectedRows = projected.values.filter { it.valid }
    ContentColumn(modifier) {
        Text("Estatísticas", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(status, fontSize = 12.sp, color = Secundario)
        NationalTotalsCard(national)
        SurfaceCard {
            Text("Exterior • resultado oficial", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (exterior?.hasVotes == true) {
                SummaryLine("Seções totalizadas", percentage(exterior.sectionsPercent))
                SummaryLine("Eleitorado", count(exterior.electorate.toDouble()))
                SummaryLine("Flávio Bolsonaro", count(exterior.candidate22.toDouble()))
                SummaryLine("Lula", count(exterior.candidate13.toDouble()))
                SummaryLine("Votos válidos", count(exterior.validVotes.toDouble()))
                SummaryLine("Brancos", count(exterior.blank.toDouble()))
                SummaryLine("Nulos", count(exterior.nullVotes.toDouble()))
                SummaryLine("Abstenções", count(exterior.abstentions.toDouble()))
            } else Text("Ainda sem votos publicados no exterior.", color = Secundario)
            Text("Agregado oficial ZZ, sem mapa de países.", fontSize = 12.sp, color = Secundario)
        }
        SurfaceCard {
            Text("Projeções matemáticas", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            SummaryLine("Regiões com dados", "$regionsWithVotes / 28")
            SummaryLine("Votos válidos projetados", count(projectedRows.sumOf { it.validos }))
            SummaryLine("Brancos projetados", count(projectedRows.sumOf { it.brancos }))
            SummaryLine("Nulos projetados", count(projectedRows.sumOf { it.nulos }))
            SummaryLine("Abstenções projetadas", count(projectedRows.sumOf { it.abst }))
            Text("Somente regiões com votos apurados. Resultados não são oficiais e não incluem municípios sem amostra.", fontSize = 12.sp, color = Secundario)
        }
        Note("Este aplicativo é independente e não representa o TSE. Os totais oficiais são publicados pelo Tribunal; os números projetados são cálculos proporcionais do aplicativo.")
        if (failed.isNotEmpty()) Text("Sem atualização recente: ${failed.sorted().joinToString()}", color = Aviso, fontSize = 12.sp)
    }
}

@Composable
private fun GovernorCandidateRow(candidate: GovernorCandidate, total: Long, color: Color, projected: Double? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${candidate.name} (${candidate.number})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(candidate.party, fontSize = 11.sp, color = Secundario)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(percentage(if (total > 0) candidate.votes * 100.0 / total else 0.0),
                fontWeight = FontWeight.Bold, color = Tinta, fontSize = 17.sp)
            Text(count(candidate.votes.toDouble()), color = Secundario, fontSize = 12.sp)
            if (projected != null) Text("Projeção: ${count(projected)}", color = Secundario, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GovernorRatioBar(report: GovernorReport) {
    val first = report.candidates.getOrNull(0)?.votes?.toDouble() ?: 0.0
    val second = report.candidates.getOrNull(1)?.votes?.toDouble() ?: 0.0
    val total = first + second
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp))
        .background(Color(0xFFE7EBF0)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total > 0.0) {
            if (first > 0.0) Box(Modifier.weight((first / total).toFloat().coerceAtLeast(.0001f))
                .fillMaxHeight().background(GOVERNOR_BLUE))
            if (second > 0.0) Box(Modifier.weight((second / total).toFloat().coerceAtLeast(.0001f))
                .fillMaxHeight().background(GOVERNOR_PURPLE))
        }
    }
}

@Composable
private fun GovernorDetailCards(report: GovernorReport?) {
    SurfaceCard {
        Text("Resultado oficial apurado", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (report == null) {
            Text("O arquivo oficial desta disputa ainda não está disponível. O app tentará novamente.",
                color = Secundario, fontSize = 13.sp)
        } else {
            Text("${percentage(report.sectionsPercent)} das seções totalizadas", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { (report.sectionsPercent / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                color = Azul, trackColor = Color(0xFFE8EDF4)
            )
            if (report.hasVotes) {
                report.candidates.forEachIndexed { index, candidate ->
                    GovernorCandidateRow(candidate, report.validVotes,
                        if (index == 0) GOVERNOR_BLUE else GOVERNOR_PURPLE)
                }
                GovernorRatioBar(report)
            } else {
                Text("Candidaturas cadastradas; aguardando votos do TSE.", color = Secundario, fontSize = 13.sp)
                report.candidates.forEach { c -> Text("${c.name} (${c.number}) — ${c.party}", fontSize = 13.sp) }
            }
            SummaryLine("Votos válidos apurados", count(report.validVotes.toDouble()))
            Text("Arquivo oficial gerado em ${report.generated}", fontSize = 11.sp, color = Secundario)
        }
    }
}

@Composable
private fun GovernorProjectionsCard(report: GovernorReport?) {
    SurfaceCard {
        Text("Projeção proporcional • Governador", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (report?.hasVotes == true) {
            report.candidates.forEachIndexed { index, c ->
                GovernorCandidateRow(c, report.validVotes,
                    if (index == 0) GOVERNOR_BLUE else GOVERNOR_PURPLE,
                    projected = report.projected[index])
            }
            SummaryLine("Votos válidos projetados", count(report.projected.sum()))
            if (report.sectionsPercent < 100.0) Text(
                "Estimativa: votos já recebidos ÷ percentual de seções apuradas nesta UF.",
                color = Secundario, fontSize = 12.sp)
        } else {
            Text("Aguardando votos para calcular a projeção.", color = Secundario)
        }
        Text("Projeção independente do aplicativo, não é resultado oficial do TSE.",
            fontSize = 12.sp, color = Secundario)
    }
}

@Composable
private fun GovernorTotalsCard(report: GovernorReport?) {
    SurfaceCard {
        Text("Estatísticas estaduais • TSE", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (report == null) {
            Text("Totais oficiais ainda indisponíveis para esta UF.", color = Secundario)
        } else {
            SummaryLine("Eleitorado", count(report.electorate.toDouble()))
            SummaryLine("Seções apuradas", "${count(report.sectionsDone.toDouble())} / ${count(report.sectionsTotal.toDouble())}")
            if (report.sectionsDone > 0L) {
                SummaryLine("Comparecimento contabilizado", count(report.turnout.toDouble()))
                SummaryLine("Abstenções contabilizadas", count(report.abstentions.toDouble()))
                SummaryLine("Votos válidos (TSE)", count(report.officialValidVotes.toDouble()))
                SummaryLine("Brancos", count(report.blank.toDouble()))
                SummaryLine("Nulos", count(report.nullVotes.toDouble()))
            } else Text("Aguardando o início da totalização.", color = Secundario, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GovernorDashboard(
    modifier: Modifier, selected: String, report: GovernorReport?,
    all: Map<String, GovernorReport>, status: String,
    onChoose: (String) -> Unit, onDetails: () -> Unit
) {
    ContentColumn(modifier) {
        Note("GOVERNADOR • 2º TURNO • Dados oficiais estaduais do TSE. Esta disputa não entra na totalização presidencial.")
        Text(status, color = Secundario, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Governador — $selected", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDetails) { Text("Detalhes") }
        }
        GovernorDetailCards(report)
        GovernorProjectionsCard(report)
        GovernorTotalsCard(report)
        SurfaceCard {
            Text("Outros estados com 2º turno", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            GOVERNOR_UFS.forEach { uf ->
                Row(Modifier.fillMaxWidth().clickable { onChoose(uf) }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Governador • $uf", fontWeight = FontWeight.SemiBold)
                        val state = all[uf]
                        Text(if (state?.hasVotes == true) "${percentage(state.sectionsPercent)} apurado"
                            else if (state != null) "Aguardando votos" else "Aguardando arquivo do TSE",
                            color = Secundario, fontSize = 12.sp)
                    }
                    Text(if (uf == selected) "Selecionado" else "Ver →", color = Azul, fontSize = 12.sp)
                }
                HorizontalDivider(color = Borda)
            }
        }
    }
}

@Composable
private fun GovernorRegions(
    modifier: Modifier, selected: String, all: Map<String, GovernorReport>,
    status: String, failed: Set<String>, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val report = all[selected]
    ContentColumn(modifier) {
        Text("Governadores • 2º turno", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(status, fontSize = 12.sp, color = Secundario)
        SurfaceCard {
            Text("Estado ou Distrito Federal", fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Governador — $selected", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("⌄")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    GOVERNOR_UFS.forEach { uf ->
                        DropdownMenuItem(text = { Text(uf) }, onClick = { onSelect(uf); expanded = false })
                    }
                }
            }
        }
        GovernorDetailCards(report)
        GovernorProjectionsCard(report)
        GovernorTotalsCard(report)
        if (selected in failed) Note("Falha temporária ao consultar $selected. O último arquivo válido continua salvo no celular.")
    }
}

@Composable
private fun GovernorMap(
    modifier: Modifier, selected: String,
    all: Map<String, GovernorReport>,
    onSelect: (String) -> Unit, onDetails: () -> Unit
) {
    val geometry = remember { readBrazilMap() }
    val colors = remember(all) {
        UFS.associateWith { uf ->
            val report = all[uf]
            if (report?.hasVotes == true) Estimate(
                a = report.candidates[0].votes.toDouble(),
                b = report.candidates[1].votes.toDouble(),
                urnas = report.sectionsPercent, valid = true
            ) else Estimate()
        }
    }
    val active = GOVERNOR_UFS.count { all[it]?.hasVotes == true }
    ContentColumn(modifier) {
        Text("Mapa • Governadores", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Somente estados com segundo turno de governador recebem cores. Toque em um deles.",
            color = Secundario, fontSize = 12.sp)
        SurfaceCard {
            Text("$active / 6 estados com votos", fontSize = 13.sp, color = Secundario)
            BrazilStateMap(geometry, colors, selected,
                onSelected = { uf -> if (uf in GOVERNOR_UFS) onSelect(uf) },
                colorA = GOVERNOR_BLUE, colorB = GOVERNOR_PURPLE)
            Text("Azul e roxo representam o primeiro e o segundo candidato por número eleitoral em cada estado.",
                fontSize = 12.sp, color = Secundario)
            Text("Cinza = sem disputa estadual ou sem votos disponíveis.", fontSize = 12.sp, color = Secundario)
        }
        SurfaceCard {
            Text("Governador • $selected", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            val report = all[selected]
            if (report != null) {
                Text("${percentage(report.sectionsPercent)} das seções totalizadas", fontSize = 13.sp)
                report.candidates.forEachIndexed { index, c ->
                    GovernorCandidateRow(c, report.validVotes,
                        if (index == 0) GOVERNOR_BLUE else GOVERNOR_PURPLE)
                }
                if (report.hasVotes) GovernorRatioBar(report)
            } else Text("Aguardando dados oficiais desta UF.", color = Secundario)
            Button(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Ver detalhes de $selected") }
        }
    }
}

@Composable
private fun GovernorStatistics(modifier: Modifier, selected: String, report: GovernorReport?, status: String) {
    ContentColumn(modifier) {
        Text("Estatísticas • Governador", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("$selected • $status", fontSize = 12.sp, color = Secundario)
        GovernorTotalsCard(report)
        GovernorProjectionsCard(report)
        Note("Cada disputa de governador usa os números e os candidatos do próprio estado. Não somamos votos de governadores entre estados nem aos votos de Presidente.")
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Secundario)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tinta)
    }
}


// Mapa vetorial simplificado por UF. Fonte: Brazilian States TopoJSON, ppKrauss/ruliana,
// licença MIT: https://gist.github.com/ppKrauss/0c33364240e841fa23e78b21005f792c
// Os polígonos são embarcados no app (não há necessidade de internet ou API).
private const val BR_MAP_GZIP_BASE64 = "H4sIAAAAAAAC/21Zy64dtxH8F605AN+P7GxZMRJEsSB5d6GFEQiBF0EAJ7sg/5569Bxd+cSAcOdwOGSzu7q6mv7Pm79/+ec/vvz7t1+//OvNH15e3nz39k16yamk+vlzws+/4GdLPY00PfAeAyvtdFLJ6SqpFI9/wHipqTT9/P47/LxmKj2VkQoeVio7lZNqThWr11Q98+07zKw91ZHqTHWlulM9evXDH7l51vO7T3wuqVVsulLzxz/+xNGeLizZRmozNbzbqXmB97Si59RL6jV1f/P+R5oGY66Gg/XUR+oTPziAv7CvL0/kllc76eor9Z36SSOn4fO+/xkvB43BUEsDNmCjCyNjaMIHeQA7DyzK1/h6pYFdCqbbvg/fY87MaRbsO9O0zz/QI7Ola2G0p8nj4fF++ye+HRjDUvqLmXG0Dx/5DgutNHeasHzYex//zKjBPhz2gguXD/HxrxzGIbhbtd0ff5J7YBGOtTjuYS590fydVvcQ/bNGWkbGp7cK+UkLxq6Z1vbwO32YFqwp3uITsbJhDey8Ov0LF4VJP2t7wONCPK/Bf5yEHT+nN7/89jeh9AVhmxvIwDJ703edj4dLGS8vZQMss7SJH4OnKDG88WmBIQzOYdwBYBqI6XXjqQM//HsVOsULwMcCOucB0L1qDNjmy7XTXvoA1pZDSy7EtGSvQqh6pA8PyFgcO2s94EOWnYNDz7a038BJvFLJzLKiHeXC7OUanYMnxMkvqzDspQYOhn8ah5VVK1V83WxB9V/ko22El/UAqPWjuUhQL4ojhUcPfAeIlhUG9rC7YOFZJ5csgE2pPFflmqVxLg3gokoW/uYpOFAQ/qJXhLHWBRCqTpUB+uIN6sotHe9bAOvraJXKbBsyhulTCg9Z4WZksM+L1T01I0LasCpfu8aQmDXcosAchOxwpJ6eRAAvbSNKe8lbixbqy87dtN7IlWmo3UalxTgSj8NtmvIG7ANGmmutO2oR4UWw+oCtIIlmO4YHAOagIEFqjkA1uw5PZIp2jM/sgGo3vW4Y8iokB5zRJjySBS45K84gS0mBpWSFDrSrhRt4rCw9IeDKiYKhHfBFOlYjDplweJaKzQLakVg80MU5XIkmdlQAAeRwDt/g4VKeMfRTux1BhtMG/N6OwTzzRrnZQhUolh4cikPzCbWdMlXnIWEau4RrqbYnD2cbSkXZDm7fi7DaIghbR6ZeNzouvmDWygGMrnw9yfO0v2rFhtCMXeWjRUDKzYLIckqR9cwJUzn9+1QSbEQ8+IgD5PN9AsFObgR663gkSgEdLGxXszz5uKwWXIf7mLYKa9o83rJh3TFXv+mMYBFwczAU3e99G4FlhBNu3rrQ4f5Gx3OSsfY6j1TDloOkHOWue2EYgbWF8G/JTos9EZKe5ZmqoqowQVZwBBWqKIigmWOKrUHge+6JhH18wB9GI89dhwrhyyowrC9Fo6XwOgZZx7vzlY9GwsqbNdwptCkVlBCIcF33coDrzNX5T5h2AR+1xPAAp0WUOzmNT2QRQX6z9mtF2HTaV+yU4a90rIbiMXszsgnn6sIDTKjMRZjrIHsLbyffyKHs6fbW1fcNBmU8i6jFARAAml3dlL+pAwILrAvT3j0ZsV3Z9WFwAzlXZd+xDbh6MmI48Z9DsZxiqI6IsiZXcnSw+B6NukSETYIx+BBYHQZUACmmjyBHRsnhFAB4nTxvSlM8xW3jeUYjlz2IpDqITMri2kQsi6uLUljcsjPZv8kltd8nQMk7SuNt2uUporTuDne1EvkJ/nf8k/MK3imBbwgB5JUcCWVbVFYL5+sE0LrN/kQZEuKL0UJJZO8hmKPdKWinqeAD8f8ntxjnKIE0OhQKHl14JP2c23xtrtwiE41SZ7Wvrx2ycRZ0f1tV9lGRLOXiuiXLNwlVsxQ1LaT396MM9fHka8T/9jl4LFRM+JxB+P18MmtUFsrFUsstjQLupwcLnh1BBq6rORzx5/JRTgZKCfoEww+2pxA0KM3FChtysZGbXGWBZqkCrFGzpAlSsfaH2tmuG5YOKPoWTnwYkVOziwokdCrl4xBkEJIqFqvIHMwJjwIauynXmmlAMadLtRXT7DK5I8fXUEmatwBg3Q38VRi3zgjlx9Bo0aJGot+JellAIeozSH6LLxS7/tAVtT5qQ5GMFPk3mjL2E0NiZaT1tIOB5nGvbFUBdVSs19UtWqIGJItKT76jZjVN9h93+a45W5jOtMtTql2EqYUKS70CS0QvV2hqJ88imkfkW3ViqDUL5lkdeKjHEr5n1WEDFvV+GXT78EQBKjgesmQIq8VtzjdFqHMf01cm9/YI+OsvEQZq2vWq+8jjRiSAtUPQU4M4GEcI0SMV111MRkZAaz/5rtvNIaArQhAhuN1xHqCgoj6GvKaD4ehDfAU3jai7nYoeBXvFuAtDYf0V60jVa/HKShZQjcZF+lxIY8clUc5C5pVbO2AUSFItQ2O3RG1zZSDhalnQ33mS1FS86N2UmFOhDgKeGdVulsBSE+cf0yigcZ7OhPyCuRKiTEyT87p1dwupK87xoVjWrSSi4EtLm05XSbs+JT8batfn0tm+OZ8k4CQL2DRf1rkq97mGJh2hBtmTR+193ZpMhcuj8D1wqXrBYiTE1M2aYiwg4xQuMkJ90td0jtgTYdr3tCJnDF4S3H2XygvafKtBdkHFtx7oYnmdcQQ9nWzVGAdm5jLAAO1oYfLd+KkHtfPK1xwmVdgfohr5KPpeFCNdC/Bld1dzbtV4IY9iLd5FlOhyKZjmk8wR5dkpvKYyTVPtn0fLrRpU6AvlJvLDvNgkuJ7Px01br8/CDuKyhBZOM9Dt5Cvmcv7cTwRe2Aj0/UQFe5S7GApRvrhoWd1HVNHJG7USEl5q6NzI3Ox4j0spGgs5mj5S/tG/t5a8i6tuZDxfOuHRLOxhSY32MgirHKZMuX1YAuyssrIci+cH1fLmz/UHeeqWfeqOTGlp1ceLpcvRZT+7ntUYL+8EjSpWzI+Ge96CN0QJ7y1VrlVs2t2yDAtuXtU51elS7wjytljmsS51zrwgc3GUU/uTxJbqmHpCoROd8gaku7/kzr5w+4b/YNd9A1NqXMrAfWbyzi7jOFsYYyO0AlTV9ympzyCiHDLg9d0J+vXxSkNFE4QUf0RmRvup1XQv10JoXBYk0t1ULD3d3aFCP3mtG5cpLrVAnYAuBpUob6yWI8jUmEcidfEXenVdwdzRovIYDzxWyftxC3mBfodozO7OlaU5rhWiYo2i5HKDr8vSppiTZB0FUbhL/OPqgTVkRu5wQgmlWgj+FvcxUmau01QSO7od8WDc+qk6xSUfOXNbOwLqEQTYfNdiXU9Gi1zvnouQFEFlGRSXggLU5VkenGrx42bmxj0+FmABBLeemdwo743A/Lhvfnj4GnR87sXGoUAI5dbvtG9URepHu+7Di5QNwOYb8284hck145qVdrl7ZRBXNLbR4uo2weeqQ3VvPMcPe7T2fLkpdo9WJ9/6IhpSytvq/zvxLcnodiru8m5BpJv/Hl4O2mqPxSUArWOb7vyIkM///R/DdvFrPRkAAA=="

// === Aba Gráfico: somente a evolução da porcentagem FINAL projetada ===
// O histórico existente é preservado: as porcentagens são calculadas a partir
// dos votos finais projetados em cada atualização (e NÃO dos votos já apurados).
private data class ProjectionPercentPoint(
    val moment: Long,
    val a: Double,
    val b: Double
)

private fun projectionPercentHistory(points: List<EvolutionPoint>): List<ProjectionPercentPoint> =
    points.mapNotNull { p ->
        val total = p.projectedA + p.projectedB
        if (!total.isFinite() || total <= 0.0 ||
            !p.projectedA.isFinite() || !p.projectedB.isFinite() ||
            p.projectedA < 0.0 || p.projectedB < 0.0) null
        else {
            val a = (p.projectedA / total * 100.0).coerceIn(0.0, 100.0)
            ProjectionPercentPoint(p.moment, a, 100.0 - a)
        }
    }

@Composable
private fun EvolutionPage(
    modifier: Modifier,
    title: String,
    points: List<EvolutionPoint>,
    nameA: String,
    nameB: String,
    colorA: Color,
    colorB: Color,
    description: String,
    onClear: (() -> Unit)? = null
) {
    val percentHistory = remember(points) { projectionPercentHistory(points) }
    ContentColumn(modifier) {
        Text("Evolução da projeção", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tinta)
        Text(title, fontSize = 14.sp, color = Secundario)
        Note(description + " O gráfico mostra a porcentagem final estimada de cada candidato, " +
            "não a porcentagem dos votos já apurados.")
        if (percentHistory.isEmpty()) {
            SurfaceCard {
                Text("Aguardando dados de projeção", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "O gráfico aparecerá quando houver votos suficientes para calcular uma projeção. " +
                        "Não são criados pontos retroativos ou fictícios.",
                    color = Secundario
                )
            }
        } else {
            val latest = percentHistory.last()
            SurfaceCard {
                Text("Projeção mais recente", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(clockTime(latest.moment), fontSize = 12.sp, color = Secundario)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProjectionPercentNumber(nameA, latest.a, colorA, Modifier.weight(1f))
                    ProjectionPercentNumber(nameB, latest.b, colorB, Modifier.weight(1f))
                }
                Text("${percentHistory.size} atualização(ões) no histórico", fontSize = 12.sp, color = Secundario)
            }
            ProjectionPercentChart(percentHistory, nameA, nameB, colorA, colorB)
            if (percentHistory.size == 1) {
                Note("O primeiro registro já está no gráfico. A linha aparecerá depois que houver " +
                    "uma nova atualização dos resultados.")
            }
            if (onClear != null) {
                var confirming by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { confirming = true }) { Text("Limpar histórico manual") }
                if (confirming) AlertDialog(
                    onDismissRequest = { confirming = false },
                    title = { Text("Apagar histórico do gráfico?") },
                    text = { Text("Os dados preenchidos serão preservados. " +
                        "Somente os registros de evolução do modo manual serão apagados.") },
                    confirmButton = { TextButton(onClick = { onClear(); confirming = false }) { Text("Apagar") } },
                    dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancelar") } }
                )
            }
        }
        Text(
            "Cada ponto representa uma projeção calculada a partir dos dados registrados naquele momento. " +
                "Consultas sem alterações não acrescentam pontos. Histórico salvo no próprio aparelho.",
            fontSize = 12.sp, color = Secundario
        )
    }
}

@Composable
private fun ProjectionPercentNumber(name: String, value: Double, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, color = Tinta, overflow = TextOverflow.Ellipsis)
        }
        // Percentuais sempre pretos; verde/vermelho identificam as séries.
        Text(percentage(value), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
private fun ProjectionPercentChart(
    points: List<ProjectionPercentPoint>,
    nameA: String,
    nameB: String,
    colorA: Color,
    colorB: Color
) {
    SurfaceCard {
        Text("Porcentagem projetada ao longo do tempo", fontSize = 17.sp,
            fontWeight = FontWeight.Bold, color = Tinta)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProjectionLegend(nameA, colorA, Modifier.weight(1f))
            ProjectionLegend(nameB, colorB, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(240.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(
                modifier = Modifier.width(39.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf("100%", "75%", "50%", "25%", "0%").forEach { label ->
                    Text(label, fontSize = 10.sp, color = Secundario)
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val insetTop = 7.dp.toPx()
                val insetBottom = 7.dp.toPx()
                val graphHeight = (size.height - insetTop - insetBottom).coerceAtLeast(1f)
                val graphWidth = size.width.coerceAtLeast(1f)
                val startTime = points.first().moment
                val elapsed = (points.last().moment - startTime).coerceAtLeast(1L)
                fun x(index: Int): Float = if (points.size == 1) graphWidth / 2f else
                    ((points[index].moment - startTime).toFloat() / elapsed)
                        .coerceIn(0f, 1f) * graphWidth
                fun y(percent: Double): Float = insetTop + graphHeight *
                    (1.0 - percent.coerceIn(0.0, 100.0) / 100.0).toFloat()

                // Eixo fixo 0–100%: não exagera visualmente variações pequenas.
                listOf(0.0, 25.0, 50.0, 75.0, 100.0).forEach { tick ->
                    val py = y(tick)
                    drawLine(Borda, Offset(0f, py), Offset(graphWidth, py),
                        strokeWidth = 1.dp.toPx())
                }
                fun plot(tint: Color, getValue: (ProjectionPercentPoint) -> Double) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val px = x(index)
                        val py = y(getValue(point))
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    if (points.size > 1) {
                        drawPath(path, tint, style = Stroke(
                            width = 2.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val lastIndex = points.lastIndex
                    val marker = Offset(x(lastIndex), y(getValue(points[lastIndex])))
                    drawCircle(Color.White, radius = 5.4.dp.toPx(), center = marker)
                    drawCircle(tint, radius = 3.4.dp.toPx(), center = marker)
                }
                plot(colorA) { it.a }
                plot(colorB) { it.b }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Início: ${smallTime(points.first().moment)}", fontSize = 11.sp, color = Secundario)
            Text("Última: ${smallTime(points.last().moment)}", fontSize = 11.sp, color = Secundario)
        }
        Text("A escala vai de 0% a 100%. As duas linhas somam 100% dos votos válidos projetados.",
            fontSize = 11.sp, color = Secundario)
    }
}

@Composable
private fun ProjectionLegend(name: String, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(5.dp)).background(tint))
        Text(name, color = Tinta, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

private fun smallTime(epoch: Long): String = SimpleDateFormat("HH:mm", PtBr).format(Date(epoch))

private data class UfOutline(val uf: String, val points: List<Offset>)

private fun readBrazilMap(): List<UfOutline> {
    val compressed = Base64.decode(BR_MAP_GZIP_BASE64, Base64.DEFAULT)
    val jsonText = GZIPInputStream(ByteArrayInputStream(compressed))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    val raw = JSONObject(jsonText)
    val arcsJson = raw.getJSONArray("arcs")
    val arcs = (0 until arcsJson.length()).map { index ->
        val encoded = arcsJson.getJSONArray(index)
        var x = 0
        var y = 0
        (0 until encoded.length()).map { p ->
            val delta = encoded.getJSONArray(p)
            x += delta.getInt(0)
            y += delta.getInt(1)
            Offset(x.toFloat(), y.toFloat())
        }
    }
    val shapes = raw.getJSONArray("geometries")
    return (0 until shapes.length()).map { index ->
        val entry = shapes.getJSONArray(index)
        val uf = entry.getString(0)
        val refs = entry.getJSONArray(1)
        val points = mutableListOf<Offset>()
        for (r in 0 until refs.length()) {
            val id = refs.getInt(r)
            val arc = if (id >= 0) arcs[id] else arcs[-id - 1].asReversed()
            // Consecutive arcs share their endpoints: avoid duplicate vertices.
            points.addAll(if (points.isEmpty()) arc else arc.drop(1))
        }
        UfOutline(uf, points)
    }
}

// Ray-casting hit test: detects the state touched on the real polygon.
private fun insidePolygon(point: Offset, polygon: List<Offset>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > point.y) != (b.y > point.y)) {
            val intersection = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            if (point.x < intersection) inside = !inside
        }
        j = i
    }
    return inside
}

private fun mapColor(result: Estimate, first: Color = Verde, second: Color = Vermelho): Color {
    if (!result.valid || result.validos <= 0) return Color(0xFFE2E7ED)
    val shareA = (result.a / result.validos).toFloat().coerceIn(0f, 1f)
    return lerp(second, first, shareA)
}

@Composable
private fun BrazilStateMap(
    geometry: List<UfOutline>,
    estimates: Map<String, Estimate>,
    selected: String,
    onSelected: (String) -> Unit,
    colorA: Color = Verde,
    colorB: Color = Vermelho
) {
    Canvas(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF9FBFD))
            .pointerInput(geometry) {
                detectTapGestures { tap ->
                    val square = min(size.width, size.height).toFloat() * 0.95f
                    val left = (size.width - square) / 2f
                    val top = (size.height - square) / 2f
                    val geo = Offset(
                        (tap.x - left) / square * 10000f,
                        10000f - (tap.y - top) / square * 10000f
                    )
                    // Reversed order lets small polygons (DF, SE etc.) win near borders.
                    geometry.asReversed().firstOrNull { insidePolygon(geo, it.points) }
                        ?.let { onSelected(it.uf) }
                }
            }
    ) {
        val square = min(size.width, size.height) * 0.95f
        val left = (size.width - square) / 2f
        val top = (size.height - square) / 2f
        geometry.forEach { state ->
            val path = Path()
            state.points.forEachIndexed { i, point ->
                val x = left + point.x / 10000f * square
                val y = top + (10000f - point.y) / 10000f * square
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = mapColor(estimates.getValue(state.uf), colorA, colorB))
            drawPath(
                path,
                color = if (selected == state.uf) Azul else Color.White,
                style = Stroke(width = if (selected == state.uf) 2.8.dp.toPx() else 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun MapPage(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    computed: Map<String, Estimate>,
    editState: (String) -> Unit,
    manualMode: Boolean
) {
    val geometry = remember { readBrazilMap() }
    var picked by remember { mutableStateOf("SC") }
    var expanded by remember { mutableStateOf(false) }
    val current = computed.getValue(picked)
    val reported = computed.values.count { it.valid }
    ContentColumn(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mapa da apuração", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (manualMode) "Toque em uma UF para consultar sua projeção manual."
                else "Toque em uma UF para consultar votos oficiais e projeção.",
                fontSize = 13.sp, color = Secundario
            )
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Brasil por estado", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("$reported/27 UFs", fontSize = 12.sp, color = Secundario)
            }
            BrazilStateMap(geometry, computed, picked, onSelected = { picked = it })
            Text(
                if (manualMode) "Cores calculadas com os votos que você digitou em cada UF."
                else "Cores calculadas a partir dos votos oficiais apurados em cada UF.",
                fontSize = 12.sp, color = Secundario
            )
            Box(
                Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(Vermelho, lerp(Vermelho, Verde, .5f), Verde)))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100% $candidateB", fontSize = 11.sp, color = Tinta)
                Text("50% / 50%", fontSize = 11.sp, color = Tinta)
                Text("100% $candidateA", fontSize = 11.sp, color = Tinta)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(11.dp).background(Color(0xFFE2E7ED), RoundedCornerShape(3.dp)))
                Text("Cinza = sem votos oficiais disponíveis", fontSize = 12.sp, color = Secundario)
            }
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Estado selecionado", fontSize = 12.sp, color = Secundario)
                    Text(picked, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Trocar UF  ⌄") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        UFS.forEach { uf ->
                            DropdownMenuItem(text = { Text(uf) }, onClick = { picked = uf; expanded = false })
                        }
                    }
                }
            }
            Text("Urnas apuradas: ${percentage(current.urnas)}", fontSize = 13.sp, color = Tinta, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { (current.urnas / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                color = Azul,
                trackColor = Color(0xFFE8EDF4)
            )
            if (current.valid) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CandidateInfo(candidateA, current.a, current.a / current.validos, Verde, Modifier.weight(1f))
                    CandidateInfo(candidateB, current.b, current.b / current.validos, Vermelho, Modifier.weight(1f))
                }
                RatioBar(current.a, current.b, thickness = 12)
                HorizontalDivider(color = Borda)
                SummaryLine("Votos válidos projetados", count(current.validos))
                SummaryLine(if (manualMode) "Votos informados" else "Votos apurados pelo TSE", count(current.observedA + current.observedB))
                if (current.warning.isNotBlank()) Note(current.warning)
            } else {
                Text(
                    if (manualMode) "Preencha os votos e o percentual de urnas na aba Estados."
                    else "Aguardando votos oficiais e seções totalizadas para projetar esta UF.",
                    color = Secundario, fontSize = 13.sp
                )
            }
            Button(onClick = { editState(picked) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (manualMode) "Editar dados de $picked" else "Ver dados oficiais de $picked")
            }
        }
        Note(
            if (manualMode) "MAPA MANUAL • As cores refletem a proporção dos votos digitados em cada estado. A projeção matemática é calculada separadamente."
            else "MAPA INDEPENDENTE • As cores refletem a proporção dos votos oficialmente apurados em cada UF, não o avanço das seções. A projeção é calculada separadamente."
        )
        Text("Geometria simplificada dos estados (TopoJSON, licença MIT), disponível no app sem internet.", color = Secundario, fontSize = 11.sp)
    }
}
