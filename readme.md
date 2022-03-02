# Kindling

A standalone collection of utilities to help [Ignition](https://inductiveautomation.com/) users. Features various tools
to help work with Ignition's custom data export formats.

## Tools

### Thread Viewer

Parses Ignition thread dump .json files (generated by all Ignition mechanisms since 8.1.10).

### IDB Viewer

Opens Ignition .idb files and displays a list of tables and allows arbitrary SQL queries to be executed.

If the file is detected as an Ignition log file, opens a custom view automatically. Right-click the tab to switch
between IDB views.

### Wrapper Log Viewer

Open one (or multiple) wrapper.log files. If the output format is Ignition's default, they will be automatically parsed
and presented in the same log view used for system logs. If multiple files are selected, an attempt will be made to
sequence them and present as a single view.

### Backup Explorer

Opens an Ignition Gateway backup (`.gwbk`) file. Allows you to export individual projects contained within, and view
some other information about the system. Can also execute queries against the .IDB file contained within the backup.

## Store and Forward Cache Viewer

Opens the [HSQLDB](http://hsqldb.org/) file that contains the Store and Forward disk cache. Attempts to parse the
Java-serialized data within into its object representation. If unable to deserialize (e.g. due to a missing class),
falls back to a string explanation of the serialized data.

Note: If you encounter any issues with missing classes, please file an issue. 

## Usage

Download the hosted installer from [JDeploy](https://www.jdeploy.com/~ignition-kindling) here. These installers allow
for auto-updating upon launch.

## Development

Kindling uses Java Swing as a GUI framework, but is written almost exclusively in Kotlin, an alternate JVM language.
Gradle is used as the build tool, and will automatically download the appropriate Gradle and Java version (via the
Gradle wrapper). Most IDEs (Eclipse, IntelliJ) should figure out the project structure automatically. You can directly
run the main class in your IDE ([MainPanel](src/main/kotlin/io/github/paulgriffith/MainPanel.kt)), or you can run the
application via`./gradlew run` at the command line.

## Contribution

Contributions of any kind (additional tools, polish to existing tools) are welcome.

## Releases

New tags pushed to Github will automatically trigger an action-based deployment of the given version, which will trigger
JDeploy to fetch the new version upon next launch.

## Acknowledgements

- [BoxIcons](https://github.com/atisawd/boxicons)
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf)
- [SerializationDumper](https://github.com/NickstaDB/SerializationDumper)
- [JDeploy](https://www.jdeploy.com/)

## Disclaimer

This is **not** an official Inductive Automation product and is not affiliated with, supported by, maintained by, or
otherwise associated with Inductive Automation in any way. This software is provided with no warranty.
