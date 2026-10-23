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
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

// Verde e vermelho permanecem associados aos candidatos nos dois temas.
private val Verde = Color(0xFF198754)
private val Vermelho = Color(0xFFD32F2F)

private data class AppPalette(
    val background: Color,
    val surface: Color,
    val sheet: Color,
    val text: Color,
    val secondary: Color,
    val border: Color,
    val accent: Color,
    val notice: Color,
    val noticeBackground: Color,
    val noticeText: Color,
    val track: Color,
    val mapBackground: Color,
    val emptyState: Color,
    val governorBlue: Color,
    val governorPurple: Color,
    val glassTint: Color,
    val glassFallback: Color,
    val glassBorder: Color
)

private val LightPalette = AppPalette(
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    sheet = Color(0xFFF6F8FC),
    text = Color(0xFF172235),
    secondary = Color(0xFF667085),
    border = Color(0xFFE4E9F0),
    accent = Color(0xFF234E8A),
    notice = Color(0xFF966100),
    noticeBackground = Color(0xFFFFF8E9),
    noticeText = Color(0xFF725016),
    track = Color(0xFFE7EBF0),
    mapBackground = Color(0xFFEDF2F8),
    emptyState = Color(0xFFD8E2ED),
    governorBlue = Color(0xFF245AA6),
    governorPurple = Color(0xFF9055A2),
    glassTint = Color.White.copy(alpha = 0.64f),
    glassFallback = Color.White.copy(alpha = 0.93f),
    glassBorder = Color.White.copy(alpha = 0.82f)
)

private val DarkPalette = AppPalette(
    background = Color(0xFF0D1420),
    surface = Color(0xFF182333),
    sheet = Color(0xFF151F2D),
    text = Color(0xFFF3F6FC),
    secondary = Color(0xFFACBBD0),
    border = Color(0xFF344256),
    accent = Color(0xFF95BEFF),
    notice = Color(0xFFF0C67B),
    noticeBackground = Color(0xFF382D1A),
    noticeText = Color(0xFFFFDCAA),
    track = Color(0xFF344154),
    mapBackground = Color(0xFF142031),
    emptyState = Color(0xFF46576D),
    governorBlue = Color(0xFF7AACFF),
    governorPurple = Color(0xFFD29BFF),
    glassTint = Color(0xFF24334A).copy(alpha = 0.74f),
    glassFallback = Color(0xFF1B293A).copy(alpha = 0.96f),
    glassBorder = Color.White.copy(alpha = 0.18f)
)

private val LocalAppPalette = androidx.compose.runtime.staticCompositionLocalOf { LightPalette }

