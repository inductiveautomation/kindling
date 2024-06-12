# Kindling

<img src="src/main/resources/logo.svg" width="250" alt="Kindling">

A standalone desktop application targeted to advanced [Ignition](https://inductiveautomation.com/) users.
Features various tools to read and access Ignition's myriad data export formats.

## Tools

### Thread Viewer

Parses Ignition thread dump files, in JSON or plain text format. Multiple thread dumps from the same system can be
opened at once and will be automatically aggregated together.

### IDB Viewer

Opens Ignition .idb files (SQLite DBs) and displays a list of tables and allows arbitrary SQL queries to be executed.

Has special handling for:

- Metrics files
- System logs
- Images in the configuration DB

### Log Viewer

Open one (or multiple) wrapper.log files. If the output format is Ignition's default, they will be automatically parsed
and presented in the same log view used for system logs. If multiple files are selected, an attempt will be made to
sequence them and present as a single view.

### Archive Explorer

Opens a zip file (including Ignition files like `.gwbk` or `.modl`). Allows opening other tools against the files within
the zip, including the .idb files in a gateway backup, or the files in a diagnostics bundle.

### Store and Forward Cache Viewer

Opens the [HSQLDB](http://hsqldb.org/) file that contains the Store and Forward disk cache. Attempts to parse the
Java-serialized data within into its object representation. If unable to deserialize (e.g. due to a missing class),
falls back to a string explanation of the serialized data.

Note: If you encounter any issues with missing classes, please file an issue.

### Alarm Cache Viewer

Opens the Java serialized `.alarms_$timestamp` files Ignition uses to persist alarm information between Gateway
restarts.
Only works for alarm caches from 8.1.20 and up gateways.

Note: If you encounter any issues with missing classes, please file an issue.

### Gateway Network Diagram Viewer

Validates a Gateway Network Diagram, as exported from the Gateway webpage (see instructions below). You can load from a
.json or .txt file on disk, or paste directly from the clipboard. Click the 'View Diagram in Browser' button to launch
the diagram visualization in a local web browser.

#### To Obtain a GAN Diagram JSON (8.1.37 and below)

1. Set the `gateway.routes.status.GanRoutes` logger to DEBUG.
2. Return to the gateway network status page and view the live graph.
3. Return to the logs and copy the JSON to the clipboard or save it to a local file.

### XML Viewer

Opens Ignition XML files in a simple text view.

Has special handling for:

- Logback configuration files, with a special interactive editor
- Store and Forward quarantine files, with an _attempt_ made to deserialize any Java-serialized data within

## Usage

1. Download the installer for your OS from the Downloads
   page: https://inductiveautomation.github.io/kindling/download.html
2. Run the Kindling application.
3. Open a supported file - either drag and drop directly onto the application window, click the `+` icon in the tab
   strip, or select a tool to open from the menubar.

Preferences are stored in `~/.kindling/preferences.json` and can be modified within the application from the menu bar.

## Development

Kindling uses Java Swing as a GUI framework, but is written almost exclusively in Kotlin, an alternate JVM language.
Gradle is used as the build tool, and will automatically download the appropriate Gradle and JDK version (via the
Gradle wrapper). Most IDEs (Eclipse, IntelliJ) should figure out the project structure automatically. You can directly
run the main class in your IDE ([`MainPanel`](src/main/kotlin/io/github/inductiveautomation/kindling/MainPanel.kt)), or
you can run the application via`./gradlew run` at the command line.

## Contribution

Contributions of any kind (additional tools, polish to existing tools, test files) are welcome.

## Acknowledgements

- [BoxIcons](https://github.com/atisawd/boxicons)
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf)
- [SerializationDumper](https://github.com/NickstaDB/SerializationDumper)
- [Hydraulic Conveyor](https://www.hydraulic.software/)
- [Terai Atsuhiro](https://java-swing-tips.blogspot.com/)

> [!WARNING]
> Kindling is **not** an official Inductive Automation product and is provided as-is with no warranty. 
