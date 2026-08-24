# RoadRecord

A RoadRecord magyar nyelvű, telefonra optimalizált Android alkalmazás munkaidő, utak, GPS-pontok, helyszínek, kereset és elszámolási időszakok lokális nyilvántartására. A legfontosabb művelet mindig a **Ma** képernyőn jelenik meg.

## Technológia és architektúra

- Kotlin, Jetpack Compose, Material 3, Navigation-elvű képernyőstruktúra
- Room + Repository + ViewModel
- Fused Location Provider, energiatakarékos (20 s / 50 m) mintavétel
- aktív munkanap alatt foreground service és állandó értesítés
- boot receiver az aktív nap visszaállításához
- FileProvider-alapú CSV/JSON export és megosztás
- a térkép- és jelentésküldő szolgáltatás úgy bővíthető, hogy később külső provider/backend köthető mögé

## Build

Android Studio Hedgehog vagy újabb, JDK 17 és Android SDK 35 szükséges.

```powershell
./gradlew assembleDebug
./gradlew assembleRelease
```

A debug APK helye: `app/build/outputs/apk/debug/app-debug.apk`. A release APK helye: `app/build/outputs/apk/release/app-release-unsigned.apk`. Publikáláshoz az APK-t alá kell írni, majd másolni:

```text
download-site/apk/roadrecord-v0.1.0.apk
download-site/apk/roadrecord-latest.apk
```

## Nethely publikálás

A letöltőoldal hivatalos címe: **https://roadrecord.widor.nhely.hu/**

Az oldal GitHub Pages-en fut érvényes HTTPS-tanúsítvánnyal. A Nethely DNS-ben a `roadrecord.widor.nhely.hu` CNAME rekord a `sztankod.github.io` címre mutat; a Nethely `/roadrecord/` könyvtára tartalékpéldányként továbbra is automatikusan frissül.

A `download-site` teljes tartalma közvetlenül feltölthető a domainhez tartozó publikus könyvtárba; Node.js és backend nem szükséges. Az APK-fájlokat a fenti neveken kell az `apk` könyvtárba tenni. A verzió, dátum, fájlméret és változáslista az `index.html` fájlban szerkeszthető.

### Automatikus GitHub → Nethely frissítés

A `.github/workflows/release-and-deploy.yml` workflow minden `master` pushkor, minden `v*` tag pushakor és kézi indításkor elkészíti az APK-t, létrehozza a verziózott és `roadrecord-latest.apk` fájlt, majd a kizárólag `/roadrecord/` mappára korlátozott FTP-fiókon keresztül feltölti az oldalt. A GitHub repository **Settings → Secrets and variables → Actions** részén ezek a titkok szükségesek:

- `NETHELY_FTP_SERVER`
- `NETHELY_FTP_USERNAME`
- `NETHELY_FTP_PASSWORD`

Új kiadás indítása például: `git tag v0.1.1`, majd `git push origin v0.1.1`. A repositoryhoz előbb GitHub remote-ot kell kapcsolni.

## Jogosultságok

Az app hely-, háttérbeli hely-, értesítés-, kamera- és boot jogosultságot deklarál. A hely és értesítés engedélyét csak az útvonal funkció indításakor kéri; a kamera/média jogosultságot a későbbi fotókezelő kérheti. Android 11+ alatt a háttérbeli helyhozzáférést a rendszer beállítási oldalán kell külön engedélyezni.

## Adatbázis

Room táblák: `WorkPeriod`, `WorkDay`, `WorkEvent`, `Trip`, `GpsPoint`, `LocationPlace`, `DailyPlacePlan`, `PlaceVisit`, `AppSettings`. Foreign keyek védik a nap–esemény–út–GPS és nap–helyszín–látogatás kapcsolatokat. A `PlaceVisit` már tárolja az előző/következő helyszínt, érkezést, távozást, menetidőt és távolságot a későbbi tanuló ajánlórendszerhez.

Kapukódot és fotót nem szabad logolni. A fájlok az alkalmazás privát tárhelyére kerülnek; a helyadat csak explicit exporttal hagyja el a készüléket. Automatikus e-mailhez nem kerül SMTP-jelszó az appba: biztonságos HTTPS backend szükséges.

Debug buildben a Beállítások / Demo adatok menüpont 10 munkanapot, 6 helyszínt, utakat és GPS-pontokat készít.
