package br.com.apuracaoeleitoral

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

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
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("▦", fontSize = 20.sp) },
                        label = { Text("Painel") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Azul, selectedTextColor = Azul, indicatorColor = Color(0xFFEAF0FB))
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("☷", fontSize = 20.sp) },
                        label = { Text("Estados") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Azul, selectedTextColor = Azul, indicatorColor = Color(0xFFEAF0FB))
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Text("◉", fontSize = 20.sp) },
                        label = { Text("Estatísticas") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Azul, selectedTextColor = Azul, indicatorColor = Color(0xFFEAF0FB))
                    )
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
