package br.com.apuracaoeleitoral

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private val UFS = listOf("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO")
private val br = Locale("pt", "BR")
private fun num(s: String): Double = s.replace(".", "").replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
private fun fmt(d: Double) = NumberFormat.getIntegerInstance(br).format(d.toLong())
private fun pct(d: Double) = String.format(br,"%.2f%%",d)
private data class StateData(val uf: String, val urnas: String = "", val a: String = "", val b: String = "", val eleitores: String = "", val abst: String = "", val brancos: String = "", val nulos: String = "")
private data class Estimate(val a: Double = 0.0, val b: Double = 0.0, val abst: Double = 0.0, val brancos: Double = 0.0, val nulos: Double = 0.0, val urnas: Double = 0.0, val eleitores: Double = 0.0, val valid: Boolean = false, val warning: String = "") {
    val validos get() = a + b
    val comparecimento get() = validos + brancos + nulos
}
private fun estimate(s: StateData): Estimate {
    val p = num(s.urnas) / 100.0
    val electorate = num(s.eleitores)
    val a = num(s.a); val b = num(s.b); val ab = num(s.abst); val br = num(s.brancos); val nu = num(s.nulos)
    if (p <= 0.0 || p > 1.0) return Estimate(urnas = p * 100, eleitores = electorate, warning = if (a+b+ab+br+nu > 0) "Informe urnas apuradas entre 0,01% e 100%." else "")
    val projection = Estimate(a/p,b/p,ab/p,br/p,nu/p,p*100,electorate,true)
    val total = projection.comparecimento + projection.abst
    val warn = when {
        electorate <= 0 -> "Eleitorado não informado: projeção sem verificação de limite."
        total > electorate * 1.001 -> "Inconsistência: votos + abstenções projetados superam o eleitorado. Confira os dados e a distribuição das urnas."
        total < electorate * 0.85 -> "Atenção: votos + abstenções projetados estão abaixo de 85% do eleitorado. Pode haver diferença no tamanho das urnas ou dados incompletos."
        else -> ""
    }
    return projection.copy(warning = warn)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ElectionScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElectionScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("apuracao_v2", 0) }
    var candidateA by remember { mutableStateOf(prefs.getString("candidateA", "Flávio Bolsonaro") ?: "Flávio Bolsonaro") }
    var candidateB by remember { mutableStateOf(prefs.getString("candidateB", "Lula") ?: "Lula") }
    var states by remember {
        mutableStateOf(UFS.associateWith { uf ->
            runCatching {
                val j = JSONObject(prefs.getString("uf_$uf", "{}") ?: "{}")
                StateData(uf,j.optString("urnas"),j.optString("a"),j.optString("b"),j.optString("eleitores"),j.optString("abst"),j.optString("brancos"),j.optString("nulos"))
            }.getOrElse { StateData(uf) }
        })
    }
    var selected by remember { mutableStateOf("SC") }
    var expanded by remember { mutableStateOf(false) }
    var showOnlyEntered by remember { mutableStateOf(true) }
    val estimates = states.mapValues { estimate(it.value) }
    val used = estimates.filterValues { it.valid && it.validos > 0 }
    val nationalA = used.values.sumOf { it.a }
    val nationalB = used.values.sumOf { it.b }
    val nationalValid = nationalA + nationalB
    val totalElectorate = states.values.sumOf { num(it.eleitores) }
    val projectedAbst = used.values.sumOf { it.abst }
    val projectedBlank = used.values.sumOf { it.brancos }
    val projectedNull = used.values.sumOf { it.nulos }
    val coverage = states.values.count { num(it.urnas) > 0 && num(it.a) + num(it.b) > 0 }
    val current = states.getValue(selected)
    val selectedEstimate = estimates.getValue(selected)
    fun update(s: StateData) {
        states = states.toMutableMap().also { it[s.uf] = s }
        prefs.edit().putString("uf_${s.uf}",JSONObject().put("urnas",s.urnas).put("a",s.a).put("b",s.b)
            .put("eleitores",s.eleitores).put("abst",s.abst).put("brancos",s.brancos).put("nulos",s.nulos).toString()).apply()
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2455A4), background = Color(0xFFF7F8FC), surface = Color.White)) {
        Scaffold(topBar = { TopAppBar(title = { Text("Apuração • 2º turno", fontWeight = FontWeight.Bold) }) }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1CF))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("PROJEÇÃO NÃO OFICIAL", fontWeight = FontWeight.Bold, color = Color(0xFF865800))
                        Text("Extrapolação proporcional por UF. A distribuição de votos nas urnas restantes pode ser diferente. Não é pesquisa eleitoral nem previsão probabilística.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Candidatos", fontWeight = FontWeight.Bold)
                    OutlinedTextField(candidateA, { candidateA=it; prefs.edit().putString("candidateA",it).apply() }, label={Text("Candidato 1")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(candidateB, { candidateB=it; prefs.edit().putString("candidateB",it).apply() }, label={Text("Candidato 2")}, singleLine=true, modifier=Modifier.fillMaxWidth())
                } }
                Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Projeção nacional", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$coverage de 27 UFs com dados válidos • ${27-coverage} sem projeção", color=Color.DarkGray)
                    Text("$candidateA: ${fmt(nationalA)} votos • ${pct(if(nationalValid>0) nationalA/nationalValid*100 else 0.0)}", fontWeight=FontWeight.SemiBold)
                    LinearProgressIndicator(progress={if(nationalValid>0) (nationalA/nationalValid).toFloat().coerceIn(0f,1f) else 0f}, modifier=Modifier.fillMaxWidth())
                    Text("$candidateB: ${fmt(nationalB)} votos • ${pct(if(nationalValid>0) nationalB/nationalValid*100 else 0.0)}", fontWeight=FontWeight.SemiBold)
                    if(nationalValid>0) Text("À frente nas UFs informadas: ${if(nationalA>nationalB) candidateA else if(nationalB>nationalA) candidateB else "empate"} • diferença de ${fmt(abs(nationalA-nationalB))} votos")
                    HorizontalDivider()
                    Text("Votos válidos projetados: ${fmt(nationalValid)}")
                    Text("Brancos projetados: ${fmt(projectedBlank)} • Nulos: ${fmt(projectedNull)}")
                    Text("Abstenções projetadas: ${fmt(projectedAbst)}")
                    Text("Eleitores cadastrados (27 UFs): ${fmt(totalElectorate)}")
                    Text("A soma nacional é parcial enquanto houver UFs sem dados. Não interprete como resultado final do Brasil.", color=Color(0xFF865800), style=MaterialTheme.typography.bodySmall)
                } }
                Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Atualizar estado", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}) {
                        OutlinedTextField(selected,onValueChange={},readOnly=true,label={Text("UF")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                            UFS.forEach { uf -> DropdownMenuItem(text={Text(uf)},onClick={selected=uf;expanded=false}) }
                        }
                    }
                    Text("Votos e abstenções acumulados nas urnas já apuradas (não informe projeções)", style=MaterialTheme.typography.bodySmall)
                    NumberField("Urnas apuradas (%)",current.urnas){update(current.copy(urnas=it))}
                    NumberField("Votos de $candidateA",current.a){update(current.copy(a=it))}
                    NumberField("Votos de $candidateB",current.b){update(current.copy(b=it))}
                    HorizontalDivider()
                    NumberField("Eleitores totais no estado",current.eleitores){update(current.copy(eleitores=it))}
                    NumberField("Abstenções observadas (se disponíveis)",current.abst){update(current.copy(abst=it))}
                    NumberField("Votos brancos observados",current.brancos){update(current.copy(brancos=it))}
                    NumberField("Votos nulos observados",current.nulos){update(current.copy(nulos=it))}
                    Text("As quantidades observadas são extrapoladas dividindo pelo percentual de urnas. Abstenções podem não ser publicadas durante a apuração; deixe em branco se não souber.", style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Projeção para $selected", fontWeight=FontWeight.Bold)
                    Text("$candidateA: ${fmt(selectedEstimate.a)} • $candidateB: ${fmt(selectedEstimate.b)}")
                    Text("Brancos: ${fmt(selectedEstimate.brancos)} • Nulos: ${fmt(selectedEstimate.nulos)} • Abstenções: ${fmt(selectedEstimate.abst)}")
                    if(selectedEstimate.warning.isNotBlank()) Text(selectedEstimate.warning,color=Color(0xFFAD6500),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={update(StateData(selected))}) { Text("Limpar dados de $selected") }
                } }
                Card { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Resumo por UF", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                    Row { Checkbox(checked=showOnlyEntered,onCheckedChange={showOnlyEntered=it}); Text("Mostrar apenas estados preenchidos",modifier=Modifier.padding(top=12.dp)) }
                    UFS.filter { !showOnlyEntered || estimates.getValue(it).validos>0 }.forEach { uf ->
                        val e=estimates.getValue(uf)
                        HorizontalDivider()
                        Text("$uf • ${pct(e.urnas)} das urnas • ${if(e.valid) "${fmt(e.a)} × ${fmt(e.b)}" else "sem projeção"}")
                        if(e.warning.isNotBlank()) Text("⚠ ${e.warning}",style=MaterialTheme.typography.bodySmall,color=Color(0xFFAD6500))
                        TextButton(onClick={selected=uf}) { Text("Editar $uf") }
                    }
                } }
                Text("Os dados são salvos automaticamente neste aparelho. Não há conexão com o TSE. Percentual de urnas é usado como aproximação do percentual de votos; regiões com urnas de tamanhos diferentes podem distorcer a projeção.",style=MaterialTheme.typography.bodySmall,color=Color.DarkGray)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NumberField(label: String,value: String,onChange:(String)->Unit) {
    OutlinedTextField(value,onValueChange={ if(it.all { c -> c.isDigit() || c == ',' || c == '.' }) onChange(it) },label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
}
