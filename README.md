# Photo Editor

Photo Editor és un editor de fotografies Android FOSS basat en el motor de
[PhotonCamera](https://github.com/bjzhou/PhotonCamera), amb una interfície i un
flux d’edició inspirats en Snapseed.

## Funcions

- Ajustos de llum i color, detalls, corbes RGB, balanç de blancs i HSL.
- Crop, gir, perspectiva, expand, selecció local, pinzell i healing.
- Lens Blur, Vignette, Grain, Bloom, HDR Scape, Drama, Vintage, Retrolux,
  Grunge, Noir, Black & White, Portrait, Face Enhance i Head Pose.
- Text amb estils Plain, Bold, Outline, Neon, Stamp i Typewriter.
- LUTs locals `.cube` i `.plut`, presets inclosos i importació de LUTs propis.
- Edició no destructiva amb receptes, undo/redo, comparació abans/després,
  exportació JPEG i compartir local.

Totes les operacions de l’editor es processen al dispositiu. El flavor `default`
és la distribució FOSS per a F-Droid i no inclou Google Play Services, Play
Billing, Firebase, Bugly ni Crashlytics. Tampoc declara permisos de xarxa.

## Compilar

```sh
./gradlew assembleDefaultRelease
```

L’APK unsigned queda a:

```text
app/build/outputs/apk/default/release/app-default-release-unsigned.apk
```

El workflow de [GitHub Actions](.github/workflows/android-build.yml) executa
els tests de l’editor i els parsers LUT, compila l’APK FOSS i publica un
GitHub Release automàtic per cada tag `v*`.

Consulta [FOSS_BUILD.md](FOSS_BUILD.md) per als detalls de la compilació,
l’auditoria de permisos i les notes de reproductibilitat.

## Llicència

El codi de Photo Editor es distribueix sota [Apache License 2.0](LICENSE),
subjecte a les llicències dels components, models, fonts i LUTs de tercers
incloses al repositori.
