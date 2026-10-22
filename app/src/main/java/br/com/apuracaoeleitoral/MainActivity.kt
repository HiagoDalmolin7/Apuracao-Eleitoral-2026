package br.com.apuracaoeleitoral

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ElectionApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectionApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Mantém o mesmo arquivo de dados da versão anterior, evitando perder as entradas.
    val prefs = remember { context.getSharedPreferences("apuracao_v2", 0) }
    var candidateA by remember {
        mutableStateOf(prefs.getString("candidateA", "Flávio Bolsonaro") ?: "Flávio Bolsonaro")
    }
    var candidateB by remember {
        mutableStateOf(prefs.getString("candidateB", "Lula") ?: "Lula")
    }
    var states by remember {
        mutableStateOf(UFS.associateWith { uf -> loadState(prefs.getString("uf_$uf", null), uf) })
    }
    var selectedUF by remember { mutableStateOf("SC") }
    var tab by remember { mutableIntStateOf(0) }

    fun update(value: StateData) {
        states = states.toMutableMap().also { it[value.uf] = value }
        prefs.edit().putString("uf_${value.uf}", value.asJson()).apply()
    }

    val computed = remember(states) { states.mapValues { estimate(it.value) } }
    val recorded = computed.filterValues { it.valid }
    val estimateA = recorded.values.sumOf { it.a }
    val estimateB = recorded.values.sumOf { it.b }
    val total = estimateA + estimateB
    val shareA = if (total > 0) estimateA / total else 0.0
    val shareB = if (total > 0) estimateB / total else 0.0
    val warnings = recorded.values.count { it.warning.isNotBlank() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Azul, background = Fundo, surface = Color.White,
            onSurface = Tinta, onBackground = Tinta
        ),
        typography = Typography()
    ) {
        Scaffold(
            containerColor = Fundo,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Apuração Eleitoral 2026", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Presidência  •  2º turno", fontSize = 12.sp, color = Secundario)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                // Distribui igualmente as quatro abas, com folga nas bordas
                // e afastamento automático da área de gestos do Android.
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Painel", "Estados", "Mapa", "Estatísticas").forEachIndexed { index, label ->
                            BottomTab(
                                name = label,
                                index = index,
                                active = tab == index,
                                onClick = { tab = index },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> Dashboard(
                    modifier = Modifier.padding(padding),
                    candidateA = candidateA, candidateB = candidateB,
                    estimateA = estimateA, estimateB = estimateB,
                    states = computed, warnings = warnings,
                    onChooseState = { uf -> selectedUF = uf; tab = 1 },
                    onStates = { tab = 1 }
                )
                1 -> StatesPage(
                    modifier = Modifier.padding(padding),
                    candidateA = candidateA, candidateB = candidateB,
                    selected = selectedUF,
                    select = { selectedUF = it },
                    data = states.getValue(selectedUF),
                    result = computed.getValue(selectedUF),
                    update = ::update,
                    all = computed
                )
                2 -> MapPage(
                    modifier = Modifier.padding(padding),
                    candidateA = candidateA, candidateB = candidateB,
                    computed = computed,
                    editState = { uf -> selectedUF = uf; tab = 1 }
                )
                else -> StatisticsPage(
                    modifier = Modifier.padding(padding),
                    candidateA = candidateA,
                    candidateB = candidateB,
                    renameA = { candidateA = it; prefs.edit().putString("candidateA", it).apply() },
                    renameB = { candidateB = it; prefs.edit().putString("candidateB", it).apply() },
                    all = states, calculations = computed
                )
            }
        }
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
            fontSize = if (name == "Estatísticas") 10.sp else 11.sp,
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

