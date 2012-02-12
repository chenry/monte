/**
 * @(#)Main.java  1.2  2009-08-29
 *
 * Copyright (c) 2008-2009 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.avidemo;

import org.monte.media.Buffer;
import org.monte.media.math.Rational;
import org.monte.media.avi.AVIWriter;
import org.monte.media.Format;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.util.Random;
import static org.monte.media.VideoFormatKeys.*;

/**
 * Main.
 *
 * @author Werner Randelshofer
 * @version 1.1 2009-08-29 Added raw output.
 * <br>1.0 2008-00-15 Created.
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            test(new File("avidemo-jpg.avi"),new Format(EncodingKey,ENCODING_AVI_MJPG,DepthKey,24,QualityKey,1f));
            test(new File("avidemo-png.avi"),new Format(EncodingKey,ENCODING_AVI_PNG,DepthKey,24));
            test(new File("avidemo-raw24.avi"), new Format(EncodingKey,ENCODING_AVI_DIB,DepthKey,24));
            test(new File("avidemo-raw8.avi"), new Format(EncodingKey,ENCODING_AVI_DIB,DepthKey,8));
            test(new File("avidemo-rle8.avi"), new Format(EncodingKey,ENCODING_AVI_RLE,DepthKey,8));
            test(new File("avidemo-tscc8.avi"), new Format(EncodingKey,ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,DepthKey,8));
            test(new File("avidemo-tscc24.avi"), new Format(EncodingKey,ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,DepthKey,24));
            //test(new File("avidemo-rle4.avi"), AVIOutputStreamOLD.AVIVideoFormat.RLE, 4, 1f);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void test(File file, Format format) throws IOException {
        System.out.println("Writing " + file);
        AVIWriter out = null;
        Graphics2D g = null;
        format=new Format(MediaTypeKey,MediaType.VIDEO,FrameRateKey,new Rational(30,1),WidthKey,320,HeightKey,160).append(format);

        try {
            out = new AVIWriter(file);
            out.addTrack(format);

            int depth=format.get(DepthKey);
            Random rnd = new Random(0); // use seed 0 to get reproducable output
            BufferedImage img;
            switch (depth) {
                case 24:
                default: {
                    img = new BufferedImage(320, 160, BufferedImage.TYPE_INT_RGB);
                    break;
                }
                case 8: {
                    byte[] red = new byte[256];
                    byte[] green = new byte[256];
                    byte[] blue = new byte[256];
                    for (int i = 0; i < 255; i++) {
                        red[i] = (byte) rnd.nextInt(256);
                        green[i] = (byte) rnd.nextInt(256);
                        blue[i] = (byte) rnd.nextInt(256);
                    }
                    rnd.setSeed(0); // set back to 0 for reproducable output
                    IndexColorModel palette=new IndexColorModel(8, 256, red, green, blue);
                    img = new BufferedImage(320, 160, BufferedImage.TYPE_BYTE_INDEXED, palette);
                    out.setPalette(0, palette);
                    break;
                }
                case 4: {
                    byte[] red = new byte[16];
                    byte[] green = new byte[16];
                    byte[] blue = new byte[16];
                    for (int i = 0; i < 15; i++) {
                        red[i] = (byte) rnd.nextInt(16);
                        green[i] = (byte) rnd.nextInt(16);
                        blue[i] = (byte) rnd.nextInt(16);
                    }
                    rnd.setSeed(0); // set back to 0 for reproducable output
                    IndexColorModel palette= new IndexColorModel(4, 16, red, green, blue);
                    img = new BufferedImage(320, 160, BufferedImage.TYPE_BYTE_INDEXED,palette);
                    out.setPalette(0, palette);
                    break;
                }
            }
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setBackground(Color.WHITE);
            g.clearRect(0, 0, img.getWidth(), img.getHeight());

            Buffer buf=new Buffer();
            buf.format=new Format(EncodingKey,ENCODING_BUFFERED_IMAGE,DataClassKey,BufferedImage.class).append(format);
            buf.sampleDuration=format.get(FrameRateKey).inverse();
            buf.data=img;
            for (int i = 0; i < 100; i++) {
                g.setColor(new Color(rnd.nextInt()));
                //g.fillRect(rnd.nextInt(img.getWidth() - 30), rnd.nextInt(img.getHeight() - 30), 30, 30);
                g.fillOval(rnd.nextInt(img.getWidth() - 30), rnd.nextInt(img.getHeight() - 30), 30, 30);
                out.write(0,buf);
            }

        } finally {
            if (g != null) {
                g.dispose();
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
