/*
 * @(#)Colors.java  1.0  July 9, 2005
 *
 * Copyright (c) 2005 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */

package org.monte.media.image;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
/**
 * Utility methods for ColorModels.
 *
 * @author  Werner Randelshofer
 * @version 1.0 July 9, 2005 Created.
 */
public class ColorModels {
    
    /**
     * Prevent instance creation.
     */
    private ColorModels() {
    }
    
    /**
     * Returns a descriptive string for the provided color model.
     */
    public static String toString(ColorModel cm) {
        StringBuffer buf = new StringBuffer();
        if (cm instanceof DirectColorModel) {
            DirectColorModel dcm = (DirectColorModel) cm;
            buf.append("Direct Color Model ");
            
            int[] masks = dcm.getMasks();
            int totalBits = 0;
            MaskEntry[] entries = new MaskEntry[masks.length];
            for (int i=0; i < masks.length; i++) {
                switch (i) {
                    case 0 : entries[i] = new MaskEntry(masks[i], "R"); break;
                    case 1 : entries[i] = new MaskEntry(masks[i], "G"); break;
                    case 2 : entries[i] = new MaskEntry(masks[i], "B"); break;
                    case 3 : entries[i] = new MaskEntry(masks[i], "A"); break;
                }
                totalBits += entries[i].getBits();
            }
            buf.append(totalBits);
            buf.append(" Bit ");
            Arrays.sort(entries);
            for (int i=0; i < entries.length; i++) {
                buf.append(entries[i]);
            }
        } else if (cm instanceof IndexColorModel) {
            buf.append("Index Color Model ");
            IndexColorModel icm = (IndexColorModel) cm;
            int mapSize = icm.getMapSize();
            buf.append(icm.getMapSize());
            buf.append(" Colors");
        } else {
            buf.append(cm.toString());
        }
        switch (cm.getTransparency()) {
            case Transparency.OPAQUE :
                break;
            case Transparency.BITMASK :
                buf.append(" with Alpha Bitmask");
                break;
            case Transparency.TRANSLUCENT :
                buf.append(" with Alpha Translucency");
                break;
        }
        return buf.toString();
    }
    
    private static class MaskEntry implements Comparable {
        private int mask;
        private int bits;
        private String name;
        public MaskEntry(int mask, String name) {
            this.mask = mask;
            this.name = name;
            
            for (int i=0; i < 32; i++) {
                if (((mask >>> i) & 1) == 1) bits++;
            }
        }
        
        public int getBits() {
            return bits;
        }
        
        public String toString() {
            return name;
        }
        
        public int compareTo(Object o) {
            MaskEntry that = (MaskEntry) o;
            return that.mask - this.mask;
        }
    }
}
