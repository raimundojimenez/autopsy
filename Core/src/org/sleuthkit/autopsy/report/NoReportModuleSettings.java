/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.report;

/**
 * Implementation of the ReportModuleSettings interface for use by report
 * modules that do not have settings.
 */
public final class NoReportModuleSettings implements ReportModuleSettings {

    private static final long serialVersionUID = 1L;
    private final String setting = "None"; //NON-NLS

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    /**
     * Gets the string used as a report options placeholder for serialization
     * purposes.
     *
     * @return The string "None"
     */
    String getSetting() {
        return setting;
    }
}
