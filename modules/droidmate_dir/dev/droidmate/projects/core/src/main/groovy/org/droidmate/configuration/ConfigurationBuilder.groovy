// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.configuration

import ch.qos.logback.classic.Level
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.konradjamrozik.Resource
import com.konradjamrozik.ResourcePath
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.droidmate.misc.BuildConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

import static org.droidmate.logging.Markers.runData

/**
 * @see IConfigurationBuilder#build(java.lang.String [ ], java.nio.file.FileSystem)
 */
@Slf4j

class ConfigurationBuilder implements IConfigurationBuilder
{
/**
 * <p>
 * <i> --- This doc was last reviewed on 14 Sep 2013.</i>
 * </p><p>
 *
 * Given command line arguments, returns {@link Configuration} instance, which is being injected to constructors
 * of most of DroidMate classes.
 * </p><p>
 * The arguments are parsed. If request to display help is detected, it is displayed on stdout and the JVM terminates.
 * Otherwise, the arguments are partially validated, e.g. resource files are checked for existence. Some properties
 * are constructed from the arguments (e.g. absolute file paths are constructed from parent path and relative path).
 * Finally, the result is returned.
 *
 * </p><p>
 * The underlying framework used for command line parsing is jCommander [1].
 *
 * </p><p>
 * [1]: http://jcommander.org/
 *
 * </p>
 *
 * @see Configuration
 */
  @Override
   Configuration build(String[] args, FileSystem fs = FileSystems.default) throws ConfigurationException
  {
    // Groovy bug: the @Memoized annotation causes cryptic compilation error if the args type is String[], so it is List instead.
    return memoizedBuildConfiguration(args as List, fs)
  }

  @Memoized
  private static Configuration memoizedBuildConfiguration(List<String> args, FileSystem fs)
  {
    log.debug("memoizedBuildConfiguration(args, fs)")

    assert args != null

    Configuration config = new Configuration(args as String[])

    JCommander jCommander
    jCommander = populateConfigurationWithArgs(args as String[], config)

    ifRequestedDisplayHelpAndExit(config, jCommander)
    assert !config.displayHelp: "DroidMate was instructed to display help. By now, it should have done it and exited, " +
      "but instead of exiting the code execution reached this assertion."

    config = bindAndValidate(config, fs)

    assert config != null
    return config
  }

   static JCommander populateConfigurationWithArgs(
    String[] args, Configuration config)
  {
    JCommander jCommander

    try
    {
      jCommander = new JCommander()
      jCommander.addObject(config)
      jCommander.parse(args)
    }
    catch (ParameterException e)
    {
      throw new ConfigurationException(e)
    }
    return jCommander
  }

  private static void ifRequestedDisplayHelpAndExit(Configuration config, JCommander jCommander)
  {
    if (config.displayHelp)
    {
      log.info("Detected request to display help. Displaying help & terminating.")

      jCommander.usage()

      System.exit(0)
    }
  }

  private static Configuration bindAndValidate(
    Configuration config, FileSystem fs) throws ConfigurationException
  {
    assert config != null

    try
    {
      setLogbackRootLoggerLoggingLevel(config)
      setupResourcesAndPaths(config, fs)
      validateExplorationSettings(config)
      normalizeAndroidApi(config)

      // This increment is done so each connected device will have its uiautomator-daemon reachable on a separate port.
      config.uiautomatorDaemonTcpPort += config.deviceIndex

    } catch (ConfigurationException e)
    {
      throw e
    }

    logConfigurationInEffect(config)

    assert config != null
    return config
  }

  static void normalizeAndroidApi(Configuration config) {
    if (config.androidApi == "19")
      config.androidApi = Configuration.api19
    if (config.androidApi == "23")
      config.androidApi = Configuration.api23
  }

  static void validateExplorationSettings(Configuration cfg)
  {
    validateExplorationStrategySettings(cfg)

    List<String> apkNames = Files.list(cfg.apksDirPath)
      .findAll {it.toString().endsWith(".apk")}
      .collect {Path it -> it.fileName.toString()}

    if (cfg.deployRawApks && ["inlined", "monitored"].any {apkNames.contains(it)})
      throw new ConfigurationException(
        "DroidMate was instructed to deploy raw apks, while the apks dir contains an apk " +
          "with 'inlined' or 'monitored' in its name. Please do not mix such apk with raw apks in one dir.\n" +
          "The searched apks dir path: ${cfg.apksDirPath.toAbsolutePath().toString()}")
  }

  private static void validateExplorationStrategySettings(Configuration cfg)
  {
    int settingsCount = widgetClickingStrategySettingsCount(cfg)

    if (settingsCount > 1)
      throw new ConfigurationException("Exploration strategy has been configured in too many different ways. Only one of the following expressions can be true:\n" +
        "alwaysClickFirstWidget: ${cfg.alwaysClickFirstWidget}\n" +
        "randomSeed != null: ${cfg.randomSeed != null}\n" +
        "widgetIndexes != null: ${cfg.widgetIndexes != null}")

    if (cfg.randomSeed == null)
    {
      cfg.randomSeed = new Random().nextLong()
      log.info("Generated random seed: $cfg.randomSeed")
    }
  }

