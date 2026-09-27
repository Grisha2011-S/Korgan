# Инструкция: Сборка APK, смена иконки (аватарки) и названия приложения

---

## 1. Как собрать проект в APK-файл

### Способ А: Через графический интерфейс Android Studio (Рекомендуется)
1. Откройте проект в **Android Studio**.
2. В верхнем меню выберите:
   **`Build`** ➔ **`Build Bundle(s) / APK(s)`** ➔ **`Build APK(s)`**.
3. Подождите 1–2 минуты, пока завершится процесс компиляции (Gradle Build).
4. В правом нижнем углу появится всплывающее уведомление: **«APK(s) generated successfully»**.
5. Нажмите на синюю ссылку **«locate»** в уведомлении, чтобы открыть папку с готовым файлом `app-debug.apk`.
   *(Прямой путь к файлу: `app/build/outputs/apk/debug/app-debug.apk`)*.

---

### Способ Б: Через командную строку (Терминал)
Откройте терминал в корневой папке проекта и выполните команду:

* **Для сборки отладочной версии (Debug APK):**
  ```bash
  gradle assembleDebug
  ```
  Готовый APK появится по пути:
  `app/build/outputs/apk/debug/app-debug.apk`

* **Для сборки релизной версии (Release APK):**
  ```bash
  gradle assembleRelease
  ```
  Готовый APK появится по пути:
  `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 2. Как поменять название приложения

### 1. Название на рабочем столе и в системе Android:
Откройте файл:
📁 **`app/src/main/res/values/strings.xml`**

Измените текст внутри тега `<string name="app_name">`:
```xml
<resources>
    <string name="app_name">Мой Детектор</string>
</resources>
```

### 2. Название проекта в среде сборки:
Откройте файл:
📁 **`settings.gradle.kts`**
```kotlin
rootProject.name = "Мой Детектор"
```

А также в файле **`metadata.json`**:
```json
{
  "name": "Мой Детектор"
}
```

---

## 3. Как поменять иконку (аватарку) приложения

### Способ А: Через встроенный инструмент Android Studio Image Asset (Самый быстрый и правильный способ)
1. В левой панели проекта (Project) нажмите правой кнопкой мыши по папке **`app/src/main/res`**.
2. Выберите **`New`** ➔ **`Image Asset`**.
3. В открывшемся окне:
   - В поле **Icon Type** оставьте **Launcher Icons (Adaptive and Legacy)**.
   - В поле **Name** оставьте `ic_launcher`.
   - В секции **Foreground Layer** (Передний план):
     - В **Source Type** выберите **Image** и нажмите на иконку папки, чтобы выбрать вашу картинку (PNG, JPEG, WebP или SVG).
     - С помощью ползунка **Resize** подгоните размер картинки, чтобы она не обрезалась по краям.
   - В секции **Background Layer** (Задний план):
     - Выберите **Color** (цвет фона) или **Image** (текстура фона).
4. Нажмите **`Next`**, затем **`Finish`**. Android Studio сама сгенерирует все нужные размеры для экранов любых телефонов.

---

### Способ Б: Ручная замена файлов в проекте
Иконки приложения находятся в следующих файлах и папках:

1. **Адаптивные иконки для современных версий Android (Android 8.0+)**:
   - 📁 `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — конфигурация квадратной/скругленной иконки.
   - 📁 `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — конфигурация круглой иконки.
   - 📁 `app/src/main/res/drawable/ic_launcher_background.xml` — фон иконки (цвет или вектор).
   - 📁 `app/src/main/res/drawable/ic_launcher_foreground.xml` — передний план (рисунок / значок по центру).

2. **Растровые PNG-иконки для старых версий Android**:
   Замените файлы `ic_launcher.png` и `ic_launcher_round.png` в папках разных плотностей:
   - `app/src/main/res/mipmap-mdpi/` (48x48 px)
   - `app/src/main/res/mipmap-hdpi/` (72x72 px)
   - `app/src/main/res/mipmap-xhdpi/` (96x96 px)
   - `app/src/main/res/mipmap-xxhdpi/` (144x144 px)
   - `app/src/main/res/mipmap-xxxhdpi/` (192x192 px)

3. **Связь с манифестом приложения**:
   В файле 📁 **`app/src/main/AndroidManifest.xml`** прописаны ссылки:
   ```xml
   <application
       android:icon="@mipmap/ic_launcher"
       android:roundIcon="@mipmap/ic_launcher_round"
       android:label="@string/app_name"
       ...>
   ```

---

## 4. Как изменить уникальный ID приложения (Package Name / Application ID)

Если вы хотите установить приложение параллельно или опубликовать его:
Откройте файл:
📁 **`app/build.gradle.kts`**

Найдите блок `defaultConfig`:
```kotlin
android {
    namespace = "com.example" // Namespace для кода R оставляем прежним
    
    defaultConfig {
        applicationId = "com.mycompany.fallguard" // Уникальный ID вашего приложения
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}
```
