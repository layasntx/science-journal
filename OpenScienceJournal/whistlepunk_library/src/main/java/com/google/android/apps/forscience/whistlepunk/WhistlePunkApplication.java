/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.licenses.LicenseProvider;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import java.util.Map;
import javax.inject.Inject;

/** Application subclass holding shared objects. */
public abstract class WhistlePunkApplication extends Application {
  private RefWatcher refWatcher;

  private static int versionCode;

  // TODO: create directly in subclasses, rather than dagger injection

  @Inject UsageTracker usageTracker;

  @Inject FeatureDiscoveryProvider featureDiscoveryProvider;

  @Inject FeedbackProvider feedbackProvider;

  @Inject AccountsProvider accountsProvider;

  @Inject Map<String, SensorDiscoverer> sensorDiscoverers;

  @Inject PerfTrackerProvider perfTrackerProvider;

  @Inject LicenseProvider licenseProvider;

  private final AppServices appServices =
      new AppServices() {
        @Override
        public RefWatcher getRefWatcher() {
          return refWatcher;
        }

        public UsageTracker getUsageTracker() {
          return usageTracker;
        }

        @Override
        public FeatureDiscoveryProvider getFeatureDiscoveryProvider() {
          return featureDiscoveryProvider;
        }

        @Override
        public FeedbackProvider getFeedbackProvider() {
          return feedbackProvider;
        }

        @Override
        public ActivityNavigator getNavigator() {
          return WhistlePunkApplication.this.getNavigator();
        }

        @Override
        public AccountsProvider getAccountsProvider() {
          return accountsProvider;
        }

        @Override
        public LicenseProvider getLicenseProvider() {
          return licenseProvider;
        }
      };

  public static AppServices getAppServices(Context context) {
    if (hasAppServices(context)) {
      WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
      return app.appServices;
    } else {
      return AppServices.STUB;
    }
  }

  public static UsageTracker getUsageTracker(Context context) {
    // TODO: use directly in callers?  (There's a lot of them)
    return getAppServices(context).getUsageTracker();
  }

  private static boolean hasAppServices(Context context) {
    return context != null && context.getApplicationContext() instanceof WhistlePunkApplication;
  }

  public static Map<String, SensorDiscoverer> getExternalSensorDiscoverers(Context context) {
    WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
    return app.sensorDiscoverers;
  }

  public static PerfTrackerProvider getPerfTrackerProvider(Context context) {
    WhistlePunkApplication app = (WhistlePunkApplication) context.getApplicationContext();
    return app.perfTrackerProvider;
  }

  public static Intent getLaunchIntentForPanesActivity(
      Context context, AppAccount appAccount, String experimentId) {
    return getAppServices(context)
        .getNavigator()
        .launchIntentForPanesActivity(context, appAccount, experimentId);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    if (LeakCanary.isInAnalyzerProcess(this)) {
      // This process is dedicated to LeakCanary for heap analysis.
      // You should not init your app in this process.
      return;
    }
    refWatcher = installLeakCanary();
    versionCode = populateVersionCode();
    onCreateInjector();
    enableStrictMode();
    setupBackupAgent();
    setupNotificationChannel();
  }

  protected void setupBackupAgent() {
    // Register your backup agent to receive settings change events here.
    // Learn more at
    // https://developer.android.com/guide/topics/data/keyvaluebackup.html#BackupAgentHelper.
  }

  protected RefWatcher installLeakCanary() {
    return RefWatcher.DISABLED;
  }

  protected abstract void onCreateInjector();

  protected void enableStrictMode() {}

  private void setupNotificationChannel() {
    if (AndroidVersionUtils.isApiLevelAtLeastOreo()) {
      NotificationManager notificationManager =
          (NotificationManager)
              getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
      // The id of the channel.
      String id = NotificationChannels.NOTIFICATION_CHANNEL;
      // The user-visible name of the channel.
      CharSequence name = getApplicationContext().getString(R.string.notification_channel_name);
      // The user-visible description of the channel.
      String description =
          getApplicationContext().getString(R.string.notification_channel_description);
      NotificationChannel mChannel =
          new NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT);
      // Configure the notification channel.
      mChannel.setDescription(description);
      notificationManager.createNotificationChannel(mChannel);
    }
  }

  public ActivityNavigator getNavigator() {
    return new ActivityNavigator() {
      @Override
      public Intent launchIntentForPanesActivity(
          Context context, AppAccount appAccount, String experimentId) {
        return PanesActivity.launchIntent(context, appAccount, experimentId);
      }
    };
  }

  private int populateVersionCode() {
    PackageInfo packageInfo;
    try {
      packageInfo =
          getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
    } catch (NameNotFoundException e) {
      // A query for ourselves (getPackageName()) should never throw NameNotFoundException
      throw new AssertionError(e);
    }
    return packageInfo.versionCode;
  }

  public static int getVersionCode() {
    return versionCode;
  }

  public static AppAccount getAccount(Context context, Bundle arguments, String key) {
    String accountKey = arguments.getString(key);
    return getAppServices(context).getAccountsProvider().getAccountByKey(accountKey);
  }

  public static AppAccount getAccount(Context context, Intent intent, String key) {
    return getAccount(context, intent.getExtras(), key);
  }
}
