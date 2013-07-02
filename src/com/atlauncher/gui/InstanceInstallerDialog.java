/**
 * Copyright 2013 by ATLauncher and Contributors
 *
 * ATLauncher is licensed under CC BY-NC-ND 3.0 which allows others you to
 * share this software with others as long as you credit us by linking to our
 * website at http://www.atlauncher.com. You also cannot modify the application
 * in any way or make commercial use of this software.
 *
 * Link to license: http://creativecommons.org/licenses/by-nc-nd/3.0/
 */
package com.atlauncher.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

import com.atlauncher.data.Instance;
import com.atlauncher.data.Pack;
import com.atlauncher.workers.InstanceInstaller;

public class InstanceInstallerDialog extends JDialog {

    private boolean isReinstall = false;
    private Pack pack = null;
    private Instance instance = null;

    private JPanel top;
    private JPanel middle;
    private JPanel bottom;
    private JButton install;
    private JButton cancel;
    private JProgressBar progressBar;
    private JProgressBar subProgressBar;
    private JLabel instanceNameLabel;
    private JTextField instanceNameField;
    private JLabel versionLabel;
    private JComboBox<String> versionsDropDown;
    private JLabel useLatestLWJGLLabel;
    private JCheckBox useLatestLWJGL;

    public InstanceInstallerDialog(Object object) {
        super(LauncherFrame.settings.getParent(), ModalityType.APPLICATION_MODAL);
        if (object instanceof Pack) {
            pack = (Pack) object;
        } else {
            instance = (Instance) object;
            pack = instance.getRealPack();
            isReinstall = true; // We're reinstalling
        }
        setSize(400, 200);
        setLocationRelativeTo(LauncherFrame.settings.getParent());
        setLayout(new BorderLayout());
        setResizable(false);

        // Top Panel Stuff
        top = new JPanel();
        top.add(new JLabel(((isReinstall) ? "Reinstalling " : "Installing ") + pack.getName()));

        // Middle Panel Stuff
        middle = new JPanel();
        middle.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        instanceNameLabel = new JLabel("Instance Name: ");
        middle.add(instanceNameLabel, gbc);

        gbc.gridx++;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        instanceNameField = new JTextField(17);
        instanceNameField.setText(((isReinstall) ? instance.getName() : pack.getName()));
        if (isReinstall) {
            instanceNameField.setEnabled(false);
        }
        middle.add(instanceNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        versionLabel = new JLabel("Version To Install: ");
        middle.add(versionLabel, gbc);

        gbc.gridx++;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        versionsDropDown = new JComboBox<String>();
        if (pack.isTester()) {
            versionsDropDown.addItem("Dev Version");
        }
        for (int i = 0; i < pack.getVersionCount(); i++) {
            versionsDropDown.addItem(pack.getVersion(i));
        }
        versionsDropDown.setPreferredSize(new Dimension(200, 25));
        middle.add(versionsDropDown, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        useLatestLWJGLLabel = new JLabel("Use Latest LWJGL? ");
        middle.add(useLatestLWJGLLabel, gbc);

        gbc.gridx++;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        useLatestLWJGL = new JCheckBox();
        middle.add(useLatestLWJGL, gbc);

        // Bottom Panel Stuff
        bottom = new JPanel();
        bottom.setLayout(new FlowLayout());
        install = new JButton(((isReinstall) ? "Reinstall" : "Install"));
        install.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!isReinstall && LauncherFrame.settings.isInstance(instanceNameField.getText())) {
                    JOptionPane.showMessageDialog(LauncherFrame.settings.getParent(),
                            "<html><center>Error!<br/><br/>There is already an instance called "
                                    + instanceNameField.getText()
                                    + "<br/><br/>Rename it and try again</center></html>",
                            "Error!", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                final String version = (String) versionsDropDown.getSelectedItem();
                final JDialog dialog = new JDialog(LauncherFrame.settings.getParent(),
                        ((isReinstall) ? "Reinstalling " : "Installing ") + pack.getName() + " "
                                + version, ModalityType.DOCUMENT_MODAL);
                dialog.setLocationRelativeTo(LauncherFrame.settings.getParent());
                dialog.setSize(300, 100);
                dialog.setResizable(false);

                JPanel topPanel = new JPanel();
                topPanel.setLayout(new BorderLayout());
                final JLabel doing = new JLabel("Starting "
                        + ((isReinstall) ? "Reinstall" : "Install") + " Process");
                doing.setHorizontalAlignment(JLabel.CENTER);
                doing.setVerticalAlignment(JLabel.TOP);
                topPanel.add(doing);

                JPanel bottomPanel = new JPanel();
                bottomPanel.setLayout(new BorderLayout());
                progressBar = new JProgressBar(0, 100);
                bottomPanel.add(progressBar, BorderLayout.NORTH);
                progressBar.setIndeterminate(true);
                subProgressBar = new JProgressBar(0, 100);
                bottomPanel.add(subProgressBar, BorderLayout.SOUTH);
                subProgressBar.setValue(0);
                subProgressBar.setVisible(false);

                dialog.add(topPanel, BorderLayout.CENTER);
                dialog.add(bottomPanel, BorderLayout.SOUTH);

                final InstanceInstaller instanceInstaller = new InstanceInstaller(instanceNameField
                        .getText(), pack, version, useLatestLWJGL.isSelected(), isReinstall) {

                    protected void done() {
                        Boolean success = false;
                        int type;
                        String text;
                        String title;
                        if (isCancelled()) {
                            type = JOptionPane.ERROR_MESSAGE;
                            text = pack.getName() + " " + version + " wasn't "
                                    + ((isReinstall) ? "reinstalled" : "installed")
                                    + "<br/><br/>Action was cancelled by user!";
                            title = pack.getName() + " " + version + " Not "
                                    + ((isReinstall) ? "Reinstalled" : "Installed");
                        } else {
                            try {
                                success = get();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                            if (success) {
                                type = JOptionPane.INFORMATION_MESSAGE;
                                text = pack.getName() + " " + version + " has been "
                                        + ((isReinstall) ? "reinstalled" : "installed")
                                        + "<br/><br/>Find it in your 'Instances' tab";
                                title = pack.getName() + " " + version + " Installed";
                                if (isReinstall) {
                                    instance.setVersion(version);
                                    instance.setMinecraftVersion(this.getMinecraftVersion());
                                    instance.setJarOrder(this.getJarOrder());
                                    instance.setIsNewLaunchMethod(this.isNewLaunchMethod());
                                    if (this.isNewLaunchMethod()) {
                                        instance.setLibrariesNeeded(this.getLibrariesNeeded());
                                        instance.setMinecraftArguments(this.getMinecraftArguments());
                                        instance.setMainClass(this.getMainClass());
                                    }
                                    LauncherFrame.settings.reloadInstancesPanel();
                                    LauncherFrame.settings.saveInstances();
                                } else {
                                    LauncherFrame.settings.addInstance(new Instance(
                                            instanceNameField.getText(), pack.getName(), pack,
                                            version, this.getMinecraftVersion(),
                                            this.getJarOrder(), this.getLibrariesNeeded(), this
                                                    .getMinecraftArguments(), this.getMainClass(),
                                            this.isNewLaunchMethod())); // Add It
                                }
                            } else {
                                if (isReinstall) {
                                    type = JOptionPane.ERROR_MESSAGE;
                                    text = pack.getName() + " " + version
                                            + " wasn't reinstalled<br/><br/>Instance is no longer "
                                            + "playable<br/><br/>Check error logs for the error!";
                                    title = pack.getName() + " " + version + " Not Reinstalled";
                                    LauncherFrame.settings.setInstanceUnplayable(instance);
                                } else {
                                    // Install failed so delete the folder and clear Temp Dir
                                    Utils.delete(this.getRootDirectory());
                                    type = JOptionPane.ERROR_MESSAGE;
                                    text = pack.getName()
                                            + " "
                                            + version
                                            + " wasn't installed<br/><br/>Check error logs for the error!";
                                    title = pack.getName() + " " + version + " Not Installed";
                                }
                            }
                        }

                        dialog.dispose();

                        Utils.cleanTempDirectory();

                        JOptionPane.showMessageDialog(LauncherFrame.settings.getParent(),
                                "<html><center>" + text + "</center></html>", title, type);
                    }

                };
                instanceInstaller.addPropertyChangeListener(new PropertyChangeListener() {

                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("progress" == evt.getPropertyName()) {
                            if (progressBar.isIndeterminate()) {
                                progressBar.setIndeterminate(false);
                            }
                            int progress = (Integer) evt.getNewValue();
                            if (progress > 100) {
                                progress = 100;
                            }
                            progressBar.setValue(progress);
                        } else if ("subprogress" == evt.getPropertyName()) {
                            if (!subProgressBar.isVisible()) {
                                subProgressBar.setVisible(true);
                            }
                            if (subProgressBar.isIndeterminate()) {
                                subProgressBar.setIndeterminate(false);
                            }
                            int progress = (Integer) evt.getNewValue();
                            if (progress > 100) {
                                progress = 100;
                            }
                            if (progress == 0) {
                                subProgressBar.setVisible(false);
                            }
                            subProgressBar.setValue(progress);
                        } else if ("subprogressint" == evt.getPropertyName()) {
                            if (!subProgressBar.isVisible()) {
                                subProgressBar.setVisible(true);
                            }
                            if (!subProgressBar.isIndeterminate()) {
                                subProgressBar.setIndeterminate(true);
                            }
                        } else if ("doing" == evt.getPropertyName()) {
                            String doingText = (String) evt.getNewValue();
                            doing.setText(doingText);
                        }

                    }
                });
                dialog.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        instanceInstaller.cancel(true);
                    }
                });
                instanceInstaller.execute();
                dispose();
                dialog.setVisible(true);

            }
        });
        cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        bottom.add(install);
        bottom.add(cancel);

        add(top, BorderLayout.NORTH);
        add(middle, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setVisible(true);
    }
}