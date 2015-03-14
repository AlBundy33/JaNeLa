/*******************************************************************************
 * Copyright 2009, 2010 Andrew Thompson.
 * 
 * This file is part of JaNeLa.
 * 
 * JaNeLa is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * JaNeLa is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with JaNeLa.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.pscode.tool.janela.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.pscode.tool.janela.JNLPAnalyser;
import org.pscode.tool.janela.JaNeLA;
import org.pscode.tool.janela.LaunchError;

public class JaNeLaPanel extends JPanel {

    private JTabbedPane tabbedPane;
    private JFileChooser fileChooser;
    private JLabel status;
    private JProgressBar progressBar;
    private CardLayout progressCards;
    private JPanel progressPanel;

    private Action openFileAction;
    private Action openURLAction;
    private Action launchAction;
    private Action textReportAction;
    private Action usageHelpAction;
    private Action errorsHelpAction;
    private Action aboutAction;
    
    private final List<JNLPAnalyser> extensionAnalysers = new ArrayList<JNLPAnalyser>();
    private JNLPAnalyser mainAnalyser;
    private URL currentJNLP;
    
    public JaNeLaPanel() {
        initializeActions();
        initializePanel();
    }
    
    private void initializeActions() {
        launchAction = new AbstractAction("Test launch") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    doLaunch();
                } catch(IOException exception) {
                    showError(exception);
                }
            }
        };
        launchAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_L);
        launchAction.setEnabled(false);
        
        openFileAction = new AbstractAction("Open file") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int result = fileChooser.showOpenDialog(JaNeLaPanel.this);
                if (result==JFileChooser.APPROVE_OPTION) {
                    try {
                        doAnalyse(fileChooser.getSelectedFile().toURI().toURL());
                    } catch(IOException exception) {
                        showError(exception);
                    }
                }
            }
        };
        openFileAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_F);
        openFileAction.putValue(Action.ACCELERATOR_KEY, 
                KeyStroke.getKeyStroke(KeyEvent.VK_F,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        
        openURLAction = new AbstractAction("Open URL") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String urlString = JOptionPane.showInputDialog(
                        JaNeLaPanel.this, "Open URL", currentJNLP);
                if(urlString != null) {
                    try {
                        doAnalyse(new URL(urlString));
                    } catch(IOException exception) {
                        showError(exception);
                    }
                }
            }
        };
        openURLAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_U);
        openURLAction.putValue(Action.ACCELERATOR_KEY, 
                KeyStroke.getKeyStroke(KeyEvent.VK_U,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
   
        textReportAction = new AbstractAction("Text report") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doTextReport();
            }
        };
        textReportAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_R);
        textReportAction.setEnabled(false);
        
        usageHelpAction = new AbstractAction("Help on usage") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doOpenLink("http://pscode.org/janela/");
            }
        };
        usageHelpAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_U);
        
        errorsHelpAction = new AbstractAction("Help on errors") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doOpenLink("http://pscode.org/janela/help.html");
            }
        };
        errorsHelpAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_E);
        
        aboutAction = new AbstractAction("About") {
            @Override
            public void actionPerformed(ActionEvent e) {
                doShowAbout();
            }
        };
        aboutAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_A);
    }

    private void doOpenLink(String link) {
        try {
            URL url = new URL(link);
            Desktop.getDesktop().browse(url.toURI());
        } catch (Exception e) {
            showError(e);
        }
    }

    private void doTextReport() {
        String textReport = getTextReport();
        
        JTextArea ta = new JTextArea(10,50);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);

        ta.setText( textReport );
        ta.setCaretPosition(0);

        JOptionPane.showMessageDialog(
            tabbedPane,
            new JScrollPane(ta),
            "JaNeLA Report",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void doShowAbout() {
        Package pckg = JaNeLA.class.getPackage();
        
        JPanel infoPane = new JPanel(new BorderLayout(4,6));
        JPanel labels = new JPanel(new GridLayout(0,1,2,2));
        JPanel text = new JPanel(new GridLayout(0,1,2,2));

        labels.add( new JLabel("Implementation Title") );
        text.add( new JLabel(pckg.getImplementationTitle()) );

        labels.add( new JLabel("Implementation Vendor") );
        text.add( new JLabel(pckg.getImplementationVendor()) );

        labels.add( new JLabel("Implementation Version") );
        text.add( new JLabel(pckg.getImplementationVersion()) );

        infoPane.add(labels, BorderLayout.WEST);
        infoPane.add(text, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(
            this,
            infoPane,
            "About JaNeLA",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    public String getTextReport() {
        StringBuffer sb = new StringBuffer("JaNeLA Report - version ");
        sb.append(JaNeLA.class.getPackage().getImplementationVersion());
        sb.append("\n\n\n");
        
        sb.append( mainAnalyser.getReport() );
    
        for (JNLPAnalyser analyser : extensionAnalysers) {
            sb.append( analyser.getReport() );
        }
        
        return sb.toString();
    }

    private void initializePanel() {
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Main JNLP", new ExtensionPanel());
        
        status = new JLabel("JaNeLA - Java Network Launch Analyzer");
        progressBar = new JProgressBar();
        progressCards = new CardLayout();
        progressPanel = new JPanel(progressCards);
        progressPanel.add(new JPanel(), "idle");
        progressPanel.add(progressBar, "progress");
        progressPanel.setPreferredSize(new Dimension(150,
                status.getPreferredSize().height));
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(status, BorderLayout.CENTER);
        statusPanel.add(progressPanel, BorderLayout.LINE_END);
        
        fileChooser = new JFileChooser();
        FileNameExtensionFilter fileFilter = new
            FileNameExtensionFilter("JNLP descriptors", "jnlp");
        fileChooser.setFileFilter(fileFilter);
        
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(new JLabel("Open JNLP descriptor from: "));
        toolBar.add(openFileAction).setText("File");
        toolBar.add(openURLAction).setText("URL");
//        toolBar.addSeparator();
//        toolBar.add(launchAction);

        setLayout(new BorderLayout(3,3));
        setBorder(new EmptyBorder(2,2,2,2));

        add(toolBar, BorderLayout.PAGE_START);
        add(tabbedPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.PAGE_END);
    }

    public ExtensionPanel getMainJNLPPanel() {
        return (ExtensionPanel) tabbedPane.getComponentAt(0);
    }
    
    public List<Action> getFileActions() {
        return Arrays.asList(openFileAction, openURLAction); //, launchAction);
    }

    public List<Action> getReportActions() {
        return Arrays.asList(textReportAction);
    }
    
    public List<Action> getHelpActions() {
        return Arrays.asList(usageHelpAction, errorsHelpAction, null, aboutAction);
    }
    
    private void doAnalyse(URL url) {
        this.currentJNLP = url;
        
        openFileAction.setEnabled(false);
        openURLAction.setEnabled(false);
        launchAction.setEnabled(false);
        textReportAction.setEnabled(false);
        
        try {
            status.setText("JaNeLA - Java Network Launch Analyzer");
            
            for(int i = tabbedPane.getTabCount() - 1; i > 0; i--) {
                tabbedPane.removeTabAt(i);
            }
            getMainJNLPPanel().setAnalyzer(null);

            mainAnalyser = null;
            extensionAnalysers.clear();
            
            if(currentJNLP != null) {
                startProgress();
                new AnalyzerWorker(currentJNLP, true).execute();
            }
        }
        catch (Exception exception) {
            showError(exception);
        }
        finally {
            openFileAction.setEnabled(true);
            openURLAction.setEnabled(true);
            launchAction.setEnabled(currentJNLP != null);
            textReportAction.setEnabled(currentJNLP != null);
        }
    }

    private void showError(Exception exception) {
        exception.printStackTrace();
        
        status.setText(exception.getClass().getSimpleName()
                + ": " + exception.getMessage());
        
        stopProgress(true);
        
        JOptionPane.showMessageDialog(
                this,
                exception.getMessage(),
                "Problem with URL",
                JOptionPane.ERROR_MESSAGE
        );
    }
    
    private void startProgress() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        progressBar.setIndeterminate(true);
        progressCards.show(progressPanel, "progress");
    }

    private void stopProgress(boolean extension) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if(extension) {
            progressBar.setIndeterminate(false);
            progressCards.show(progressPanel, "idle");
        }
    }
    
    private void doLaunch() throws IOException {
        // do something here!
        ProcessBuilder pb = new ProcessBuilder(
            "javaws",
            "-import",
            "-silent",
            "-wait",
            currentJNLP.toString());
        pb.directory(new File("."));
        // combine the output and error streams
        pb = pb.redirectErrorStream(true);

        Process p = pb.start();
        InputStream is = p.getInputStream();
        ByteArrayOutputStream launchResult = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int read = is.read(b);
        while ( read>-1 ) {
            launchResult.write(b,0,read);
            read = is.read(b);
        }
        int processResult = 0;
        try {
            processResult = p.waitFor();
            launchResult.flush();
        } catch(InterruptedException ie) {
            ie.printStackTrace();
        }
        
        JTextArea result = new JTextArea("Exit code: " + processResult + "\n" +
                launchResult.toString());
        result.setOpaque(false);
        JOptionPane.showMessageDialog(this, result);
    }
    
    private class AnalyzerWorker extends SwingWorker<JNLPAnalyser, LaunchError> {

        private final URL url;
        private final boolean main;

        public AnalyzerWorker(URL url, boolean main) {
            this.url = url;
            this.main = main;
        }
        
        @Override
        protected JNLPAnalyser doInBackground() throws Exception {
            JNLPAnalyser analyser = new JNLPAnalyser(url);
            analyser.analyze();
            return analyser;
        }
        
        @Override
        protected void done() {
            if(!isCancelled()) {
                try {
                    JNLPAnalyser analyser = get();
                    String path = getJNLPName(analyser.getURL());
                    
                    ExtensionPanel panel;
                    if(main) {
                        mainAnalyser = analyser;
                        
                        panel = getMainJNLPPanel();
                        tabbedPane.setTitleAt(0, "Main JNLP: " + path);
                    }
                    else {
                        extensionAnalysers.add(analyser);
                        
                        panel = new ExtensionPanel();
                        tabbedPane.addTab("Extension: " + path, panel);
                    }

                    panel.setAnalyzer(analyser);

                    // TODO Check for recursive JNLPs?
                    for(URL extension : analyser.getExtensions()) {
                        new AnalyzerWorker(extension, false).execute();
                    }

                    boolean allExtensionsDone = mainAnalyser != null &&
                        extensionAnalysers.size() == mainAnalyser.getExtensions().size();
                    if(main || allExtensionsDone) {
                        stopProgress(!main || allExtensionsDone);
                    }
                }
                catch (Exception e) {
                    showError(e);
                }
            }
            else {
                stopProgress(true);
            }
        }

        private String getJNLPName(URL url) {
            String path = url.getPath();
            int index = path.lastIndexOf('/');
            if(index != -1) {
                path = path.substring(index + 1);
            }
            index = path.lastIndexOf('\\');
            if(index != -1) {
                path = path.substring(index + 1);
            }

            return path;
        }
    }
}