  static int widgetClickingStrategySettingsCount(Configuration cfg)
  {
    return [
      cfg.alwaysClickFirstWidget,
      cfg.widgetIndexes?.size() > 0
    ].collect {it ? 1 : 0}.sum() as int
  }


  private static void setupResourcesAndPaths(Configuration cfg, FileSystem fs) throws ConfigurationException
  {
    cfg.uiautomatorDaemonJar = new Resource("uiautomator-daemon.jar").extractTo(fs.getPath(BuildConstants.dir_name_temp_extracted_resources))
    log.info("Using uiautomator-daemon.jar located at "+cfg.uiautomatorDaemonJar.toAbsolutePath().toString())

    cfg.uiautomator2DaemonApk = new Resource("uiautomator2-daemon.apk").extractTo(fs.getPath(BuildConstants.dir_name_temp_extracted_resources))
    log.info("Using uiautomator2-daemon.apk located at " + cfg.uiautomator2DaemonApk.toAbsolutePath().toString())

    cfg.uiautomator2DaemonTestApk = new Resource("uiautomator2-daemon-test.apk").extractTo(fs.getPath(BuildConstants.dir_name_temp_extracted_resources))
    log.info("Using uiautomator2-daemon-test.apk located at " + cfg.uiautomator2DaemonTestApk.toAbsolutePath().toString())

    cfg.monitorApkApi19 = new Resource(BuildConstants.monitor_api19_apk_name).extractTo(fs.getPath(BuildConstants.dir_name_temp_extracted_resources))
    log.info("Using $BuildConstants.monitor_api19_apk_name located at "+cfg.monitorApkApi19.toAbsolutePath().toString())

    cfg.monitorApkApi23 = new Resource(BuildConstants.monitor_api23_apk_name).extractTo(fs.getPath(BuildConstants.dir_name_temp_extracted_resources))
    log.info("Using $BuildConstants.monitor_api23_apk_name located at "+cfg.monitorApkApi23.toAbsolutePath().toString())

    
    cfg.droidmateOutputDirPath = fs.getPath(cfg.droidmateOutputDir)
    cfg.droidmateOutputReportDirPath = cfg.droidmateOutputDirPath.resolve(cfg.reportOutputSubdir)
    cfg.reportInputDirPath = fs.getPath(cfg.reportInputDir)
    cfg.reportOutputDirPath = fs.getPath(cfg.reportOutputDir)
    cfg.apksDirPath = cfg.useApkFixturesDir ? new ResourcePath(BuildConstants.apk_fixtures).path : fs.getPath(cfg.apksDirName.toString())

    if (cfg.apksDirPath.createDirIfNotExists())
      log.info("Created directory from which DroidMate will read input apks: "+cfg.apksDirPath.toAbsolutePath().toString())

    if (cfg.droidmateOutputDirPath.createDirIfNotExists())
      log.info("Created directory to which DroidMate will output: "+cfg.droidmateOutputDirPath.toAbsolutePath().toString())

  }

  private static void setLogbackRootLoggerLoggingLevel(Configuration config) throws ConfigurationException
  {
    ch.qos.logback.classic.Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    ch.qos.logback.classic.Logger explorationLogger = LoggerFactory.getLogger("org.droidmate.exploration") as ch.qos.logback.classic.Logger

    if (config.logLevel.toLowerCase() in ["info", "debug", "trace"])
    {
      rootLogger.setLevel(Level.toLevel(config.logLevel))
      explorationLogger.setLevel(Level.toLevel(config.logLevel))
    } else
      throw new ConfigurationException(String.format(
        "Unrecognized logging level. Given level: %s. Expected one of levels: info debug trace",
        config.logLevel))
  }

  /*
   * To keep the source DRY, we use apache's ReflectionToStringBuilder, which gets the field names and values using
   * reflection.
   */
  private static void logConfigurationInEffect(Configuration config)
  {

    // The customized display style strips the output of any data except the field name=value pairs.
    StandardToStringStyle displayStyle = new StandardToStringStyle()
    displayStyle.setArrayContentDetail(true)
    displayStyle.setUseClassName(false)
    displayStyle.setUseIdentityHashCode(false)
    displayStyle.setContentStart("")
    displayStyle.setContentEnd("")
    displayStyle.setFieldSeparator("\n")

    String configurationDump = new ReflectionToStringBuilder(config, displayStyle).toString()
    configurationDump = configurationDump.tokenize('\n').sort().join("\n")

    log.info(runData, "--------------------------------------------------------------------------------")
    log.info(runData, "")
    log.info(runData, "Working dir:   ${System.getProperty("user.dir")}")
    log.info(runData, "")
    log.info(runData, "JVM arguments: ${readJVMarguments()}")
    log.info(runData, "")
    log.info(runData, "Configuration dump:")
    log.info(runData, "")
    log.info(runData, configurationDump)
    log.info(runData, "")
    log.info(runData, "End of configuration dump")
    log.info(runData, "")
    log.info(runData, "--------------------------------------------------------------------------------")

  }

  /**
   * Based on: http://stackoverflow.com/a/1531999/986533
   */
  private static List<String> readJVMarguments()
  {
    return ManagementFactory.runtimeMXBean.inputArguments
  }

}