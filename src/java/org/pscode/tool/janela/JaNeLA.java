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
package org.pscode.tool.janela;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;

import org.pscode.tool.janela.gui.JaNeLaPanel;

public class JaNeLA extends JPanel {

	public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                
//                try {
//                for(LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
//                    if("Nimbus".equals(info.getName())) {
//                            UIManager.setLookAndFeel(info.getClassName());
//                        break;
//                    }
//                }
//                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//                }
//                catch(Exception e) {
//                    e.printStackTrace();
//                }
                
                final JFrame frame = new JFrame("JaNeLA");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                createGUI(frame.getRootPane());
                
                JMenu fileMenu = frame.getJMenuBar().getMenu(0);
                fileMenu.addSeparator();
                JMenuItem exitMenuItem = new JMenuItem("Exit");
                exitMenuItem.addActionListener(new ActionListener(){
                        public void actionPerformed(ActionEvent ae) {
                            frame.dispose();
                        }
                    });
                exitMenuItem.setMnemonic('x');
                fileMenu.add(exitMenuItem);
                
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
    
	private static void createGUI(JRootPane rootPane) {
	    JaNeLaPanel panel = new JaNeLaPanel();
        JMenuBar menuBar = new JMenuBar();
        createMenu(menuBar, "File", 'f', panel.getFileActions());
        createMenu(menuBar, "Report", 'r', panel.getReportActions());
        createMenu(menuBar, "Help", 'h', panel.getHelpActions());
        
        rootPane.setJMenuBar(menuBar);
        rootPane.getContentPane().add(panel);
    }

	private static void createMenu(JMenuBar menuBar,
	        String name, char mnemonic,
            List<? extends Action> actions) {
	    
	    JMenu menu = new JMenu(name);
        menu.setMnemonic(mnemonic);
        for(Action action : actions) {
            if(action == null) {
                menu.addSeparator();
            }
            else {
                menu.add(action);
            }
        }
        menuBar.add(menu);
    }
}
