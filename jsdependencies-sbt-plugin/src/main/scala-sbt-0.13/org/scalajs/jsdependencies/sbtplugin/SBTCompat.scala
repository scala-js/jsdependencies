package org.scalajs.jsdependencies.sbtplugin

import sbt._

private[sbtplugin] object SBTCompat {
  def moduleIDWithConfigurations(moduleID: ModuleID,
      configurations: Option[String]): ModuleID = {
    moduleID.copy(configurations = configurations)
  }
}
