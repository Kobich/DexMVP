# Possible Changes and Build Notes

Этот файл фиксирует, что можно улучшить под рабочие требования, и почему текущая схема с remote Compose не ломается.

## 1. Что сейчас уже есть

Проект уже умеет:

- запускать Android host app без root/admin прав;
- выбирать адрес server в UI;
- работать с IP-адресом server, например `http://10.0.2.2:8080`;
- получать manifest с Ktor server;
- скачивать remote APK artifact;
- проверять SHA-256;
- сохранять artifact во внутреннее хранилище;
- делать artifact read-only перед загрузкой;
- грузить entry point через `DexClassLoader`;
- запускать output-фичи через `RemoteFeature`;
- открывать remote Compose-фичи через `RemoteComposeFeature`;
- переключаться между несколькими фичами из одного remote artifact.

## 2. Что не полностью совпадает с рабочими требованиями

### Artifact format

В требованиях написано:

```text
Отправка модулей Компоненту A в виде DEX-файлов
```

Сейчас server отдает APK:

```text
remote-module-debug.apk
```

Технически это рабочий DEX-контейнер, потому что внутри APK есть `classes.dex`, а `DexClassLoader` умеет грузить `.apk`/`.jar` с DEX внутри.

Если проверяющий строго требует именно `.dex`, можно доработать сборку remote artifact:

- собирать `.jar` или `.dex`;
- отдавать его с server;
- грузить его тем же `DexClassLoader`.

Практически для Android MVP APK-контейнер удобнее, потому что его собирает Android Gradle Plugin.

### Modules vs features

Сейчас один remote APK содержит несколько фич:

```text
remote-module-debug.apk
  HelloRemoteFeature
  CounterComposeFeature
  ProfileCardComposeFeature
  ChecklistComposeFeature
```

Требование говорит:

```text
Возможность переключения между загруженными модулями
```

Строже будет сделать несколько отдельных artifacts:

```text
remote-feature-output.apk
remote-feature-counter.apk
remote-feature-profile.apk
remote-feature-checklist.apk
```

Тогда host будет реально скачивать и переключать разные модули, а не разные entry point внутри одного APK.

## 3. Что стоит доработать следующим шагом

### 3.1. Manifest со списком модулей

Сейчас manifest описывает один artifact и список features внутри него:

```json
{
  "moduleId": "hello",
  "artifactUrl": ".../hello/1/artifact",
  "sha256": "...",
  "features": []
}
```

Лучше под требования:

```json
{
  "modules": [
    {
      "moduleId": "counter",
      "version": 1,
      "kind": "compose",
      "entryPoint": "com.engboost.remote.counter.CounterComposeFeature",
      "artifactUrl": "http://10.0.2.2:8080/api/v1/modules/counter/1/artifact",
      "sha256": "..."
    },
    {
      "moduleId": "profile",
      "version": 1,
      "kind": "compose",
      "entryPoint": "com.engboost.remote.profile.ProfileCardComposeFeature",
      "artifactUrl": "http://10.0.2.2:8080/api/v1/modules/profile/1/artifact",
      "sha256": "..."
    }
  ]
}
```

### 3.2. Несколько Gradle remote modules

Добавить модули:

```text
remote-features/output
remote-features/counter
remote-features/profile
remote-features/checklist
```

Каждый собирается в свой APK.

### 3.3. UI состояния модулей

Показывать для каждого модуля:

```text
available
downloaded
loaded
failed
update available
```

Кнопки:

```text
Download
Open
Delete cache
```

### 3.4. Кеш и версии

Host должен уметь:

- открыть уже скачанный module без повторного download;
- понять, что version изменилась;
- не запускать artifact с неправильным hash;
- удалить битый artifact;
- при ошибке оставить предыдущую рабочую версию.

### 3.5. Подпись manifest/artifact

SHA-256 защищает от случайной порчи и несоответствия файла manifest. Для trust model лучше добавить подпись:

