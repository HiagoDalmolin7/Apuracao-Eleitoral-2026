# Modernização de Gradle, zoom, testes e Android Lint

Este pacote usa o `MainActivity.kt` mais recente (bandeiras e áudios incluídos) e
adiciona testes às regras realmente chamadas em produção.

## Arquivos a substituir/adicionar no GitHub (todos os caminhos estão dentro do ZIP)

1. **Substituir** `app/src/main/java/br/com/apuracaoeleitoral/MainActivity.kt`.
2. **Adicionar** `app/src/main/java/br/com/apuracaoeleitoral/ElectionMath.kt`.
3. **Adicionar** `app/src/test/java/br/com/apuracaoeleitoral/ElectionMathTest.kt`.
4. **Substituir** `app/build.gradle.kts` (verifique o `versionCode` antes de copiar).
5. **Substituir** `build.gradle.kts` na raiz.
6. **Criar ou atualizar** `gradle.properties` na raiz. Preserve qualquer propriedade adicional própria do seu projeto.
7. **Substituir** `.github/workflows/build-apk.yml`.

Não altere os arquivos de áudio, as imagens, o `AndroidManifest.xml` ou o `settings.gradle.kts`.

## Como testar no GitHub

1. Envie todos os arquivos para a branch `main` e faça Commit changes.
2. A execução do workflow ocorre automaticamente no `push`, ou manualmente pela aba Actions.
3. Confira as etapas: **Testes unitários Kotlin**, **Android Lint**, **Gerar APK Debug** e **Verificar reutilização do Configuration Cache**.
4. Se todas passarem, baixe o artefato `ApuracaoEleitoral-APK-Debug`.
5. Confira `Relatorios-Testes-Lint-Gradle` para HTML de testes, XML e relatórios do Lint.

## Sobre as versões

- Mantidas as versões estáveis já usadas com sucesso: Gradle 9.8.2, AGP 9.4.1, Kotlin/Compose Compiler 2.4.21, Haze 2.0.1.
- Testes adicionados: JUnit 4.13.2.
- APK continua **Debug**, conforme solicitado.
- `versionCode=3`, `versionName=1.2.0` são valores sugeridos: não diminua o `versionCode` se sua versão instalada for maior.

## Limitações e diagnóstico

- Este ambiente não possui o Android SDK/Gradle completo: a compilação Android e o Lint **não foram executados aqui**. O GitHub Actions será a validação definitiva.
- `--warning-mode all` revelará exatamente a origem dos recursos deprecados no Gradle. Não é possível garantir sua eliminação antes de observar esse novo log.
- O Configuration Cache está em modo estrito, sem ignorar erros. A última etapa repete `assembleDebug` para conferir a reutilização; procure `Reusing configuration cache.` no log.
- O `sdkmanager` pode continuar aparecendo internamente no próprio `setup-android@v4`, mesmo que o seu passo use Android CLI.
- Há alguns riscos de Lint para aplicativos Compose (traduções, APIs Android, ícones, permissões): erros identificados devem ser corrigidos no código, não suprimidos indiscriminadamente.
- Se o Android CLI instalado não reconhecer `platforms/android-37.2`, troque somente esse passo por `sdkmanager --install "platforms;android-37.2" "build-tools;36.0.0"` que já funcionava no log anterior.

## Testes matemáticos incluídos

- Projeção 20%, 100%, razão proporcional, percentuais inválidos.
- Vitória irreversível ou não, empate, limite de diferença exato, resultado estadual ignorado, eleitorado e seções inválidos, discrepância em totais oficiais.

As regras de projeção e de vitória do app agora chamam as funções da classe `ElectionMath`, que é exatamente o que os testes verificam.
