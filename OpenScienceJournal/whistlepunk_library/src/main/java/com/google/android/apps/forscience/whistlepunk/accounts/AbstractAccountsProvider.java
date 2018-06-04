/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.accounts;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.TwoStatePreference;
import androidx.annotation.Nullable;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** An abstract base class for accounts providers. */
abstract class AbstractAccountsProvider implements AccountsProvider {
  final Context applicationContext;
  final BehaviorSubject<AppAccount> observableCurrentAccount = BehaviorSubject.create();
  private final Object lockCurrentAccount = new Object();
  private AppAccount currentAccount;
  private final Map<String, AppAccount> accountsByKey = new HashMap<>();
  private final Map<String, Boolean> accountBasedPreferenceKeys = new HashMap<>();

  AbstractAccountsProvider(Context context) {
    applicationContext = context.getApplicationContext();

    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(applicationContext);
    accountsByKey.put(nonSignedInAccount.getAccountKey(), nonSignedInAccount);
  }

  @Override
  public final boolean isSignedIn() {
    synchronized (lockCurrentAccount) {
      return currentAccount != null && currentAccount.isSignedIn();
    }
  }

  @Override
  public final Observable<AppAccount> getObservableCurrentAccount() {
    return observableCurrentAccount;
  }

  @Override
  public AppAccount getAccountByKey(String accountKey) {
    AppAccount appAccount = accountsByKey.get(accountKey);
    if (appAccount != null) {
      return appAccount;
    }
    throw new IllegalArgumentException("The accountKey is not associated with a known AppAccount");
  }

  @Override
  public void registerAccountBasedPreferenceKey(String prefKey, Boolean defaultValue) {
    accountBasedPreferenceKeys.put(prefKey, defaultValue);
  }

  @Override
  public void adjustPreferenceFragment(PreferenceFragment preferenceFragment) {
    AppAccount appAccount = getCurrentAccount();
    if (appAccount != null) {
      for (Map.Entry<String, Boolean> entry : accountBasedPreferenceKeys.entrySet()) {
        String key = entry.getKey();
        Preference preference = preferenceFragment.findPreference(key);
        if (preference != null) {
          // Adjust the preference's key and checked value with the key and checked value from the
          // account-based preference.
          String accountPreferenceKey = appAccount.getPreferenceKey(key);
          preference.setKey(accountPreferenceKey);
          if (preference instanceof TwoStatePreference) {
            boolean defaultValue = entry.getValue();
            boolean value =
                preference.getSharedPreferences().getBoolean(accountPreferenceKey, defaultValue);
            ((TwoStatePreference) preference).setChecked(value);
          } else {
            throw new UnsupportedOperationException(
                "Adjustment for "
                    + preference.getClass().getSimpleName()
                    + " has not been implemented");
          }
        }
      }
    }
  }

  @Nullable
  protected final AppAccount getCurrentAccount() {
    synchronized (lockCurrentAccount) {
      return currentAccount;
    }
  }

  /**
   * Sets the current account and publishes it to observers if the current account override has not
   * been set.
   */
  protected void setCurrentAccount(AppAccount currentAccount) {
    synchronized (lockCurrentAccount) {
      // Add the account to the accountsByKey map.
      accountsByKey.put(currentAccount.getAccountKey(), currentAccount);

      this.currentAccount = currentAccount;
      observableCurrentAccount.onNext(currentAccount);
    }
  }

  protected void removeAccounts(Set<String> accountKeysToRemove) {
    accountsByKey.keySet().removeAll(accountKeysToRemove);
  }
}
