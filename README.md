# qupath-extension-guided-tutorials

Step-by-step walkthroughs of QuPath's own documentation tutorials, **inside QuPath**. A small panel
tells you what to do, outlines the control to click, notices when you've done it, and checks the
result before moving on.

- **You do every click.** The extension explains, points and checks. It never changes your image data.
- **No AI, no account, no network requests.** "Open download page" hands the link to your browser.

## Tutorials

| Menu | Follows | Sample slide |
|---|---|---|
| Extensions ▸ Guided tutorials ▸ Cell detection (Ki67) | [Cell detection](https://qupath.readthedocs.io/en/stable/docs/tutorials/cell_detection.html) | [`OS-2.ndpi`](https://openslide.cs.cmu.edu/download/openslide-testdata/Hamamatsu/OS-2.ndpi) (~977 MB), or your own slide |

## Build & install

Requires a full JDK 25 (`javac -version` → 25.x) and QuPath 0.7.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat test shadowJar
```

Copy `build/libs/qupath-extension-guided-tutorials-0.1.0-all.jar` into your QuPath extensions folder
(Windows: `%USERPROFILE%\QuPath\v0.7\extensions\`), deleting any older version first, then restart
QuPath.

## How it works

- `engine/`: pure: `Tutorial` → `Step`s, each with a check over a `TutorialState` snapshot, and
  `TutorialProgress` (when to auto-advance). Unit-tested.
- `tutorials/`: the tutorial scripts. Checks are static pure functions, unit-tested.
- `support/`: pure node-tree walks (`FieldFinder`, `MenuFinder`) and `PositivePercent`.
- `live/`: the QuPath glue: `StateReader` builds the snapshot, `TutorialRunner` polls twice a second,
  `Highlighter` draws outlines, `TutorialPanel` is the window. Verified by hand in QuPath.