private val Fundo: Color @Composable get() = LocalAppPalette.current.background
private val Tinta: Color @Composable get() = LocalAppPalette.current.text
private val Secundario: Color @Composable get() = LocalAppPalette.current.secondary
private val Borda: Color @Composable get() = LocalAppPalette.current.border
private val Azul: Color @Composable get() = LocalAppPalette.current.accent
private val Aviso: Color @Composable get() = LocalAppPalette.current.notice
private val GovernorBlue: Color @Composable get() = LocalAppPalette.current.governorBlue
private val GovernorPurple: Color @Composable get() = LocalAppPalette.current.governorPurple
private val PtBr = Locale.forLanguageTag("pt-BR")

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
    require(votes22 + votes13 <= ballots.longField("tv") || ballots.longField("tv") == 0L)
    TseReport(
        location = region,
        candidate22 = votes22,
        candidate13 = votes13,
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
// Tons de governador são selecionados pelo tema na camada de interface.

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

private fun nameForRegion(uf: String): String = if (uf == "ZZ") "Exterior" else uf

// Siglas ficam nos seletores compactos; a lista de escolha mostra nomes completos.
private val REGION_FULL_NAMES = mapOf(
    "AC" to "Acre", "AL" to "Alagoas", "AP" to "Amapá",
    "AM" to "Amazonas", "BA" to "Bahia", "CE" to "Ceará",
    "DF" to "Distrito Federal", "ES" to "Espírito Santo", "GO" to "Goiás",
    "MA" to "Maranhão", "MT" to "Mato Grosso", "MS" to "Mato Grosso do Sul",
    "MG" to "Minas Gerais", "PA" to "Pará", "PB" to "Paraíba",
    "PR" to "Paraná", "PE" to "Pernambuco", "PI" to "Piauí",
    "RJ" to "Rio de Janeiro", "RN" to "Rio Grande do Norte",
    "RS" to "Rio Grande do Sul", "RO" to "Rondônia", "RR" to "Roraima",
    "SC" to "Santa Catarina", "SP" to "São Paulo", "SE" to "Sergipe",
    "TO" to "Tocantins", "ZZ" to "Exterior"
)
private fun fullRegionName(uf: String): String = REGION_FULL_NAMES[uf] ?: uf

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
        // Conteúdo edge-to-edge: o app ocupa toda a janela e controla seus próprios insets.
        // Os ícones do sistema acompanham o tema claro/escuro salvo no aparelho.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ElectionApp() }
        // Contraste do sistema acompanha o tema atual.
        applySystemBarContrast()
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarContrast() {
        val dark = getSharedPreferences("apuracao_v2", Context.MODE_PRIVATE)
            .getBoolean("tema_escuro_2026", false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // LightStatusBars=true: ícones escuros; false: ícones claros.
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        // Barras transparentes no modo edge-to-edge.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBarContrast()
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
    // Tema salvo no aparelho, com a opção no menu de três pontos.
    var darkMode by remember { mutableStateOf(prefs.getBoolean("tema_escuro_2026", false)) }
    val palette = if (darkMode) DarkPalette else LightPalette
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
    }
    // A barra de ferramentas e a navegação inferior acompanham o dedo, sem consumir a rolagem.
    // O usuário puxa para cima para recolhê-las e para baixo para reaparecerem.
    val density = LocalDensity.current
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topChromeHeight = statusInset + 64.dp
    val bottomChromeHeight = navInset + 84.dp
    val scrollDistancePx = with(density) { 104.dp.toPx() }
    var chromeVisible by remember { mutableFloatStateOf(1f) }
    val chromeNestedScroll = remember(scrollDistancePx, showOptions) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Ao chegar ao início da página, o gesto para baixo também devolve as barras.
                val gestureY = if (consumed.y != 0f) consumed.y else available.y.coerceAtLeast(0f)
                if (!showOptions && source == NestedScrollSource.UserInput && abs(gestureY) > 0.5f) {
                    chromeVisible = (chromeVisible + gestureY / scrollDistancePx).coerceIn(0f, 1f)
                }
                return Offset.Zero
            }

            // Ao soltar o dedo, completa a transição com uma animação curta.
            // Durante o gesto, a posição continua vinculada diretamente à rolagem.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!showOptions && chromeVisible > 0.01f && chromeVisible < 0.99f) {
                    val destination = if (chromeVisible >= 0.52f) 1f else 0f
                    animate(
                        initialValue = chromeVisible,
                        targetValue = destination,
                        animationSpec = tween(durationMillis = 170)
                    ) { value, _ -> chromeVisible = value }
                }
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(tab, electionMode) { chromeVisible = 1f }
    // O blur segue a posição real da folha modal, inclusive quando arrastada para fechar.
    // Efeito vidro nativo: o conteúdo por trás da pílula vira fonte de blur.
    val dockHazeState = rememberHazeState()
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    // Usa a altura realmente medida da janela, sem estimar o tamanho do modal.
    // A posição permanece estável mesmo durante gestos rápidos e mudanças de layout.
    var windowHeightPx by remember { mutableFloatStateOf(0f) }
    var sheetGesturePx by remember { mutableFloatStateOf(0f) }
    var sheetExpandedToTop by remember { mutableStateOf(false) }
    var closingOptions by remember { mutableStateOf(false) }
    val sheetReveal by remember(showOptions, optionsSheetState) {
        derivedStateOf {
            if (!showOptions || sheetHeightPx <= 1f || windowHeightPx <= 1f) 0f
            else {
                val nativeY = runCatching { optionsSheetState.requireOffset() }.getOrNull()
                if (nativeY == null || !nativeY.isFinite()) 0f
                else ((windowHeightPx - nativeY) / sheetHeightPx)
                    .coerceIn(0f, 1f)
            }
        }
    }
    // A cor e o desfoque seguem a posição do pop-up, mas a interpolação evita
    // mudanças abruptas quando o modal termina de sair da composição.
    val visibleOverlay by animateFloatAsState(
        targetValue = sheetReveal,
        animationSpec = tween(durationMillis = 130),
        label = "Fundo do menu"
    )
    fun dismissOptions() {
        if (closingOptions || !showOptions) return
        closingOptions = true
        sheetScope.launch {
            try {
                // Um único controlador de fechamento; não disputar com a rolagem interna.
                if (optionsSheetState.isVisible) optionsSheetState.hide()
            } finally {
                showOptions = false
                sheetGesturePx = 0f
                sheetExpandedToTop = false
                closingOptions = false
            }
        }
    }
    fun finishSheetDrag() {
        val upThreshold = with(density) { 48.dp.toPx() }
        val downThreshold = with(density) { 68.dp.toPx() }
        when {
            sheetGesturePx < -upThreshold -> sheetExpandedToTop = true
            sheetGesturePx > downThreshold && sheetExpandedToTop -> sheetExpandedToTop = false
            sheetGesturePx > downThreshold -> dismissOptions()
        }
        // Sem translationY e sem corrotinas de retorno concorrentes.
        // Só uma animação de altura (StableExpandableSheetBody) controla a folha.
        sheetGesturePx = 0f
    }
    fun cancelSheetDrag() { sheetGesturePx = 0f }
    // O modo manual utiliza as mesmas chaves da versão original (uf_SC, uf_GO...).
    // Os arquivos oficiais ficam SOMENTE em tse_json_*, sem sobrescrever a digitação.
    var manualMode by remember { mutableStateOf(prefs.getBoolean("modo_manual", false)) }
    var notificationOn by remember { mutableStateOf(prefs.getBoolean("notificacao_projecao_ativa", false)) }
    var notificationError by remember { mutableStateOf(false) }
    // A ação "Encerrar" é disparada pelo serviço fora da tela Compose.
    // Escutar a mesma preferência sincroniza imediatamente o Switch, mesmo com
    // o menu aberto e sem exigir que o app seja colocado em segundo plano.
    DisposableEffect(prefs, activity) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == "notificacao_projecao_ativa") {
                activity.runOnUiThread {
                    notificationOn = changed.getBoolean("notificacao_projecao_ativa", false)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
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
        colorScheme = if (darkMode) darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF0E2036),
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.sheet,
            onSurfaceVariant = palette.secondary,
            outline = palette.border
        ) else lightColorScheme(
            primary = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.sheet,
            onSurfaceVariant = palette.secondary,
            outline = palette.border
        ), typography = Typography()
    ) {
        CompositionLocalProvider(LocalAppPalette provides palette) {
        if (showOptions) {
            ElectionOptionsSheet(
                sheetState = optionsSheetState,
                onSheetHeight = { sheetHeightPx = it },
                expandedToTop = sheetExpandedToTop,
                onDragBy = { delta ->
                    // Drag para cima amplia a folha; para baixo recolhe ou fecha.
                    // A rolagem da lista abaixo é independente deste detector.
                    sheetGesturePx = (sheetGesturePx + delta)
                        .coerceIn(-with(density) { 180.dp.toPx() }, with(density) { 180.dp.toPx() })
                },
                onDragFinished = { finishSheetDrag() },
                onDragCancelled = { cancelSheetDrag() },
                electionMode = electionMode,
                selectedGovernor = selectedGovernorUF,
                automatic = !manualMode,
                darkMode = darkMode,
                onDarkModeChange = { enabled ->
                    darkMode = enabled
                    prefs.edit().putBoolean("tema_escuro_2026", enabled).apply()
                },
                notificationEnabled = notificationOn,
                notificationError = notificationError,
                status = if (electionMode == "governador") {
                    when {
                        governorLoading -> "Sincronizando resultados estaduais..."
                        governorLastGood > 0L -> "Última atualização: ${clockTime(governorLastGood)}"
                        else -> "Aguardando dados oficiais do TSE"
                    }
                } else status,
                onDismiss = { dismissOptions() },
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
                    // Solicita nova consulta sem fechar o menu nem perder o foco.
                    if (electionMode == "governador") governorRetry++
                    else if (!manualMode) needsRetry++
                }
            )
        }
        // A intensidade não depende de um booleano: segue o deslocamento da folha,
        // acompanhando também o gesto de fechamento, sem saltos bruscos.
        val menuBackgroundBlur = if (Build.VERSION.SDK_INT >= 31) (visibleOverlay * 8f).dp else 0.dp
        val edgeFadeColor = Fundo // capturado fora do DrawScope
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { windowHeightPx = it.height.toFloat() }
                .nestedScroll(chromeNestedScroll)
                .blur(menuBackgroundBlur),
            containerColor = Fundo,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // Barra totalmente recolhível, inclusive a área reservada para o status bar.
                // O texto desliza junto com o dedo. A tela fica edge-to-edge.
                Box(Modifier.fillMaxWidth().height(topChromeHeight * chromeVisible).clipToBounds()) {
                    Column(Modifier.fillMaxWidth().requiredHeight(topChromeHeight).graphicsLayer {
                        translationY = -(1f - chromeVisible) * with(density) { 64.dp.toPx() }
                        alpha = chromeVisible
                    }) {
                        Spacer(Modifier.height(statusInset))
                        TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        title = {
                            Column(
                                modifier = Modifier.padding(vertical = 3.dp),
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        HorizontalDivider(color = Borda, thickness = 0.8.dp)
                    }
                }
            },
            bottomBar = {}
        ) { padding ->
            // Dissolução discreta do conteúdo nas extremidades. Conforme as barras
            // recolhem, o efeito aumenta e mantém os ícones do sistema legíveis.
            Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(dockHazeState).drawWithContent {
                drawContent()
                val opacity = (1f - chromeVisible) * 0.93f
                if (opacity > 0.01f) {
                    val edge = 42.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(edgeFadeColor.copy(alpha = opacity), edgeFadeColor.copy(alpha = 0f)),
                            startY = 0f, endY = edge
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(edgeFadeColor.copy(alpha = 0f), edgeFadeColor.copy(alpha = opacity)),
                            startY = (size.height - edge).coerceAtLeast(0f), endY = size.height
                        )
                    )
                }
            }) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState > initialState)
                        AnimatedContentTransitionScope.SlideDirection.Left
                    else AnimatedContentTransitionScope.SlideDirection.Right
                    val entering = slideIntoContainer(direction, animationSpec = tween(310)) +
                        fadeIn(animationSpec = tween(200))
                    val leaving = slideOutOfContainer(direction, animationSpec = tween(310)) +
                        fadeOut(animationSpec = tween(170))
                    entering togetherWith leaving
                },
                label = "Transição horizontal das páginas"
            ) { pageTab ->
            if (electionMode == "governador") {
                val stateReport = governorReports[selectedGovernorUF]
                val govStatus = when {
                    governorLoading && governorLastGood == 0L -> "Conectando aos resultados estaduais do TSE..."
                    governorLastGood == 0L -> "Aguardando arquivos oficiais estaduais do TSE"
                    governorFailed.isNotEmpty() -> "Última sincronização: ${clockTime(governorLastGood)} • fontes indisponíveis: ${governorFailed.sorted().joinToString()}"
                    else -> "Dados TSE: ${clockTime(governorLastGood)} • atualização a cada 60 s"
                }
                when (pageTab) {
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
                        colorA = GovernorBlue, colorB = GovernorPurple,
                        description = "Dados oficiais do TSE. As projeções usam o percentual de seções apuradas."
                    )
                    else -> GovernorStatistics(Modifier.padding(padding), selectedGovernorUF, stateReport, govStatus)
                }
            } else if (manualMode) {
                when (pageTab) {
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
                when (pageTab) {
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
            } // AnimatedContent
            } // Conteúdo com fade nas bordas
            // Dock flutuante sobre o conteúdo, não retângulo de ponta a ponta.
            // Continua retraindo junto ao gesto de rolagem da versão anterior.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(bottomChromeHeight * chromeVisible).clipToBounds()
            ) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .requiredHeight(bottomChromeHeight)
                        .graphicsLayer {
                            translationY = (1f - chromeVisible) * with(density) { 78.dp.toPx() }
                            alpha = chromeVisible
                        }
                ) {
                    GlassNavigationPill(
                        selected = tab,
                        onTabSelected = { next ->
                            if (tab != next) tab = next
                            chromeVisible = 1f
                        },
                        onSettings = {
                            chromeVisible = 1f
                            showOptions = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 15.dp, end = 15.dp, bottom = navInset + 8.dp),
                        hazeState = dockHazeState
                    )
                }
            }
            } // Área de conteúdo e dock

        }
        // Scrim próprio: o padrão do ModalBottomSheet escurecia instantaneamente
        // quando o diálogo desaparecia. A camada segue a mesma animação do blur.
        if (visibleOverlay > 0.001f) {
            Box(
                Modifier.fillMaxSize().background(
                    Color.Black.copy(alpha = visibleOverlay * if (darkMode) 0.35f else 0.12f)
                )
            )
        }
        } // CompositionLocalProvider: paleta claro/escuro de toda a interface
    }
}