- server подписывает manifest;
- host содержит public key;
- host проверяет подпись manifest до скачивания artifact.

## 4. Почему текущий remote Compose не ломается

Главная проблема remote Compose через `DexClassLoader` - нельзя допустить, чтобы remote APK упаковал свои копии:

```text
RemoteFeature
RemoteComposeFeature
Compose runtime
Compose UI
Material
```

Если remote APK упакует эти классы внутрь себя, может появиться конфликт classloader identity.

Пример проблемы:

```text
host видит com.engboost.remoteapi.RemoteComposeFeature из host classloader
remote APK видит com.engboost.remoteapi.RemoteComposeFeature из DexClassLoader
```

Для JVM/Android это могут быть разные типы, даже если package и class name одинаковые.

Тогда cast ломается:

```kotlin
instance as RemoteComposeFeature
```

или Compose начинает ломаться на runtime-классах.

## 5. Как проект сейчас этого избегает

### 5.1. `api` module публикует Compose runtime наружу

В `feature:remote-execution:api`:

```kotlin
dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
}
```

Почему именно `api`, а не `implementation`:

- `RemoteComposeFeature` содержит `@Composable`;
- потребители API должны видеть Compose runtime types;
- `impl` и `app`, которые зависят от API, получают нужный compile/runtime classpath;
- host app содержит общий API и Compose runtime.

Если поставить `implementation`, downstream-модули могут не получить нужные Compose API на compile classpath.

### 5.2. `remote-module` использует `compileOnly`

В `remote-module`:

```kotlin
dependencies {
    compileOnly(project(":feature:remote-execution:api"))
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtime)
    compileOnly(libs.androidx.compose.foundation)
    compileOnly(libs.androidx.compose.ui)
    compileOnly(libs.androidx.compose.material3)
}
```

Почему `compileOnly`:

- remote code должен компилироваться против этих классов;
- но remote APK не должен паковать эти классы внутрь себя;
- во время выполнения эти классы приходят из parent classloader host app.

`DexClassLoader` создается так:

```kotlin
DexClassLoader(
    artifact.absolutePath,
    optimizedDir.absolutePath,
    null,
    context.classLoader,
)
```

`context.classLoader` - parent. Поэтому remote APK может использовать классы, которые уже есть в host app.

### 5.3. `impl` добавляет Compose runtime в host APK

В `feature:remote-execution:impl`:

```kotlin
dependencies {
    implementation(project(":feature:remote-execution:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

Потому что host реально рисует Compose UI и должен содержать runtime.

## 6. Правило зависимостей

Для host side:

```text
api module:
  api(compose runtime)

impl module:
  implementation(api module)
  implementation(compose runtime/ui/material)

app:
  implementation(impl module)
```

Для remote side:

```text
remote module:
  compileOnly(api module)
  compileOnly(compose runtime/ui/material)
```

Нельзя делать в remote module:

```kotlin
implementation(project(":feature:remote-execution:api"))
implementation(libs.androidx.compose.runtime)
implementation(libs.androidx.compose.material3)
```

Потому что тогда эти классы могут попасть в remote APK и начать конфликтовать с host.

## 7. Как проверить, что remote APK не тащит лишнее

Команда:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
jar tf remote-module\build\outputs\apk\debug\remote-module-debug.apk | rg "androidx/compose|com/engboost/remoteapi"
```

Ожидаемо:

```text
пустой вывод
```

Если команда выводит `androidx/compose/...` или `com/engboost/remoteapi/...`, значит remote APK тащит классы, которые должен брать из host.

## 8. Самое важное коротко

- Host должен содержать общий API и Compose runtime.
- Remote APK должен только компилироваться против API/Compose, но не паковать их.
- Поэтому в API используется `api(...)`.
- Поэтому в remote APK используется `compileOnly(...)`.
- `DexClassLoader` должен иметь parent `context.classLoader`.
- Тогда remote Compose видит те же классы, что и host, и cast/Content работают стабильнее.

