# L2 KVADRA Test v0.4

Android-only diagnostic observer for Pretech 1102 / KVADRA OS / L2Meta Interlude.

## What it does
- Requests MediaProjection screen capture permission
- Continues capturing while the user switches to L2Meta
- Samples HP and MP bar colors using coordinates calibrated from a 1600x960 screenshot
- Heuristically detects the red target bar
- Runs ArcherDecisionEngine with HP 40%, MP 35%, basic attacks only
- Saves the latest diagnostic observation locally for display after returning to the app
- Does not automatically control L2Meta or upload screenshots

## How to test
1. Install the APK from GitHub Actions artifacts.
2. Tap **Начать наблюдение** and select **entire screen** if prompted.
3. Switch to L2Meta for 10-20 seconds, select a monster, then return.
4. Tap **Показать последний результат**. Note HP/MP percentages and target state.
5. Tap **Остановить наблюдение**.

The HP/MP estimates are experimental; UI scaling, overlays, and colors may cause false readings.
The app does not yet display a floating overlay or perform OCR.