// Estrutura compartilhada dos menus: a altura EXTERNA permanece fixa.
// O fundo do painel alcança o rodapé edge-to-edge; os controles internos usam
// navigationBarsPadding, sem aplicar novamente o inset no ModalBottomSheet.
// Apenas o painel interno cresce/recolhe, impedindo o reancoramento que causava
// vibração quando a folha chegava ao topo e o usuário deslizava de volta.
@Composable
private fun StableExpandableSheetBody(
    expanded: Boolean,
    compactHeight: Dp,
    panelColor: Color,
    onTapOutsidePanel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val fullHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 30.dp)
        .coerceAtLeast(300.dp)
    val compact = minOf(compactHeight, fullHeight)
    val panelHeight by animateDpAsState(
        targetValue = if (expanded) fullHeight else compact,
        animationSpec = tween(durationMillis = 275),
        label = "Expansão estável do menu"
    )
    Column(Modifier.fillMaxWidth().height(fullHeight)) {
        Spacer(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onTapOutsidePanel))
        Column(
            Modifier.fillMaxWidth().height(panelHeight)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(panelColor),
            content = content
        )
    }
}

// Menu compacto de preferências acessível pelos três pontos ou pelo subtítulo.
// Todas as opções permanecem disponíveis, mas fora da área dos resultados.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectionOptionsSheet(
    sheetState: SheetState,
    onSheetHeight: (Float) -> Unit,
    expandedToTop: Boolean,
    onDragBy: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit,
    electionMode: String,
    selectedGovernor: String,
    automatic: Boolean,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
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
    // Menu compacto: escolhas frequentes no topo, estados sob demanda, status ao final.
    // Os valores são sempre recebidos da tela principal, evitando duplicar estados.
    val isGovernor = electionMode == "governador"
    var showGovernorList by remember(isGovernor) { mutableStateOf(isGovernor) }
    var showSourceInfo by remember { mutableStateOf(false) }
    var refreshRequested by remember { mutableStateOf(false) }
    LaunchedEffect(status) { refreshRequested = false }
    val panel = LocalAppPalette.current
    val lightCard = panel.surface.copy(alpha = if (darkMode) 0.97f else 0.94f)
    val captionSize = 12.sp

    // Não reiniciar o detector a cada pixel arrastado: isso quebrava os gestos rápidos.
    val latestDragBy by rememberUpdatedState(onDragBy)
    val latestDragFinished by rememberUpdatedState(onDragFinished)
    val latestDragCancelled by rememberUpdatedState(onDragCancelled)
    val dragGesture = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, amount ->
                change.consume()
                latestDragBy(amount)
            },
            onDragEnd = { latestDragFinished() },
            onDragCancel = { latestDragCancelled() }
        )
    }
    ModalBottomSheet(
        sheetState = sheetState,
        // Evita a disputa entre a rolagem do conteúdo e o arrasto nativo
        // (causava o movimento repetido para cima/baixo nos swipes rápidos).
        sheetGesturesEnabled = false,
        onDismissRequest = onDismiss,
        modifier = Modifier.onSizeChanged { onSheetHeight(it.height.toFloat()) },
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Color.Transparent,
        // Modal edge-to-edge: não reservar uma segunda faixa para a barra de gestos.
        // O painel interno já desenha até a borda inferior da janela.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        contentColor = panel.text,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = !darkMode,
            isAppearanceLightNavigationBars = !darkMode
        ),
        // Scrim próprio da tela principal com opacidade progressiva.
        scrimColor = Color.Transparent,
        dragHandle = null
    ) {
        StableExpandableSheetBody(
            expanded = expandedToTop,
            compactHeight = 650.dp,
            panelColor = panel.sheet,
            onTapOutsidePanel = onDismiss
        ) {
            Column(
                // A cor do painel continua por trás da barra de gestos;
                // apenas os controles respeitam o espaço seguro inferior.
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)
                    .navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            // Arraste só pela alça/cabeçalho; a lista rola livremente, sem conflito.
            Box(Modifier.fillMaxWidth().then(dragGesture)
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (darkMode) panel.secondary else Color(0xFFBBC4D1)))
            }
            Row(Modifier.fillMaxWidth().then(dragGesture), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Opções", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Tinta)
                    Text(
                        if (isGovernor) "Governador • $selectedGovernor • fonte TSE"
                        else "Presidente • Brasil • ${if (automatic) "TSE" else "Manual"}",
                        fontSize = captionSize, color = Secundario
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = Azul, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // O título e o botão Fechar ficam fixos; só as opções rolam.
            HorizontalDivider(color = Borda)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Surface(shape = RoundedCornerShape(22.dp), color = lightCard,
                border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ACOMPANHAR ELEIÇÃO", fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Secundario)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (!isGovernor) {
                            Button(onClick = onPresident, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Presidente", fontSize = 13.sp, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = onPresident, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Presidente", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        if (isGovernor) {
                            Button(onClick = { showGovernorList = !showGovernorList },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Governador $selectedGovernor", fontSize = 12.sp, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = {
                                showGovernorList = true
                                onGovernor(selectedGovernor)
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("Governador", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                    if (isGovernor && showGovernorList) {
                        HorizontalDivider(color = Borda)
                        Text("Escolha o estado", color = Secundario, fontSize = 12.sp)
                        GOVERNOR_UFS.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { uf ->
                                    FilterChip(
                                        selected = selectedGovernor == uf,
                                        onClick = { onGovernor(uf) },
                                        label = {
                                            Text(uf, modifier = Modifier.fillMaxWidth(),
                                                fontSize = 13.sp, textAlign = TextAlign.Center)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(22.dp), color = lightCard,
                border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DADOS E ALERTAS", fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Secundario)
                    if (!isGovernor) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Atualização automática", color = Tinta,
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(if (automatic) "Resultados oficiais do TSE" else "Preenchimento manual",
                                    fontSize = captionSize, color = Secundario)
                            }
                            Switch(checked = automatic, onCheckedChange = onAutomaticChange,
                                colors = SwitchDefaults.colors(checkedTrackColor = Azul))
                        }
                        HorizontalDivider(color = Borda)
                    } else {
                        Text("A apuração de governador usa os resultados oficiais do TSE.",
                            fontSize = captionSize, color = Secundario)
                        HorizontalDivider(color = Borda)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Notificação da projeção", color = Tinta,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Atualizações com o app em segundo plano",
                                fontSize = captionSize, color = Secundario)
                        }
                        Switch(checked = notificationEnabled, onCheckedChange = onNotificationChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = Azul))
                    }
                    if (notificationError) {
                        Text("Autorize as notificações nas configurações do Android.",
                            fontSize = 12.sp, color = Vermelho)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(22.dp), color = lightCard,
                border = BorderStroke(1.dp, Borda)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("APARÊNCIA", fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            color = Secundario)
                        Text("Modo escuro", color = Tinta,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Adaptar cores, gráficos, mapa e navegação",
                            fontSize = captionSize, color = Secundario)
                    }
                    Switch(
                        checked = darkMode,
                        onCheckedChange = onDarkModeChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = Azul)
                    )
                }
            }

            Surface(shape = RoundedCornerShape(22.dp), color = lightCard,
                border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SINCRONIZAÇÃO", fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Secundario)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(
                            if (isGovernor || automatic) Verde else Azul))
                        Text(status, fontSize = captionSize, color = Secundario,
                            modifier = Modifier.weight(1f))
                    }
                    if (isGovernor || automatic) {
                        Button(onClick = {
                            refreshRequested = true
                            onRefresh()
                        }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)) {
                            Text("Atualizar agora", fontSize = 13.sp)
                        }
                        if (refreshRequested) {
                            Text("Atualização solicitada ao TSE…", fontSize = 12.sp, color = Secundario)
                        }
                    } else {
                        Text("Seus dados manuais são salvos automaticamente neste aparelho.",
                            fontSize = captionSize, color = Secundario)
                    }
                }
            }

            TextButton(onClick = { showSourceInfo = !showSourceInfo },
                modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (showSourceInfo) "Ocultar informações" else "Sobre os dados e projeções",
                    fontSize = 12.sp, color = Secundario)
            }
            if (showSourceInfo) {
                Text(
                    "Aplicativo independente, sem vínculo oficial com o TSE. " +
                        "Os votos publicados pelo TSE são resultados oficiais; " +
                        "as projeções são estimativas matemáticas não oficiais.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    fontSize = 12.sp, textAlign = TextAlign.Center, color = Secundario
                )
            }
            } // Conteúdo rolável: título permanece visível
            } // Painel interno (altura animada)
        } // Âncora externa estável
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
            Text("Exterior", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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

// Seletor visual unificado: pílula sem seta e folha modal com nomes de estados por extenso.
// O valor persistido continua sendo a sigla oficial (inclusive ZZ para Exterior).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerPill(
    selected: String,
    options: List<String>,
    title: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    descriptionFor: (String) -> String? = { null }
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val palette = LocalAppPalette.current
    OutlinedButton(
        onClick = { pickerOpen = true },
        modifier = modifier.heightIn(min = 43.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, palette.border),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = nameForRegion(selected),
            modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1
        )
    }
    if (pickerOpen) {
        val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val pickerScope = rememberCoroutineScope()
        var closingPicker by remember { mutableStateOf(false) }
        fun dismissPicker() {
            if (closingPicker) return
            closingPicker = true
            pickerScope.launch {
                try {
                    if (pickerState.isVisible) pickerState.hide()
                } finally {
                    pickerOpen = false
                }
            }
        }
        var pickerExpanded by remember { mutableStateOf(false) }
        var pickerDrag by remember { mutableFloatStateOf(0f) }
        val dragThreshold = with(LocalDensity.current) { 60.dp.toPx() }
        val pickerDragGesture = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, amount ->
                    change.consume()
                    pickerDrag = (pickerDrag + amount).coerceIn(-300f, 300f)
                },
                onDragEnd = {
                    when {
                        pickerDrag < -dragThreshold -> pickerExpanded = true
                        pickerDrag > dragThreshold && pickerExpanded -> pickerExpanded = false
                        pickerDrag > dragThreshold -> dismissPicker()
                    }
                    pickerDrag = 0f
                },
                onDragCancel = { pickerDrag = 0f }
            )
        }
        ModalBottomSheet(
            sheetState = pickerState,
            sheetGesturesEnabled = false,
            onDismissRequest = { dismissPicker() },
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            containerColor = Color.Transparent,
            // Mesma política do menu Opções: nenhum inset duplicado na base.
            // A cor de palette.sheet é desenhada atrás do indicador de gestos.
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            contentColor = palette.text,
            properties = ModalBottomSheetProperties(
                isAppearanceLightStatusBars = palette == LightPalette,
                isAppearanceLightNavigationBars = palette == LightPalette
            ),
            scrimColor = Color.Black.copy(alpha = 0.25f),
            dragHandle = null
        ) {
            StableExpandableSheetBody(
                expanded = pickerExpanded,
                compactHeight = 610.dp,
                panelColor = palette.sheet,
                onTapOutsidePanel = { dismissPicker() }
            ) {
                // Lista termina acima da barrinha do sistema, sem deixar fundo transparente.
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)
                    .navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().then(pickerDragGesture)
                        .padding(top = 12.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(palette.secondary.copy(alpha = 0.55f)))
                }
                Row(Modifier.fillMaxWidth().then(pickerDragGesture),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Selecione uma região", color = palette.secondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { dismissPicker() }) {
                        Text("Fechar", color = palette.accent)
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(options.size) { index ->
                        val uf = options[index]
                        val isSelected = uf == selected
                        Surface(
                            onClick = {
                                onSelect(uf)
                                dismissPicker()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) palette.accent.copy(alpha = 0.13f)
                                else palette.surface,
                            border = BorderStroke(1.dp, if (isSelected) palette.accent.copy(alpha = 0.40f)
                                else palette.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(fullRegionName(uf), color = palette.text,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp)
                                    descriptionFor(uf)?.let { extra ->
                                        Text(extra, color = palette.secondary, fontSize = 11.sp)
                                    }
                                }
                                if (uf != "ZZ") {
                                    Text(uf, color = palette.secondary, fontSize = 12.sp)
                                }
                                if (isSelected) {
                                    Text("✓", color = palette.accent, fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                } // Conteúdo do seletor de UF
            } // Âncora estável do seletor de UF
        }
    }
}

