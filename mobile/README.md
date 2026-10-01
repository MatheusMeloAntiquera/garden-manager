# Garden Manager - Mobile

Aplicativo Android desenvolvido em Kotlin com Jetpack Compose. O design das telas está em [`design/`](design/README.md).

## Estado atual

- Entrar, criar conta e sair, com renovação automática da sessão (refresh token).
- Perfil com os dados da conta e escolha de tema (do sistema, claro ou escuro).
- Abas Ambientes, Plantas e Agenda ainda são telas provisórias ("Em breve").

## Stack

Kotlin, Jetpack Compose (Material 3), Navigation Compose, Hilt, Retrofit + OkHttp + kotlinx.serialization, DataStore e Tink (os tokens ficam criptografados com uma chave do Android Keystore). Arquitetura MVVM: `ViewModel` com `StateFlow` e repositórios. As versões ficam em [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Estrutura

```
app/src/main/java/com/matheusantiquera/gardenmanager/
├── core/
│   ├── designsystem/   # tema "Musgo" (claro e escuro), tipografia Manrope e componentes
│   ├── network/        # clientes HTTP, token no header, renovação em 401, erros da API
│   ├── datastore/      # tokens criptografados e preferências (tema)
│   ├── session/        # estado da sessão (carregando, logado, deslogado)
│   ├── di/             # módulos do Hilt
│   └── ui/             # texto de tela (UiText) e máscara de data
├── data/auth/          # AuthApi/UserApi, DTOs e AuthRepository
├── feature/            # uma pasta por funcionalidade (auth, profile, placeholder)
└── navigation/         # rotas e grafos (autenticação x app logado)
```

## Requisitos

- [Android Studio](https://developer.android.com/studio) (traz o JDK 21 e o SDK).
- JDK 17 ou superior para rodar o Gradle pela linha de comando. O JDK embutido no Android Studio (`<pasta do Studio>/jbr`) serve.
- SDK Android com a plataforma 37 (o Gradle instala sozinho se as licenças já foram aceitas).

## Rodando

1. Suba a API local (veja o [README do backend](../backend/README.md)): `make db-up`, `make migrate-up` e `make run`, que sobe em `http://localhost:8080`.

2. Com o celular conectado por USB (depuração USB ligada), faça o app enxergar o `localhost` do computador:

   ```bash
   adb reverse tcp:8080 tcp:8080
   ```

3. Instale o app:

   ```bash
   ./gradlew installDebug
   ```

   Ou gere só o APK, em `app/build/outputs/apk/debug/app-debug.apk`:

   ```bash
   ./gradlew assembleDebug
   ```

### Sem USB (APK copiado para o celular)

O celular e o computador precisam estar na mesma rede Wi-Fi. Aponte o build de debug para o IP do computador, em `mobile/local.properties` (arquivo fora do git), e gere o APK:

```properties
apiBaseUrl=http://192.168.0.11:8080/api/v1/
```

Você também pode passar `-PapiBaseUrl=...` ao `./gradlew assembleDebug`. Se o computador mudar de IP, gere o APK de novo. No Windows, libere a porta 8080 no firewall para a rede privada.

Se o `JAVA_HOME` do seu sistema for anterior ao JDK 17, aponte-o para o JDK do Android Studio antes de rodar o `./gradlew`.

No build de **debug**, a API é `http://localhost:8080/api/v1/`, a menos que você configure outra URL (HTTP sem TLS é liberado só no debug). A URL do build de **release** está como placeholder em `app/build.gradle.kts` e precisa ser trocada quando a API for publicada.

## Testes

```bash
./gradlew test
```

## Licenças

A fonte Manrope é distribuída sob a SIL Open Font License ([`licenses/Manrope-OFL.txt`](licenses/Manrope-OFL.txt)).
