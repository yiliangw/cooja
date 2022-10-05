/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.Cooja;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.contikimote.ContikiMoteType.NetworkStack;
import org.contikios.cooja.mote.BaseContikiMoteType;

/**
 * Contiki Mote Type compile dialog.
 *
 * @author Fredrik Osterlind
 */
public class ContikiMoteCompileDialog extends AbstractCompileDialog {
  private static final Logger logger = LogManager.getLogger(ContikiMoteCompileDialog.class);

  private final JComboBox<?> netStackComboBox = new JComboBox<>(NetworkStack.values());

  public static boolean showDialog(Container parent, Simulation sim, ContikiMoteType mote) {
    final var dialog = new ContikiMoteCompileDialog(parent, sim, mote);
    dialog.setVisible(true); // Blocks.
    return dialog.createdOK();
  }

  private ContikiMoteCompileDialog(Container parent, Simulation simulation, ContikiMoteType moteType) {
    super(parent, simulation, moteType);

    if (contikiSource != null) {
      /* Make sure compilation variables are updated */
      getDefaultCompileCommands(contikiSource.getName());
    }

    /* Add Contiki mote type specifics */
    addAdvancedTab(tabbedPane);
  }

  @Override
  public boolean canLoadFirmware(String name) {
    return false; // Always recompile, CoreComm needs fresh names.
  }

  @Override
  public String getDefaultCompileCommands(String name) {
    if (name == null || !Files.exists(Path.of(name))) {
      return ""; // Not ready to compile yet.
    }

    if (moteType.getIdentifier() == null) {
      var usedNames = new HashSet<String>();
      for (var mote : simulation.getMoteTypes()) {
        usedNames.add(mote.getIdentifier());
      }
      moteType.setIdentifier(ContikiMoteType.generateUniqueMoteTypeID(usedNames));
    }

    moteType.setContikiSourceFile(new File(name));
    var env = moteType.getCompilationEnvironment();
    compilationEnvironment = BaseContikiMoteType.oneDimensionalEnv(env);
    if (SwingUtilities.isEventDispatchThread()) {
      createEnvironmentTab(tabbedPane, env);
    } else {
      try {
        SwingUtilities.invokeAndWait(() -> createEnvironmentTab(tabbedPane, env));
      } catch (InvocationTargetException | InterruptedException e) {
        logger.fatal("Error when updating for source " + name + ": " + e.getMessage(), e);
      }
    }

    String defines = "";
    if (((ContikiMoteType) moteType).getNetworkStack().getHeaderFile() != null) {
      defines = " DEFINES=NETSTACK_CONF_H=" + ((ContikiMoteType) moteType).getNetworkStack().getHeaderFile();
    }

    return Cooja.getExternalToolsSetting("PATH_MAKE") + " -j$(CPUS) " +
            ContikiMoteType.getMakeTargetName(name).getName() + " TARGET=cooja" + defines;
  }

  @Override
  public Class<? extends MoteInterface>[] getAllMoteInterfaces() {
    return ((ContikiMoteType)moteType).getAllMoteInterfaceClasses();
  }

  @Override
  public Class<? extends MoteInterface>[] getDefaultMoteInterfaces() {
	  return getAllMoteInterfaces();
  }

  private void addAdvancedTab(JTabbedPane parent) {

    /* TODO System symbols */

    /* Communication stack */
    JLabel label = new JLabel("Default network stack header");
    label.setPreferredSize(LABEL_DIMENSION);
    final JTextField headerTextField = new JTextField();
    headerTextField.setText(((ContikiMoteType)moteType).getNetworkStack().manualHeader);
    headerTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateHeader();
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        updateHeader();
      }
      @Override
      public void changedUpdate(DocumentEvent e) {
        updateHeader();
      }
      private void updateHeader() {
        ((ContikiMoteType)moteType).getNetworkStack().manualHeader = headerTextField.getText();
      }
    });
    final Box netStackHeaderBox = Box.createHorizontalBox();
    netStackHeaderBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    netStackHeaderBox.add(label);
    netStackHeaderBox.add(Box.createHorizontalStrut(20));
    netStackHeaderBox.add(headerTextField);

    label = new JLabel("Default network stack");
    label.setPreferredSize(LABEL_DIMENSION);
    netStackComboBox.setSelectedItem(((ContikiMoteType)moteType).getNetworkStack());
    netStackComboBox.setEnabled(true);
    netStackComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ((ContikiMoteType)moteType).setNetworkStack((NetworkStack)netStackComboBox.getSelectedItem());
        netStackHeaderBox.setVisible(netStackComboBox.getSelectedItem() == NetworkStack.MANUAL);
        setDialogState(DialogState.SELECTED_SOURCE);
      }
    });
    Box netStackBox = Box.createHorizontalBox();
    netStackBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    netStackBox.add(label);
    netStackBox.add(Box.createHorizontalStrut(20));
    netStackBox.add(netStackComboBox);
    netStackHeaderBox.setVisible(netStackComboBox.getSelectedItem() == NetworkStack.MANUAL);


    /* Advanced tab */
    Box box = Box.createVerticalBox();
    box.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    /*box.add(symbolsCheckBox);*/
    box.add(netStackBox);
    box.add(netStackHeaderBox);
    box.add(Box.createVerticalGlue());
    JPanel container = new JPanel(new BorderLayout());
    container.add(BorderLayout.NORTH, box);
    parent.addTab("Advanced", null, new JScrollPane(container), "Advanced Contiki Mote Type settings");
  }

  private void createEnvironmentTab(JTabbedPane parent, Object[][] env) {
    /* Remove any existing environment tabs */
    for (int i=0; i < tabbedPane.getTabCount(); i++) {
      if (tabbedPane.getTitleAt(i).equals("Environment")) {
        tabbedPane.removeTabAt(i--);
      }
    }

    /* Create new tab, fill with current environment data */
    String[] columnNames = { "Variable", "Value" };
    JTable table = new JTable(env, columnNames) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };

    JPanel panel = new JPanel(new BorderLayout());
    JButton button = new JButton("Change environment variables: Open external tools dialog");
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        /* Show external tools dialog */
        ExternalToolsDialog.showDialog(Cooja.getTopParentContainer());

        /* Update and select environment tab */
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            getDefaultCompileCommands(moteType.getContikiSourceFile().getName());
            for (int i=0; i < tabbedPane.getTabCount(); i++) {
              if (tabbedPane.getTitleAt(i).equals("Environment")) {
                tabbedPane.setSelectedIndex(i);
                break;
              }
            }
            setDialogState(DialogState.AWAITING_COMPILATION);
          }
        });

      }
    });
    panel.add(BorderLayout.NORTH, button);
    panel.add(BorderLayout.CENTER, new JScrollPane(table));

    parent.addTab("Environment", null, panel, "Environment variables");
  }
}
