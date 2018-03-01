/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import io.flutter.run.LaunchState;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.RunMode;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class BazelTestConfig extends RunConfigurationBase
  implements RunConfigurationWithSuppressedDefaultRunAction, LaunchState.RunConfig {
  @NotNull private BazelTestFields fields = new BazelTestFields();

  BazelTestConfig(final @NotNull Project project, final @NotNull ConfigurationFactory factory, @NotNull final String name) {
    super(project, factory, name);
  }

  @NotNull
  BazelTestFields getFields() {
    return fields;
  }

  void setFields(@NotNull BazelTestFields newFields) {
    fields = newFields;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    fields.checkRunnable(getProject());
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new FlutterBazelTestConfigurationEditorForm(getProject());
  }

  @NotNull
  @Override
  public LaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final BazelTestFields launchFields = fields.copy();
    try {
      launchFields.checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final MainFile main = MainFile.verify(launchFields.getEntryFile(), env.getProject()).get();
    final RunMode mode = RunMode.fromEnv(env);
    final Module module = ModuleUtil.findModuleForFile(main.getFile(), env.getProject());

    final LaunchState.Callback callback = (device) -> {
      if (device == null) return null;

      final GeneralCommandLine command = launchFields.getLaunchCommand(env.getProject(), device, mode);
      System.out.println("BazelRunConfig.getState " + command.toString());
      return FlutterApp.start(env, env.getProject(), module, mode, device, command,
                              StringUtil.capitalize(mode.mode()) + "BazelApp", "StopBazelApp");
    };

    return new LaunchState(env, main.getAppDir(), main.getFile(), this, callback);
  }

  public BazelTestConfig clone() {
    final BazelTestConfig clone = (BazelTestConfig)super.clone();
    clone.fields = fields.copy();
    return clone;
  }

  RunConfiguration copyTemplateToNonTemplate(String name) {
    final BazelTestConfig copy = (BazelTestConfig)super.clone();
    copy.setName(name);
    copy.fields = fields.copyTemplateToNonTemplate(getProject());
    return copy;
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    XmlSerializer.serializeInto(fields, element, new SkipDefaultValuesSerializationFilters());
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    XmlSerializer.deserializeInto(fields, element);
  }
}