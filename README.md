# Apuração Eleitoral — versão 2 (Android nativo)

Projeto Kotlin/Jetpack Compose, tema claro, com 27 UFs e armazenamento automático local.

## Entradas por UF
- Percentual de urnas apuradas (0 a 100).
- Votos observados de cada candidato, brancos, nulos e abstenções observadas, quando disponíveis.
- Eleitorado total cadastrado na UF.

## Cálculo
Para cada UF com votos informados e percentual p de urnas apuradas: projeção de cada categoria = quantidade observada / (p/100). A soma nacional considera somente UFs com votos válidos e percentual informado. O app **não inventa resultados** para UFs ausentes. O eleitorado é usado como verificação de plausibilidade, não como um fator que altera artificialmente a proporção de votos. Abstenções só são projetadas se o usuário as informar.

**Limitações:** o percentual de urnas pode não equivaler ao percentual de votos; não há modelagem do perfil das urnas restantes, nem fonte oficial automática, nem incerteza estatística calibrada. O painel sempre informa que são projeções não oficiais.

## Compilação
Abra a pasta no Android Studio com SDK 35 e JDK 17. Sincronize Gradle e execute Run ou Build > Build APK(s). O projeto não contém APK pré-compilado.


## Gerar APK via GitHub Actions (sem instalar Android Studio)
1. Crie um repositório no GitHub, por exemplo `apuracao-eleitoral`.
2. Extraia o ZIP deste projeto e envie para a raiz do repositório o conteúdo extraído, mantendo as pastas `app/` e `.github/workflows/`.
3. Na aba **Actions**, abra **Gerar APK Android** e clique em **Run workflow**; o envio inicial ao ramo `main` também pode disparar a compilação.
4. Quando o workflow estiver concluído, abra a execução e baixe o artefato **ApuracaoEleitoral-APK**.
5. Extraia o arquivo baixado para encontrar `app-debug.apk`. Esse APK pode ser instalado no seu telefone Android. Talvez seja necessário permitir a instalação a partir do aplicativo usado para abrir o arquivo.

Este APK é um **debug APK**, adequado para testes e uso pessoal, não assinado para publicação na Play Store. A compilação deve ocorrer nos servidores do GitHub; não foi possível compilar localmente neste ambiente porque o Android SDK e o Gradle não estão disponíveis.
