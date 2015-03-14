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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import org.pscode.tool.janela.JNLPAnalyser;
import org.pscode.tool.janela.LaunchError;

public class ExtensionPanel extends JPanel {

    private JTextArea content;
    private JSplitPane splitPane;
    private DefaultListModel errorList;
    private JList results;
    
    public ExtensionPanel() {
        initializePanel();
    }
    
    private void initializePanel() {
        content = new JTextArea(20, 60);
        content.setEditable(false);

        errorList = new DefaultListModel();
        results = new JList(errorList);
        results.setCellRenderer(new LaunchErrorCellRenderer());
        results.setVisibleRowCount(8);
        
        splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(
                content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED ),
            new JScrollPane(
                results,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED )
                );
        
        addComponentListener(new ComponentAdapter(){
                @Override
                public void componentShown(ComponentEvent ce) {
                    setDividerLocation();
                }
            });
        
        setLayout(new BorderLayout());
        add( splitPane, BorderLayout.CENTER );
    }
    
    private void setDividerLocation() {
        splitPane.setDividerLocation(0.7);
    }
    
    /** Load a file into the editing area.
    @param is An input stream pointing to the desired file. */
    private void loadFile(URL url) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                new InputStreamReader(url.openStream()));
            content.read(reader, "text");
            content.setCaretPosition(0);
        }
        catch (IOException e) {
            content.setText(e.getMessage());
        }
        finally {
            if(reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    // ignore;
                }
            }
        }
    }

    public void setAnalyzer(JNLPAnalyser analyser) {
        content.setText("");
        errorList.clear();
        if(analyser != null) {
            loadFile(analyser.getURL());
            
            String result;
            if ( analyser.isXMLValid() ) {
                result = "This document is valid according to the schema.  " +
                "The data might still be wrong, but at least it is valid!";
            } else {
                result = "This document is invalid according to the schema.  " +
                "Click the errors in the list for more details.  " +
                "It pays to fix the top-most errors first, as later errors might " +
                "be 'phantoms' that disappear as soon as earlier errors are fixed.";
            }
            // TODO Do something with this result text?
            System.out.println(result);
            
            List<LaunchError> errors = new ArrayList<LaunchError>(analyser.getErrors());
//            Collections.sort(sortedErrors, new Comparator<LaunchError>()
//            {
//              @Override
//              public int compare(LaunchError theO1, LaunchError theO2)
//              {
//                return theO1.getLevel().ordinal() - theO2.getLevel().ordinal();
//              }
//            });
            
            for(LaunchError error : errors) {
                errorList.addElement(error);
            }
        }
    }
}
