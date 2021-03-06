// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ui.configuration.SdkPopup
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

data class RuntimeChooserCustomItem(
  val version: String,
  override val homeDir: String,
) : RuntimeChooserItem(), RuntimeChooserItemWithFixedLocation

object RuntimeChooserAddCustomItem : RuntimeChooserItem()

object RuntimeChooserCustom {
  private val LOG = logger<RuntimeChooserCustom>()
  private const val minJdkFeatureVersion = 11

  val sdkType
    get() = SdkType
      .getAllTypes()
      .singleOrNull(SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType()::value)

  val isActionAvailable
    get() = sdkType != null

  val jdkDownloaderExtensionProvider = DataProvider { dataId ->
    when {
      JDK_DOWNLOADER_EXT.`is`(dataId) -> jdkDownloaderExtension
      else -> null
    }
  }

  private val jdkDownloaderExtension = object: JdkDownloaderDialogHostExtension {
    override fun allowWsl(): Boolean = false

    override fun shouldIncludeItem(sdkType: SdkTypeId, item: JdkItem): Boolean {
      return item.jdkMajorVersion >= minJdkFeatureVersion && item.os == JdkPredicate.currentOS && item.arch == JdkPredicate.currentArch
    }
  }

  fun createSdkChooserPopup(parent: JComponent, model: RuntimeChooserModel) : SdkPopup? {
    return SdkPopupFactory
      .newBuilder()
      .withSdkType(sdkType ?: return null)
      .withSdkFilter { it != null && isSupportedSdkItem({ it.versionString }, { it.homePath }) }
      .withSuggestedSdkFilter { it != null && isSupportedSdkItem({ it.versionString }, { it.homePath }) }
      .onSdkSelected { sdk -> importNewItem(parent, sdk, model) }
      .buildPopup()
  }

  private fun isSupportedSdkItem(versionString: () -> String?, homePath: () -> String?): Boolean = runCatching {
    val version = versionString() ?: return false
    val home = homePath() ?: return false
    val javaVersion = JavaVersion.tryParse(version) ?: return false
    javaVersion.feature >= minJdkFeatureVersion && !WslDistributionManager.isWslPath(home)
  }.getOrDefault(false)

  private fun importNewItem(parent: JComponent, sdk: Sdk?, model: RuntimeChooserModel) {
    if (sdk == null) return

    object: Task.Backgroundable(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.jdk", false, ALWAYS_BACKGROUND)) {
      override fun run(indicator: ProgressIndicator) {
        val homeDir = runCatching { sdk.homePath }.getOrNull() ?: return

        try {
          val info = JdkVersionDetector.getInstance().detectJdkVersionInfo(homeDir) ?: return
          val version = listOfNotNull(info.displayName, info.version?.toString() ?: sdk.versionString ?: return).joinToString(" ")

          if (WslDistributionManager.isWslPath(homeDir)) {
            return service<RuntimeChooserMessages>().notifyCustomJdkIsFromWsl(parent, homeDir)
          }

          if (info.version.feature < minJdkFeatureVersion) {
            return service<RuntimeChooserMessages>().notifyCustomJdkVersionIsTooOld(parent, homeDir, info.version.toString(), "11")
          }

          if (!isJdkAlive(homeDir)) {
            return service<RuntimeChooserMessages>().notifyCustomJdkDoesNotStart(parent, homeDir)
          }

          if (!isSupportedSdkItem({ version }, { homeDir })) {
            return service<RuntimeChooserMessages>().notifyCustomJdkInvalid(parent, homeDir)
          }

          val newItem = RuntimeChooserCustomItem(version, homeDir)
          invokeLater(ModalityState.stateForComponent(parent)) {
            model.addExistingSdkItem(newItem)
          }
        } catch (t: Throwable) {
          LOG.warn("Failed to scan JDK for boot runtime: $sdk, ${sdk.homeDirectory}. ${t.message}", t)
          return service<RuntimeChooserMessages>().notifyCustomJdkInvalid(parent, homeDir)
        }
      }
    }.queue()
  }

  private fun isJdkAlive(javaHome: String): Boolean {
    val java = when {
      SystemInfo.isWindows -> "bin/java.exe"
      SystemInfo.isMac -> "Contents/Home/bin/java"
      else -> "bin/java"
    }

    val bin = Path.of(javaHome, java)
    if (!Files.isRegularFile(bin) || !(SystemInfo.isWindows || Files.isExecutable(bin))) return false

    try {
      val cmd = GeneralCommandLine(bin.toString(), "-version")
      return CapturingProcessHandler(cmd).runProcess(30_000).exitCode == 0
    }
    catch (t: Throwable) {
      LOG.warn("Failed to run JDK for boot runtime: $javaHome}. ${t.message}", t)
      return false
    }
  }
}
