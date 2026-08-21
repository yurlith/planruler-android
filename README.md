# PlanRuler Unified

[![License: GPL v3+](https://img.shields.io/badge/License-GPLv3%2B-blue.svg)](LICENSE)
[![Android CI](https://github.com/yurlith/planruler-android/actions/workflows/android.yml/badge.svg)](https://github.com/yurlith/planruler-android/actions/workflows/android.yml)

PlanRuler is an offline-first Android application for installers who need to
measure PDF and image plans, calculate materials and workshop geometry, and
manage projects without an engineering-style interface or cloud account.

The complete source for the Android application is published here for F-Droid
and independent review. The release website and direct APK download remain at
<https://yurlith.github.io/planruler/>.

Offline-first Android-приложение для измерений по PDF, PNG и JPEG. Это объединённая реализация на основе модели/engine PlanRuler и Android document gateway PlanMeasure.

## Что работает

- проекты и повторное открытие;
- безопасное удаление проектов в локальную корзину, восстановление и отдельное
  необратимое удаление;
- локальные профили с PIN, клиенты и заказы CRM без сервера; чувствительные поля
  шифруются ключом Android Keystore;
- переносимый зашифрованный `.planruler-backup` через системный выбор файла;
- предварительный теплотехнический расчёт с протоколом формул, гидравлика
  Darcy-Weisbach и общий с чертежом контур тёплого пола;
- SAF-импорт PDF/PNG/JPEG с persistable URI permission;
- инспектор данных фото: EXIF-оптика, полный XMP/extended-XMP, GDepth и
  concatenated Dynamic Depth decoder, единая метрическая карта независимо от
  формата вложенного JPEG/PNG/WebP/HEIF-растра (в пределах кодеков устройства),
- монтажная мастерская на одном экране: фланцевое смещение, контур узла,
  автоматический подбор DN/PN, три длины реза, сварные зазоры, болтовой круг,
  масса и цветной график раскроя хлыстов,
  8/16-bit precision, confidence и локальная статистика camera profile по median/MAD;
- многостраничный PDF, адаптивный рендер и LRU-cache;
- zoom/pan в отдельной системе координат viewport;
- ручная калибровка и масштабы PDF 1:20, 1:50, 1:100;
- длина, полилиния, площадь/периметр, угол, аннотация, счётчик;
- undo/redo, автосохранение, атомарный JSON и backup;
- CSV, versioned JSON и annotated PDF export с легендой, масштабом, датой и
  названием проекта; разделитель CSV и состав PDF задаются в настройках;
- польский, английский, немецкий, французский, итальянский и русский интерфейс
  с восстановлением выбора после перезапуска;
- выбор и drag объектов/вершин, snapping с guide lines и редактор свойств
  (категория, подкатегория, материал, диаметр, размер, количество, запас, слой);
- слои: видимость, блокировка, переименование и перенос измерений;
- сохраняемые рабочие шаблоны, 15 стартовых шаблонов, активный шаблон до переключения
  и обновление свойств существующих замеров без изменения геометрии;
- точная длина с H/V-ограничениями и повтор последнего замера;
- итоги по шаблону, материалу, слою, странице и проекту с единым расчётом
  количества/запаса на экране, в CSV и PDF;
- tiled-рендер видимой области при глубоком zoom (PDF и изображения);
- ревизии страниц: сохранение старого источника, ручное совмещение по 2/3 точкам,
  регулируемое наложение, перенос измерений как непроверенных копий, фильтры и
  журнал ревизий в JSON/CSV/PDF;
- современная Material 3 дизайн-система, контрастный холст, цветовые коды
  инструментов и анимированные переходы;
- отдельные real-SAF Compose journeys для PDF, PNG, rotated JPEG и corrupt PDF.

## Сборка на Windows

Путь содержит кириллицу. Android Gradle Plugin допускается через `android.overridePathCheck=true`, но JUnit worker требует ASCII-путь:

```powershell
subst R: "C:\path\to\planruler-android"
Set-Location R:\
.\gradlew.bat test
.\gradlew.bat lint assembleDebug assembleRelease
```

Если batch-файл не читает путь, используйте:

```powershell
java '-Dfile.encoding=UTF-8' -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test
```

Нужны JDK 17+ и Android SDK 36. Release AAB/APK подписываются локальным Play upload key
из игнорируемого `keystore.properties`; ключи и пароли не хранятся в репозитории.

Статус: `1.5.1`, публичный open-source release. Предварительные HVAC-расчёты
не являются заявлением соответствия SIA/DIN: нормативные профили заблокированы
до сверки по лицензированным текстам и подписанным контрольным примерам.
Актуальные проверки и ограничения описаны в `docs/TEST_REPORT.md` и
`docs/LOCAL_HVAC_CRM_IMPLEMENTATION_2026-08-14.md`.

## Open-source and F-Droid build

The project is licensed under `GPL-3.0-or-later`. Bundled PlanRuler artwork is
distributed under the same license; third-party dependencies retain their own
compatible licences.

The app has no advertising, analytics or Google Play Services dependency and
does not request Internet access. An unsigned release APK suitable for F-Droid
signing can be built with JDK 17 and Android SDK 36:

```bash
./gradlew :app:assembleRelease
```

Upstream store metadata is under `fastlane/metadata/android/en-US/`. The
proposed official F-Droid build recipe and RFP text are under `fdroid/`.
# Hardening snapshot (2026-07-25)

Current quality status: **Prototype — final RC verification pending**.

Real SAF E2E, process-death restoration, separate PDF/JPEG/corrupt journeys,
independent export validation and measured stress tests run on API 26 and API
35. Human field/external-viewer acceptance remains. See
`docs/RC_HARDENING_REPORT.md`, `docs/DEVICE_TEST_MATRIX.md`, and
`docs/KNOWN_LIMITATIONS.md` for the evidence and remaining RC blockers.

# UI/UX specification

`docs/UX_SCREEN_MAP.md` — граф экранов, анатомия каждого экрана, состояния,
машина состояний жестов и адаптивные правила.
`docs/UI_DESIGN_SYSTEM.md` — токены, шесть тем, профили касания, контракты
компонентов, доступность и реестр тестовых тегов.
