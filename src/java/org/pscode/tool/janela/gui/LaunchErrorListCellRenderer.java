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

import java.awt.Color;
import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import org.pscode.tool.janela.LaunchError;

class LaunchErrorCellRenderer extends DefaultListCellRenderer {

    private static final int LOW = 203;
    private static final int HI = 255;

    private static final Color OPTIMIZE = new Color(LOW,HI,LOW);
    private static final Color WARNING = new Color(HI,HI,LOW);
    private static final Color ERROR = new Color(HI,LOW,LOW);
    private static final Color FATAL = new Color(HI,LOW,HI);

    private final Border raisedBevel = new BevelBorder(BevelBorder.RAISED);
    private final Border loweredBevel = new BevelBorder(BevelBorder.LOWERED);

    public LaunchErrorCellRenderer() {
        setOpaque(true);
    }
    
    @Override
    public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus) {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        
        LaunchError launchError = (LaunchError)value;
        setText(launchError.getMessage());
        setBorder( (isSelected ? raisedBevel : loweredBevel) );
        switch (launchError.getLevel()) {
        case OPTIMIZE:
            setBackground(OPTIMIZE);
            break;
        case WARNING:
            setBackground(WARNING);
            break;
        case ERROR:
            setBackground(ERROR);
            break;
        case FATAL:
            setBackground(FATAL);
            break;
        default:
            assert false : "Unknown error level: " + launchError.getLevel();
        }

        return this;
    }
}

