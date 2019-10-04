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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Dialog to display the unpackaging progress and allow cancellation.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class UnpackagePortableCaseProgressDialog extends javax.swing.JDialog implements PropertyChangeListener {

    private UnpackageWorker worker;

    /**
     * Creates new form UnpackagePortableCaseProgressDialog
     */
    @NbBundle.Messages({"UnpackagePortableCaseProgressDialog.title.text=Unpackage Portable Case Progress",})
    UnpackagePortableCaseProgressDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.UnpackagePortableCaseProgressDialog_title_text(),
                true);
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        cancelButton.setEnabled(true);
        okButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        resultLabel.setText(""); // NON-NLS
    }

    /**
     * Unpackage the case
     *
     * @param packagedCase The compressed case
     * @param outputFolder The output folder
     */
    void unpackageCase(String packagedCase, String outputFolder) {

        worker = new UnpackageWorker(packagedCase, outputFolder);
        worker.addPropertyChangeListener(this);
        worker.execute();

        setLocationRelativeTo((JFrame) WindowManager.getDefault().getMainWindow());
        this.setVisible(true);

    }

    /**
     * Returns whether the unpackaging was completed successfully.
     *
     * @return True if unpackaging was completed successfully, false otherwise
     */
    boolean isSuccess() {
        if (worker == null) {
            return false;
        } else {
            return worker.isSuccess();
        }
    }

    @NbBundle.Messages({"UnpackagePortableCaseProgressDialog.propertyChange.success=Successfully unpacked case",})
    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if ("state".equals(evt.getPropertyName())
                && (SwingWorker.StateValue.DONE.equals(evt.getNewValue()))) { // NON-NLS

            // The worker is done processing            
            // Disable cancel button and enable ok
            cancelButton.setEnabled(false);
            okButton.setEnabled(true);

            if (worker.isSuccess()) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(progressBar.getMaximum());
                resultLabel.setText(Bundle.UnpackagePortableCaseProgressDialog_propertyChange_success());
            } else {
                // If there was an error, reset the progress bar and display an error message
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                resultLabel.setForeground(Color.red);
                resultLabel.setText(worker.getDisplayError());
            }
        }
    }

    /**
     * Swing worker to do the decompression.
     */
    private class UnpackageWorker extends SwingWorker<Void, Void> {

        private final String packagedCase;
        private final String outputFolder;

        private final AtomicBoolean success = new AtomicBoolean();
        private String lastError = "";

        UnpackageWorker(String packagedCase, String outputFolder) {
            this.packagedCase = packagedCase;
            this.outputFolder = outputFolder;
            this.success.set(false);
        }

        @NbBundle.Messages({
            "UnpackageWorker.doInBackground.errorFinding7zip=Could not locate 7-Zip executable",
            "UnpackageWorker.doInBackground.errorCompressingCase=Error unpackaging case",
            "UnpackageWorker.doInBackground.canceled=Unpackaging canceled by user",})
        @Override
        protected Void doInBackground() throws Exception {

            // Find 7-Zip
            File sevenZipExe = locate7ZipExecutable();
            if (sevenZipExe == null) {
                setDisplayError(Bundle.UnpackageWorker_doInBackground_errorFinding7zip());
                throw new TskCoreException("Error finding 7-Zip executable"); // NON-NLS
            }

            String outputFolderSwitch = "-o" + outputFolder; // NON-NLS
            ProcessBuilder procBuilder = new ProcessBuilder();
            procBuilder.command(
                    sevenZipExe.getAbsolutePath(),
                    "x", // Extract
                    packagedCase,
                    outputFolderSwitch
            );

            try {
                Process process = procBuilder.start();

                while (process.isAlive()) {
                    if (this.isCancelled()) {
                        setDisplayError(Bundle.UnpackageWorker_doInBackground_canceled());
                        return null;
                    }
                    Thread.sleep(200);
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    // Save any errors so they can be logged
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append(System.getProperty("line.separator")); // NON-NLS
                        }
                    }

                    setDisplayError(Bundle.UnpackageWorker_doInBackground_errorCompressingCase());
                    throw new TskCoreException("Error unpackaging case. 7-Zip output: " + sb.toString()); // NON-NLS
                }
            } catch (IOException | InterruptedException ex) {
                setDisplayError(Bundle.UnpackageWorker_doInBackground_errorCompressingCase());
                throw new TskCoreException("Error unpackaging case", ex); // NON-NLS
            }

            success.set(true);
            return null;
        }

        @Override
        synchronized protected void done() {
            if (this.isCancelled()) {
                return;
            }

            try {
                get();
            } catch (Exception ex) {
                Logger.getLogger(UnpackagePortableCaseProgressDialog.class.getName()).log(Level.SEVERE, "Error unpackaging portable case", ex); // NON-NLS
            }
        }

        /**
         * Save the error that should be displayed to the user
         *
         * @param errorStr Error to be displayed in the UI
         */
        private synchronized void setDisplayError(String errorStr) {
            lastError = errorStr;
        }

        /**
         * Gets the error to display to the user
         *
         * @return Error to be displayed in the UI
         */
        private synchronized String getDisplayError() {
            return lastError;
        }

        protected boolean isSuccess() {
            return success.get();
        }

        /**
         * Locate the 7-Zip executable from the release folder.
         *
         * @return 7-Zip executable
         */
        private File locate7ZipExecutable() {
            if (!PlatformUtil.isWindowsOS()) {
                return null;
            }

            String executableToFindName = Paths.get("7-Zip", "7z.exe").toString(); // NON-NLS
            File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, UnpackagePortableCaseProgressDialog.class.getPackage().getName(), false);
            if (null == exeFile) {
                return null;
            }

            if (!exeFile.canExecute()) {
                return null;
            }

            return exeFile;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        progressBar = new javax.swing.JProgressBar();
        cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        resultLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseProgressDialog.class, "UnpackagePortableCaseProgressDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseProgressDialog.class, "UnpackagePortableCaseProgressDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(resultLabel, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseProgressDialog.class, "UnpackagePortableCaseProgressDialog.resultLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(progressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(resultLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton)
                    .addComponent(resultLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        worker.cancel(true);
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton okButton;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel resultLabel;
    // End of variables declaration//GEN-END:variables
}