@Composable
private fun Dashboard(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    estimateA: Double, estimateB: Double,
    states: Map<String, Estimate>, warnings: Int,
    onChooseState: (String) -> Unit, onStates: () -> Unit
) {
    val sample = states.values.filter { it.valid }
    val total = estimateA + estimateB
    val shareA = if (total > 0) estimateA / total else 0.0
    val shareB = if (total > 0) estimateB / total else 0.0
    val actualA = sample.sumOf { it.observedA }
    val actualB = sample.sumOf { it.observedB }
    val remaining = max(0.0, total - actualA - actualB)

    ContentColumn(modifier) {
        Note("PROJEÇÃO NÃO OFICIAL • Estimativas calculadas com os dados inseridos manualmente. As urnas restantes podem ter distribuição de votos diferente.")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Projeção nacional", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Somente UFs com dados suficientes", fontSize = 12.sp, color = Secundario)
                }
                Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFFF0F3F7)) {
                    Text("${sample.size}/27 UFs", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CandidateInfo(candidateA, estimateA, shareA, Verde, Modifier.weight(1f))
                CandidateInfo(candidateB, estimateB, shareB, Vermelho, Modifier.weight(1f))
            }
            RatioBar(estimateA, estimateB, thickness = 14)
            HorizontalDivider(color = Borda)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Diferença projetada", fontSize = 12.sp, color = Secundario)
                    Text("${count(abs(estimateA - estimateB))} votos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("ESTIMATIVA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Secundario)
            }
            if (sample.size < 27) Text("A projeção não contempla ${27 - sample.size} UF(s). Ainda não é uma estimativa nacional completa.", color = Aviso, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MiniStat("Apurados", count(actualA + actualB), Modifier.weight(1f))
            MiniStat("Restantes*", count(remaining), Modifier.weight(1f))
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Apuração por estado", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onStates) { Text("Ver todos") }
            }
            val populated = UFS.mapNotNull { uf ->
                val s = states.getValue(uf)
                if (s.valid) uf to s else null
            }
            if (populated.isEmpty()) {
                Text("Nenhum estado preenchido. Abra Estados para começar a apuração.", color = Secundario, fontSize = 13.sp)
                Button(onClick = onStates, modifier = Modifier.fillMaxWidth()) { Text("Preencher um estado") }
            } else {
                populated.take(6).forEach { (uf, data) ->
                    StateSummaryRow(uf, data, onClick = { onChooseState(uf) })
                }
                if (populated.size > 6) Text("Mais ${populated.size - 6} estado(s) disponíveis na aba Estados", fontSize = 12.sp, color = Secundario)
            }
        }
        if (warnings > 0) Note("$warnings UF(s) com alertas de plausibilidade. Confira os dados na aba Estados.")
        Text("*Votos restantes estimados nas UFs com dados. As porcentagens representam projeções matemáticas, não resultados oficiais.", color = Secundario, fontSize = 11.sp, lineHeight = 16.sp)
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
private fun StatesPage(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    selected: String, select: (String) -> Unit,
    data: StateData, result: Estimate,
    update: (StateData) -> Unit,
    all: Map<String, Estimate>
) {
    var expanded by remember { mutableStateOf(false) }
    ContentColumn(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Apuração por estado", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Escolha uma UF e informe os números acumulados.", fontSize = 13.sp, color = Secundario)
        }
        SurfaceCard {
            Text("Estado selecionado", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Secundario)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text(selected, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, fontWeight = FontWeight.Bold)
                    Text("⌄", fontSize = 20.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    UFS.forEach { uf ->
                        DropdownMenuItem(
                            text = { Text("$uf${if (all.getValue(uf).valid) "  •  preenchido" else ""}") },
                            onClick = { select(uf); expanded = false }
                        )
                    }
                }
            }
            Text("Urnas apuradas", fontSize = 12.sp, color = Secundario)
            Text(percentage(result.urnas), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tinta)
            LinearProgressIndicator(
                progress = { (result.urnas / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(10.dp)),
                color = Azul, trackColor = Color(0xFFE8EDF4)
            )
            NumberField("Porcentagem de urnas apuradas (%)", data.urnas, { update(data.copy(urnas = it)) }, decimal = true)
        }
        SurfaceCard {
            Text("Votos dos candidatos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Informe os votos acumulados nas urnas já apuradas.", fontSize = 12.sp, color = Secundario)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(10.dp).background(Verde, CircleShape))
                Text(candidateA, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            NumberField("Votos informados", data.a, { update(data.copy(a = it)) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(10.dp).background(Vermelho, CircleShape))
                Text(candidateB, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            NumberField("Votos informados", data.b, { update(data.copy(b = it)) })
        }
        SurfaceCard {
            Text("Eleitorado e outros votos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Informe valores observados, não projeções. Deixe em branco quando não souber.", fontSize = 12.sp, color = Secundario)
            NumberField("Eleitores totais da UF", data.eleitores, { update(data.copy(eleitores = it)) })
            NumberField("Abstenções observadas", data.abst, { update(data.copy(abst = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Brancos", data.brancos, { update(data.copy(brancos = it)) }, modifier = Modifier.weight(1f))
                NumberField("Nulos", data.nulos, { update(data.copy(nulos = it)) }, modifier = Modifier.weight(1f))
            }
        }
        SurfaceCard {
            Text("Projeção para $selected", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CandidateInfo(candidateA, result.a, if (result.validos > 0) result.a / result.validos else 0.0, Verde, Modifier.weight(1f))
                CandidateInfo(candidateB, result.b, if (result.validos > 0) result.b / result.validos else 0.0, Vermelho, Modifier.weight(1f))
            }
            RatioBar(result.a, result.b)
            HorizontalDivider(color = Borda)
            Text("Brancos: ${count(result.brancos)}  •  Nulos: ${count(result.nulos)}", fontSize = 12.sp)
            Text("Abstenções: ${count(result.abst)}", fontSize = 12.sp)
            if (result.warning.isNotBlank()) Note(result.warning)
            OutlinedButton(onClick = { update(StateData(selected)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar os dados de $selected")
            }
        }
        Note("Os dados são salvos automaticamente neste aparelho. O percentual de urnas é usado como aproximação do percentual de votos apurados.")
    }
}

@Composable
private fun StatisticsPage(
    modifier: Modifier,
    candidateA: String, candidateB: String,
    renameA: (String) -> Unit, renameB: (String) -> Unit,
    all: Map<String, StateData>, calculations: Map<String, Estimate>
) {
    val rows = calculations.values.filter { it.valid }
    val electorate = all.values.sumOf { quantity(it.eleitores) }
    val projectedAbst = rows.sumOf { it.abst }
    val projectedBlank = rows.sumOf { it.brancos }
    val projectedNull = rows.sumOf { it.nulos }
    val projectedVotes = rows.sumOf { it.validos }
    val turnouts = projectedVotes + projectedBlank + projectedNull
    val registered = all.values.count { quantity(it.eleitores) > 0 }
    ContentColumn(modifier) {
        Text("Estatísticas", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        SurfaceCard {
            Text("Eleitorado e comparecimento", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            SummaryLine("Eleitores cadastrados", count(electorate))
            SummaryLine("UFs com eleitorado informado", "$registered de 27")
            SummaryLine("Comparecimento projetado*", count(turnouts))
            SummaryLine("Abstenções projetadas*", count(projectedAbst))
        }
        SurfaceCard {
            Text("Distribuição dos votos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            SummaryLine("Votos válidos projetados*", count(projectedVotes))
            SummaryLine("Votos brancos projetados*", count(projectedBlank))
            SummaryLine("Votos nulos projetados*", count(projectedNull))
            Text("*Somente UFs com dados válidos. Categorias não preenchidas não são estimadas automaticamente.", fontSize = 12.sp, color = Secundario)
        }
        SurfaceCard {
            Text("Nomes dos candidatos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(candidateA, renameA, label = { Text("Candidato verde") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(candidateB, renameB, label = { Text("Candidato vermelho") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("A mudança de nome não altera votos já registrados.", fontSize = 12.sp, color = Secundario)
        }
        Note("Todas as projeções são cálculos proporcionais das urnas informadas. Não há conexão com o TSE, nem previsão estatística do resultado final.")
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
private const val BR_MAP_GZIP_BASE64 = "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"

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

private fun mapColor(result: Estimate): Color {
    if (!result.valid || result.validos <= 0) return Color(0xFFE2E7ED)
    // A proporção é entre os dois candidatos no próprio estado.
    val shareA = (result.a / result.validos).toFloat().coerceIn(0f, 1f)
    return lerp(Vermelho, Verde, shareA)
}

@Composable
private fun BrazilStateMap(
    geometry: List<UfOutline>,
    estimates: Map<String, Estimate>,
    selected: String,
    onSelected: (String) -> Unit
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
            drawPath(path, color = mapColor(estimates.getValue(state.uf)))
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
    editState: (String) -> Unit
) {
    val geometry = remember { readBrazilMap() }
    var picked by remember { mutableStateOf("SC") }
    var expanded by remember { mutableStateOf(false) }
    val current = computed.getValue(picked)
    val reported = computed.values.count { it.valid }
    ContentColumn(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mapa da apuração", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Toque em um estado para ver sua projeção.", fontSize = 13.sp, color = Secundario)
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Brasil por estado", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("$reported/27 UFs", fontSize = 12.sp, color = Secundario)
            }
            BrazilStateMap(geometry, computed, picked, onSelected = { picked = it })
            Text("Cores proporcionais aos votos válidos informados em cada UF.", fontSize = 12.sp, color = Secundario)
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
                Text("Cinza = estado sem dados suficientes", fontSize = 12.sp, color = Secundario)
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
                SummaryLine("Votos observados", count(current.observedA + current.observedB))
                if (current.warning.isNotBlank()) Note(current.warning)
            } else {
                Text("Sem votos suficientes para projetar esta UF. Informe o percentual de urnas e os votos dos candidatos.", color = Secundario, fontSize = 13.sp)
            }
            Button(onClick = { editState(picked) }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (current.valid) "Atualizar" else "Preencher"} dados de $picked")
            }
        }
        Note("MAPA NÃO OFICIAL • A mistura de cores mostra apenas a proporção dos votos válidos de cada UF. Não representa o percentual de urnas apuradas e não prevê mudanças nas urnas restantes.")
        Text("Geometria simplificada dos estados (TopoJSON, licença MIT), disponível no app sem internet.", color = Secundario, fontSize = 11.sp)
    }
}