@Composable
private fun ManualRegions(
    modifier: Modifier, candidateA: String, candidateB: String,
    selected: String, onSelect: (String) -> Unit,
    state: StateData, projection: Estimate,
    all: Map<String, StateData>, onUpdate: (StateData) -> Unit
) {
    var confirmReset by remember { mutableStateOf(false) }
    ContentColumn(modifier) {
        Text("Entrada manual", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Os campos são editáveis e salvos automaticamente no celular. O TSE não é consultado neste modo.", fontSize = 12.sp, color = Secundario)
        SurfaceCard {
            Text("Selecionar estado ou exterior", fontWeight = FontWeight.Medium)
            RegionPickerPill(
                selected = selected,
                options = UFS + "ZZ",
                title = "Selecionar estado ou exterior",
                onSelect = onSelect,
                descriptionFor = { region ->
                    if (all[region]?.let { quantity(it.a) + quantity(it.b) > 0 } == true) "Preenchido" else null
                },
                modifier = Modifier.fillMaxWidth()
            )
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

// Pílula flutuante: cinco destinos e o menu de configurações na sexta posição.
// O fundo realmente desfoca o conteúdo marcado como hazeSource atrás da navegação.
@Composable
private fun GlassNavigationPill(
    selected: Int,
    onTabSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(100.dp)
    val glass = LocalAppPalette.current
    val glassStyle = remember(glass) {
        HazeBlurStyle {
            blurRadius(17.dp)
            noiseFactor(0.035f)
            colorEffects(listOf(HazeColorEffect.tint(glass.glassTint)))
            fallbackColorEffect(HazeColorEffect.tint(glass.glassFallback))
        }
    }
    BoxWithConstraints(
        modifier = modifier.height(66.dp).shadow(16.dp, shape, clip = false)
            .clip(shape)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
            .background(Brush.verticalGradient(listOf(
                glass.surface.copy(alpha = 0.32f),
                glass.surface.copy(alpha = 0.08f)
            )))
            .border(BorderStroke(1.dp, glass.glassBorder), shape)
    ) {
        val inset = 5.dp
        val widthPerItem = (maxWidth - inset * 2) / 6
        val selectedOffset by animateDpAsState(
            targetValue = inset + widthPerItem * selected.coerceIn(0, 4),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "Indicador deslizando pela pílula"
        )
        Box(
            Modifier.offset(x = selectedOffset, y = 5.dp)
                .width(widthPerItem).height(56.dp)
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(
                    Azul.copy(alpha = 0.16f), Azul.copy(alpha = 0.07f)
                )))
                .border(1.dp, glass.glassBorder, RoundedCornerShape(30.dp))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Painel", "Estados", "Mapa", "Gráfico", "Estat.").forEachIndexed { index, name ->
                GlassDockItem(
                    name = name,
                    index = index,
                    selected = selected == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.width(widthPerItem).fillMaxHeight()
                )
            }
            Box(
                modifier = Modifier.width(widthPerItem).fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(onClick = onSettings)
                    .semantics { contentDescription = "Abrir opções da apuração" },
                contentAlignment = Alignment.Center
            ) {
                val menuDotColor = Tinta
                Canvas(Modifier.size(22.dp)) {
                    val x = size.width / 2f
                    val centerY = size.height / 2f
                    for (step in -1..1) {
                        drawCircle(color = menuDotColor, radius = size.width * 0.068f,
                            center = Offset(x, centerY + step * size.height * 0.26f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassDockItem(
    name: String,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Aba $name" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BottomTabSymbol(index, if (selected) Azul else Secundario)
        if (selected) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = name,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tinta,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
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
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 102.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(LocalAppPalette.current.noticeBackground).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("ⓘ", fontSize = 18.sp, color = Aviso)
        Text(message, fontSize = 12.sp,
            color = LocalAppPalette.current.noticeText, lineHeight = 17.sp)
    }
}

// Imagens ilustradas aprovadas pelo usuário e empacotadas localmente no APK.
// O nome (e nunca apenas a cor) decide qual imagem aparece.
// Disputas de governador continuam sem retrato, pois os candidatos variam por UF.
private fun candidatePortraitResource(name: String): Int? = when {
    name.contains("Flávio Bolsonaro", ignoreCase = true) -> R.drawable.candidato_flavio
    name.contains("Lula", ignoreCase = true) -> R.drawable.candidato_lula
    else -> null
}

@Composable
private fun CandidatePortrait(
    name: String,
    accent: Color,
    size: Dp = 44.dp,
    corner: Dp = 12.dp
) {
    val portrait = candidatePortraitResource(name)
    if (portrait != null) {
        val shape = RoundedCornerShape(corner)
        Image(
            painter = painterResource(id = portrait),
            contentDescription = "Retrato ilustrado de $name",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
                .clip(shape)
                .border(1.dp, accent.copy(alpha = 0.55f), shape)
        )
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(corner))
            .background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun CandidateInfo(name: String, votes: Double, share: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CandidatePortrait(name = name, accent = color, size = 46.dp, corner = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, maxLines = 2, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 15.sp,
                    overflow = TextOverflow.Ellipsis, color = Tinta)
                Box(Modifier.width(24.dp).height(3.dp)
                    .clip(RoundedCornerShape(99.dp)).background(color))
            }
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
            .background(LocalAppPalette.current.track),
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
                    color = Azul, trackColor = LocalAppPalette.current.track
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
            Text("Exterior", fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Borda), shape = RoundedCornerShape(14.dp)) {
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
    ContentColumn(modifier) {
        Text("Apuração por região", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Dados importados automaticamente do TSE • 27 UFs e exterior", color = Secundario, fontSize = 12.sp)
        Text(status, color = Secundario, fontSize = 12.sp)
        SurfaceCard {
            Text("Escolher região", fontWeight = FontWeight.Medium)
            RegionPickerPill(
                selected = selected,
                options = UFS + "ZZ",
                title = "Escolher região",
                onSelect = onSelect,
                descriptionFor = { region -> if (all[region]?.hasVotes == true) "Dados atualizados" else null },
                modifier = Modifier.fillMaxWidth()
            )
            if (selected == "ZZ") Text("Total de votos do exterior. Não há mapa por país.", fontSize = 12.sp, color = Secundario)
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
                    color = Azul, trackColor = LocalAppPalette.current.track
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
            Text("Total oficial do exterior, sem mapa de países.", fontSize = 12.sp, color = Secundario)
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
        .background(LocalAppPalette.current.track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total > 0.0) {
            if (first > 0.0) Box(Modifier.weight((first / total).toFloat().coerceAtLeast(.0001f))
                .fillMaxHeight().background(GovernorBlue))
            if (second > 0.0) Box(Modifier.weight((second / total).toFloat().coerceAtLeast(.0001f))
                .fillMaxHeight().background(GovernorPurple))
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
                color = Azul, trackColor = LocalAppPalette.current.track
            )
            if (report.hasVotes) {
                report.candidates.forEachIndexed { index, candidate ->
                    GovernorCandidateRow(candidate, report.validVotes,
                        if (index == 0) GovernorBlue else GovernorPurple)
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
                    if (index == 0) GovernorBlue else GovernorPurple,
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
    val report = all[selected]
    ContentColumn(modifier) {
        Text("Governadores • 2º turno", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(status, fontSize = 12.sp, color = Secundario)
        SurfaceCard {
            Text("Estado ou Distrito Federal", fontWeight = FontWeight.SemiBold)
            RegionPickerPill(
                selected = selected,
                options = GOVERNOR_UFS,
                title = "Selecionar governador",
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth()
            )
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
                colorA = GovernorBlue, colorB = GovernorPurple)
            Text("Use dois dedos para dar zoom; arraste para explorar. Dois toques ampliam e ↺ restaura o mapa.",
                fontSize = 11.sp, color = Secundario)
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
                        if (index == 0) GovernorBlue else GovernorPurple)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CandidatePortrait(name = name, accent = tint, size = 32.dp, corner = 9.dp)
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, color = Tinta, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
        // Percentuais ficam em alto contraste: escuros no tema claro e claros no tema escuro.
        Text(percentage(value), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tinta)
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
            val gridLineColor = Borda
            val markerFill = LocalAppPalette.current.surface
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
                    drawLine(gridLineColor, Offset(0f, py), Offset(graphWidth, py),
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
                    drawCircle(markerFill, radius = 5.4.dp.toPx(), center = marker)
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
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CandidatePortrait(name = name, accent = tint, size = 23.dp, corner = 7.dp)
        Box(Modifier.width(12.dp).height(3.dp).clip(RoundedCornerShape(5.dp)).background(tint))
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

// Cor do vencedor apenas: intensidade proporcional à vantagem, sem misturar verde/vermelho.
// Empate exato recebe uma cor neutra. Verde ou vermelho puros representam 100% dos votos.
private fun winningMapColor(
    result: Estimate,
    first: Color = Verde,
    second: Color = Vermelho,
    missing: Color = Color(0xFFE2E7ED),
    dark: Boolean = false
): Color {
    if (!result.valid || result.validos <= 0) return missing
    val shareA = (result.a / result.validos).toFloat().coerceIn(0f, 1f)
    if (kotlin.math.abs(shareA - 0.5f) < 0.000001f) {
        return if (dark) Color(0xFF66758B) else Color(0xFFAEB8C6)
    }
    val candidateAWon = shareA > 0.5f
    val winner = if (candidateAWon) first else second
    // Tons de base pastel cuidadosamente separados também em 51/49 ou 60/40.
    // No escuro, a base tem mais contraste com o fundo do mapa.
    val soft = when {
        dark && candidateAWon -> Color(0xFF365D4B)
        dark -> Color(0xFF78464B)
        candidateAWon -> Color(0xFF9FD5B5)
        else -> Color(0xFFF0AAA7)
    }
    val winnerShare = maxOf(shareA, 1f - shareA)
    val strength = ((winnerShare - 0.5f) * 2f).coerceIn(0f, 1f)
    return lerp(soft, winner, strength)
}

// Limita o deslocamento para que a área visível nunca saia completamente do mapa.
// As coordenadas do deslocamento são pixels na tela; o zoom é aplicado ao desenho,
// e a conversão inversa é usada nos toques para identificar a UF corretamente.
private fun limitMapPan(offset: Offset, scale: Float, viewport: IntSize): Offset {
    if (scale <= 1.001f || viewport.width == 0 || viewport.height == 0) return Offset.Zero
    val maxX = (viewport.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (viewport.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
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
    val mapPalette = LocalAppPalette.current
    val mapSelectedBorder = Azul
    // Bordas nítidas sobre os fundos claro e escuro, mesmo após dar zoom.
    val mapOutline = if (mapPalette == DarkPalette) Color(0xFF6A7F9B)
        else Color(0xFF69788C)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // Mantemos os detectores de toque independentes do gesto de zoom/panorâmica.
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)

    fun zoomTo(target: Float, focal: Offset? = null) {
        val nextScale = target.coerceIn(1f, 6f)
        if (nextScale <= 1.001f) {
            scale = 1f
            offset = Offset.Zero
            return
        }
        val ratio = nextScale / scale
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        // Se o zoom foi acionado com dois toques, mantém o ponto tocado sob o dedo.
        val focused = focal ?: center
        val relative = focused - center
        val candidate = Offset(
            relative.x - (relative.x - offset.x) * ratio,
            relative.y - (relative.y - offset.y) * ratio
        )
        offset = limitMapPan(candidate, nextScale, viewport)
        scale = nextScale
    }

    val gestureState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
        if (nextScale <= 1.001f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val ratio = nextScale / scale
            offset = limitMapPan(
                Offset(offset.x * ratio + panChange.x, offset.y * ratio + panChange.y),
                nextScale, viewport
            )
            scale = nextScale
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(mapPalette.mapBackground)
            .onSizeChanged { viewport = it }
            // Quando o zoom é 1x, um dedo continua rolando a página normalmente.
            // Com zoom ativo, o mesmo movimento passa a arrastar o mapa.
            .transformable(state = gestureState, canPan = { scale > 1.01f })
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
                .pointerInput(geometry) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val target = when {
                                latestScale < 1.5f -> 2.6f
                                latestScale < 4f -> 4.8f
                                else -> 1f
                            }
                            zoomTo(target, focal = tap)
                        },
                        onTap = { tap ->
                            val square = min(size.width, size.height).toFloat() * 0.95f
                            val left = (size.width - square) / 2f
                            val top = (size.height - square) / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val unzoomed = Offset(
                                center.x + (tap.x - center.x - latestOffset.x) / latestScale,
                                center.y + (tap.y - center.y - latestOffset.y) / latestScale
                            )
                            val geo = Offset(
                                (unzoomed.x - left) / square * 10000f,
                                10000f - (unzoomed.y - top) / square * 10000f
                            )
                            geometry.asReversed().firstOrNull { insidePolygon(geo, it.points) }
                                ?.let { onSelected(it.uf) }
                        }
                    )
                }
        ) {
            val square = min(size.width, size.height) * 0.95f
            val left = (size.width - square) / 2f
            val top = (size.height - square) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            geometry.forEach { state ->
                val path = Path()
                state.points.forEachIndexed { i, point ->
                    val baseX = left + point.x / 10000f * square
                    val baseY = top + (10000f - point.y) / 10000f * square
                    val x = center.x + (baseX - center.x) * scale + offset.x
                    val y = center.y + (baseY - center.y) * scale + offset.y
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = winningMapColor(estimates.getValue(state.uf), colorA, colorB,
                    mapPalette.emptyState, dark = mapPalette == DarkPalette))
                drawPath(
                    path,
                    color = if (selected == state.uf) mapSelectedBorder else mapOutline,
                    style = Stroke(width = if (selected == state.uf) 2.8.dp.toPx() else 1.15.dp.toPx())
                )
            }
        }

        // Controles também permitem usar o zoom sem gestos multitoque.
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            color = mapPalette.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, mapPalette.border),
            shadowElevation = 3.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { zoomTo(scale / 1.6f) },
                    enabled = scale > 1.01f,
                    modifier = Modifier.size(38.dp).semantics {
                        contentDescription = "Diminuir zoom do mapa"
                    }
                ) { Text("−", fontSize = 23.sp, color = if (scale > 1.01f) mapPalette.text else mapPalette.secondary) }
                Text(
                    "${(scale * 100).toInt()}%",
                    modifier = Modifier.width(46.dp),
                    color = mapPalette.text,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                IconButton(
                    onClick = { zoomTo(scale * 1.6f) },
                    enabled = scale < 5.99f,
                    modifier = Modifier.size(38.dp).semantics {
                        contentDescription = "Aumentar zoom do mapa"
                    }
                ) { Text("+", fontSize = 22.sp, color = if (scale < 5.99f) mapPalette.text else mapPalette.secondary) }
                IconButton(
                    onClick = { zoomTo(1f) },
                    enabled = scale > 1.01f,
                    modifier = Modifier.size(38.dp).semantics {
                        contentDescription = "Restaurar mapa para 100 por cento"
                    }
                ) { Text("↺", fontSize = 23.sp, color = if (scale > 1.01f) mapPalette.text else mapPalette.secondary) }
            }
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
            Text("Aproxime dois dedos ou toque em + para ampliar. Arraste para explorar e use ↺ para restaurar.",
                fontSize = 11.sp, color = Secundario)
            Text(
                if (manualMode) "Cor do candidato à frente, com intensidade conforme os votos digitados em cada UF."
                else "Cor do candidato à frente, com intensidade conforme os votos oficiais apurados em cada UF.",
                fontSize = 12.sp, color = Secundario
            )
            // Escala de duas famílias de cor, sem o marrom criado pela mistura anterior.
            val darkMap = LocalAppPalette.current == DarkPalette
            val softRed = if (darkMap) Color(0xFF78464B) else Color(0xFFF0AAA7)
            val softGreen = if (darkMap) Color(0xFF365D4B) else Color(0xFF9FD5B5)
            val tied = if (darkMap) Color(0xFF66758B) else Color(0xFFAEB8C6)
            Box(
                Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(
                        0f to Vermelho,
                        0.485f to softRed,
                        0.499f to softRed,
                        0.5f to tied,
                        0.501f to softGreen,
                        0.515f to softGreen,
                        1f to Verde
                    ))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100% $candidateB", fontSize = 11.sp, color = Tinta)
                Text("Empate", fontSize = 11.sp, color = Tinta)
                Text("100% $candidateA", fontSize = 11.sp, color = Tinta)
            }
            Text("A cor mostra quem lidera; quanto mais intensa, maior sua vantagem. " +
                "Cinza central = empate.", fontSize = 11.sp, color = Secundario)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(11.dp).background(LocalAppPalette.current.emptyState, RoundedCornerShape(3.dp)))
                Text("Cinza = sem votos oficiais disponíveis", fontSize = 12.sp, color = Secundario)
            }
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Estado selecionado", fontSize = 12.sp, color = Secundario)
                    Text(picked, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Trocar UF", fontSize = 12.sp, color = Secundario)
                    RegionPickerPill(
                        selected = picked,
                        options = UFS,
                        title = "Escolher UF no mapa",
                        onSelect = { picked = it },
                        compact = true,
                        modifier = Modifier.widthIn(min = 94.dp, max = 110.dp)
                    )
                }
            }
            Text("Urnas apuradas: ${percentage(current.urnas)}", fontSize = 13.sp, color = Tinta, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { (current.urnas / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                color = Azul,
                trackColor = LocalAppPalette.current.track
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
