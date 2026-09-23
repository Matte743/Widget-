# QS Widget per Nothing Phone (3)

Widget per la schermata Home con le impostazioni rapide, in stile Nothing OS:
icone e numeri a matrice di punti, nero/bianco con il rosso Nothing, tema chiaro e scuro automatici.

| Controllo | Cosa fa un tocco |
|---|---|
| **Luminosità** | Tocca la barra: il livello toccato si applica subito e sopra il widget si apre un pannello dove trascinare il dito. Tocca l'icona del sole per la luminosità adattiva. |
| **Volume** | Come la luminosità: tocca la barra e trascina il dito nel pannello. Tocca l'icona dell'altoparlante per silenziare. |
| **Wi-Fi, Dati, Bluetooth** | Con Shizuku si accendono e spengono direttamente. Senza Shizuku si apre il pannello di sistema. |
| **Hotspot** | Apre la pagina dell'hotspot nelle Impostazioni. |
| **Torcia** | Accende e spegne il flash posteriore. |
| **Glyph** | Accende tutti i LED della Glyph Matrix sul retro come torcia. Si spegne dal widget o dalla notifica. |

I widget di Android ricevono solo tocchi e non possono seguire il dito che scorre.
Per questo le barre aprono un pannello a comparsa con due slider a punti: trascini, e ogni punto superato dà un piccolo tick.
Il pannello si chiude toccando fuori o dopo 5 secondi senza tocchi.

Ogni tocco dà un feedback aptico breve: doppio impulso quando accendi, impulso leggero quando spegni, tick sulle barre.
Il feedback si può disattivare dall'app.

## Installazione

1. Dal telefono apri la pagina **Releases** di questo repository e scarica `QS-Widget.apk` dalla release “QS Widget (ultima build)”.
2. Apri il file scaricato. Se Android lo chiede, consenti al browser o a File di installare app sconosciute.
3. Se Play Protect mostra un avviso, tocca **Altri dettagli** e poi **Installa comunque**. L'app non è sul Play Store, per questo compare l'avviso.
4. Apri **QS Widget** e concedi i permessi:
   - **Modifica impostazioni di sistema**, per la luminosità.
   - **Dispositivi nelle vicinanze**, per il Bluetooth.
   - **Notifiche**, per il tasto “Spegni” della torcia Glyph.
5. Tocca **Aggiungi alla Home**. In alternativa tieni premuto su uno spazio vuoto della Home, tocca **Widget** e cerca “QS Widget”.

Ogni nuova build ha la stessa firma, quindi si installa sopra la precedente senza perdere le impostazioni.

## Perché Wi-Fi, dati e Bluetooth aprono un pannello

Da Android 10 le app normali non possono accendere o spegnere il Wi-Fi.
Da Android 13 non possono farlo nemmeno con il Bluetooth, e i dati mobili e l'hotspot sono riservati alle app di sistema.
Senza Shizuku il widget apre quindi il pannello di sistema, dove basta un altro tocco.

### Modalità Shizuku, facoltativa

[Shizuku](https://shizuku.rikka.app/) permette al widget di usare i comandi della shell di Android, come farebbe ADB.
Con Shizuku, Wi-Fi, dati e Bluetooth si commutano con un solo tocco.

1. Installa **Shizuku** dal Play Store.
2. Attiva le **Opzioni sviluppatore**: Impostazioni › Info sul telefono › tocca 7 volte **Numero build**.
3. Apri Shizuku e avvialo con **Debug wireless**, seguendo le istruzioni nell'app.
4. Apri QS Widget, concedi l'autorizzazione Shizuku e attiva **Usa Shizuku per Wi-Fi, dati e Bluetooth**.

Dopo ogni riavvio del telefono Shizuku va riavviato dall'app Shizuku. Finché non è attivo, il widget torna ad aprire i pannelli.
L'hotspot apre sempre la pagina delle Impostazioni, perché Android non offre un comando affidabile per attivarlo.

## Torcia Glyph

La torcia Glyph usa il [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) ufficiale di Nothing.
Funziona su Phone (3) e Phone (4a) Pro.
Mentre è accesa resta una notifica, perché la matrice rimane illuminata solo finché l'app è attiva.
Se usi un Glyph Toy con il pulsante Glyph, il Toy ha la precedenza sulla torcia.

## Compilare da sorgente

Ogni push su GitHub compila l'APK con GitHub Actions e aggiorna la release `apk-latest`.
Per compilare in locale serve Android Studio, oppure JDK 17 e Android SDK 36:

```sh
./gradlew assembleRelease
```

L'APK si trova in `app/build/outputs/apk/release/`.

La licenza del Glyph Matrix SDK non permette di ridistribuirlo, quindi non è nel repository.
La build lo scarica dal repository ufficiale di Nothing al primo avvio.

La chiave di firma `app/widget.keystore` è inclusa apposta, così ogni build si aggiorna sopra la precedente.
È pensata per uso personale. Se vuoi distribuire l'app ad altri, generane una tua e non pubblicarla.
