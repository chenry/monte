/*
 * @(#)ScreenRecorderMain.java  
 *
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.screenrecorder;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.monte.media.AudioFormatKeys.SampleRateKey;
import static org.monte.media.AudioFormatKeys.SampleSizeInBitsKey;
import static org.monte.media.FormatKeys.EncodingKey;
import static org.monte.media.FormatKeys.FrameRateKey;
import static org.monte.media.FormatKeys.KeyFrameIntervalKey;
import static org.monte.media.FormatKeys.MIME_AVI;
import static org.monte.media.FormatKeys.MIME_QUICKTIME;
import static org.monte.media.FormatKeys.MediaTypeKey;
import static org.monte.media.FormatKeys.MimeTypeKey;
import static org.monte.media.VideoFormatKeys.COMPRESSOR_NAME_QUICKTIME_ANIMATION;
import static org.monte.media.VideoFormatKeys.COMPRESSOR_NAME_QUICKTIME_JPEG;
import static org.monte.media.VideoFormatKeys.COMPRESSOR_NAME_QUICKTIME_PNG;
import static org.monte.media.VideoFormatKeys.COMPRESSOR_NAME_QUICKTIME_RAW;
import static org.monte.media.VideoFormatKeys.CompressorNameKey;
import static org.monte.media.VideoFormatKeys.DepthKey;
import static org.monte.media.VideoFormatKeys.ENCODING_AVI_DIB;
import static org.monte.media.VideoFormatKeys.ENCODING_AVI_MJPG;
import static org.monte.media.VideoFormatKeys.ENCODING_AVI_PNG;
import static org.monte.media.VideoFormatKeys.ENCODING_AVI_RLE;
import static org.monte.media.VideoFormatKeys.ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE;
import static org.monte.media.VideoFormatKeys.ENCODING_QUICKTIME_ANIMATION;
import static org.monte.media.VideoFormatKeys.ENCODING_QUICKTIME_JPEG;
import static org.monte.media.VideoFormatKeys.ENCODING_QUICKTIME_PNG;
import static org.monte.media.VideoFormatKeys.ENCODING_QUICKTIME_RAW;
import static org.monte.media.VideoFormatKeys.QualityKey;

import java.awt.AWTException;
import java.awt.Frame;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.prefs.Preferences;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.monte.media.Format;
import org.monte.media.FormatKeys.MediaType;
import org.monte.media.gui.Worker;
import org.monte.media.math.Rational;

/**
 * ScreenRecorderMain.
 *
 * @author Werner Randelshofer
 * @version $Id: ScreenRecorderMain.java 150 2012-01-13 13:08:26Z werner $
 */
public class ScreenRecorderMain extends javax.swing.JFrame {

    private class Handler implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            ScreenRecorder r = screenRecorder;
            if (r != null && r.getState() == ScreenRecorder.State.FAILED) {
                recordingFailed();
            }
        }
    }
    private Handler handler = new Handler();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss");
    private volatile Worker recorder;
    private ScreenRecorder screenRecorder;
    private int depth;
    private int format;
    private int encoding;
    private int cursor;
    private int audio;
    private double screenRate;
    private double mouseRate;

    private static class AudioItem {

        private String title;
        private int sampleRate;
        private int bitsPerSample;

        public AudioItem(String title, int sampleRate, int bitsPerSample) {
            this.title = title;
            this.sampleRate = sampleRate;
            this.bitsPerSample = bitsPerSample;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Creates new form ScreenRecorderMain */
    public ScreenRecorderMain() {
        initComponents();

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            infoLabel.setText(infoLabel.getText().replaceAll("\"Movies\"", "\"Videos\""));
        }

        ((JPanel) getContentPane()).setBorder(new EmptyBorder(12, 20, 20, 20));
        pack();

        final Preferences prefs = Preferences.userNodeForPackage(ScreenRecorderMain.class);
        depth = min(max(0, prefs.getInt("ScreenRecording.colorDepth", 3)), colorsChoice.getItemCount() - 1);
        colorsChoice.setSelectedIndex(depth);
        format = min(max(0, prefs.getInt("ScreenRecording.format", 0)), formatChoice.getItemCount() - 1);
        formatChoice.setSelectedIndex(format);
        encoding = min(max(0, prefs.getInt("ScreenRecording.encoding", 0)), encodingChoice.getItemCount() - 1);
        encodingChoice.setSelectedIndex(encoding);
        cursor = min(max(0, prefs.getInt("ScreenRecording.cursor", 1)), cursorChoice.getItemCount() - 1);
        cursorChoice.setSelectedIndex(cursor);

        screenRate = prefs.getDouble("ScreenRecording.screenRate", 15);
        SpinnerNumberModel screenRateModel = new SpinnerNumberModel(screenRate, 1, 30, 1);
        screenRateField.setModel(screenRateModel);

        mouseRate = prefs.getDouble("ScreenRecording.mouseRate", 30);
        SpinnerNumberModel mouseRateModel = new SpinnerNumberModel(mouseRate, 1, 30, 1);
        mouseRateField.setModel(mouseRateModel);


        // FIXME - 8-bit recording is currently broken
        audioChoice.setModel(new DefaultComboBoxModel(new Object[]{
                    new AudioItem("No Audio", 0, 0),
                    //new AudioItem("8.000 Hz, 8-bit",8000,8),
                    new AudioItem("8.000 Hz", 8000, 16),
                    //new AudioItem("11.025 Hz, 8-bit",11025,8),
                    new AudioItem("11.025 Hz", 11025, 16),
                    //new AudioItem("22.050 Hz, 8-bit",22050,8),
                    new AudioItem("22.050 Hz", 22050, 16),
                    //new AudioItem("44.100 Hz, 8-bit",44100,8),
                    new AudioItem("44.100 Hz", 44100, 16),}));
        audio = prefs.getInt("ScreenRecording.audio", 0);
        audioChoice.setSelectedIndex(audio);

        getRootPane().setDefaultButton(startStopButton);
        updateEncodingChoice();

    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        demoLabel = new javax.swing.JLabel();
        formatLabel = new javax.swing.JLabel();
        formatChoice = new javax.swing.JComboBox();
        colorsLabel = new javax.swing.JLabel();
        colorsChoice = new javax.swing.JComboBox();
        infoLabel = new javax.swing.JLabel();
        startStopButton = new javax.swing.JButton();
        mouseLabel = new javax.swing.JLabel();
        cursorChoice = new javax.swing.JComboBox();
        audioLabel = new javax.swing.JLabel();
        audioChoice = new javax.swing.JComboBox();
        screenRateLabel = new javax.swing.JLabel();
        screenRateField = new javax.swing.JSpinner();
        mouseRateLabel = new javax.swing.JLabel();
        mouseRateField = new javax.swing.JSpinner();
        encodingLabel = new javax.swing.JLabel();
        encodingChoice = new javax.swing.JComboBox();

        FormListener formListener = new FormListener();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Screen Recorder");
        addWindowListener(formListener);

        demoLabel.setText("<html><b>This is a demo of the Monte Media Library.</b><br>Copyright © 2012 Werner Randelshofer. All rights reserved.<br> This software can be licensed under Creative Commons Attribution 3.0.");

        formatLabel.setText("Format:");

        formatChoice.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "AVI", "QuickTime" }));
        formatChoice.addActionListener(formListener);

        colorsLabel.setText("Colors:");

        colorsChoice.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Hundreds", "Thousands", "Millions" }));

        infoLabel.setFont(new java.awt.Font("Lucida Grande", 0, 11));
        infoLabel.setText("<html>When you press the Start button, this window will be minized before the recording starts.<br> \nTo stop the recording restore this window.<br>  The recording will be stored in the folder \"Movies\" inside your home folder.<br> A new file will be created every hour or when the file size limit is reached.");

        startStopButton.setText("Start");
        startStopButton.addActionListener(formListener);

        mouseLabel.setText("Mouse:");

        cursorChoice.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "No Cursor", "Black Cursor", "White Cursor" }));

        audioLabel.setText("Audio:");

        audioChoice.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "No Audio", "44.100 kHz" }));

        screenRateLabel.setText("Screen Rate:");

        mouseRateLabel.setText("Mouse Rate:");

        encodingLabel.setText("Encoding:");

        encodingChoice.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Screen Capture", "Run Length", "None", "PNG", "JPEG 100 %", "JPEG   50 %" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(colorsLabel)
                    .addComponent(mouseLabel)
                    .addComponent(formatLabel)
                    .addComponent(audioLabel))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(formatChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(colorsChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cursorChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(audioChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 35, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(screenRateLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(mouseRateLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(encodingLabel))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(encodingChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(screenRateField, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(mouseRateField, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(34, 34, 34))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(demoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 490, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(startStopButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(infoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 490, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(46, Short.MAX_VALUE))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {audioChoice, colorsChoice, cursorChoice, formatChoice});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(demoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(30, 30, 30)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(formatChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(formatLabel))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(encodingChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(encodingLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(colorsChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(colorsLabel))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(screenRateField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(screenRateLabel))
                        .addGap(1, 1, 1)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(cursorChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(mouseLabel))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(mouseRateField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(mouseRateLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(audioChoice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(audioLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 51, Short.MAX_VALUE)
                .addComponent(infoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(startStopButton)
                .addContainerGap())
        );

        pack();
    }

    // Code for dispatching events from components to event handlers.

    private class FormListener implements java.awt.event.ActionListener, java.awt.event.WindowListener {
        FormListener() {}
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            if (evt.getSource() == formatChoice) {
                ScreenRecorderMain.this.formatChoicePerformed(evt);
            }
            else if (evt.getSource() == startStopButton) {
                ScreenRecorderMain.this.startStopPerformed(evt);
            }
        }

        public void windowActivated(java.awt.event.WindowEvent evt) {
        }

        public void windowClosed(java.awt.event.WindowEvent evt) {
        }

        public void windowClosing(java.awt.event.WindowEvent evt) {
            if (evt.getSource() == ScreenRecorderMain.this) {
                ScreenRecorderMain.this.formWindowClosing(evt);
            }
        }

        public void windowDeactivated(java.awt.event.WindowEvent evt) {
        }

        public void windowDeiconified(java.awt.event.WindowEvent evt) {
            if (evt.getSource() == ScreenRecorderMain.this) {
                ScreenRecorderMain.this.formWindowDeiconified(evt);
            }
        }

        public void windowIconified(java.awt.event.WindowEvent evt) {
        }

        public void windowOpened(java.awt.event.WindowEvent evt) {
        }
    }// </editor-fold>//GEN-END:initComponents

    private void updateValues() {
        Preferences prefs = Preferences.userNodeForPackage(ScreenRecorderMain.class);
        format = formatChoice.getSelectedIndex();
        prefs.putInt("ScreenRecording.format", format);
        encoding = encodingChoice.getSelectedIndex();
        prefs.putInt("ScreenRecording.encoding", encoding);
        depth = colorsChoice.getSelectedIndex();
        prefs.putInt("ScreenRecording.colorDepth", depth);
        cursor = cursorChoice.getSelectedIndex();
        prefs.putInt("ScreenRecording.cursor", cursor);
        audio = audioChoice.getSelectedIndex();
        prefs.putInt("ScreenRecording.audio", audio);
        if (screenRateField.getValue() instanceof Double) {
            screenRate = (Double) screenRateField.getValue();
            prefs.putDouble("ScreenRecording.screenRate", screenRate);
        }
        if (mouseRateField.getValue() instanceof Double) {
            mouseRate = (Double) mouseRateField.getValue();
            prefs.putDouble("ScreenRecording.mouseRate", mouseRate);
        }
    }

    private void start() throws IOException, AWTException {
        updateValues();


        if (screenRecorder == null) {
            String mimeType;
            String videoFormatName, compressorName;
            float quality=1.0f;
            int bitDepth;
            switch (depth) {
                default:
                case 0:
                    bitDepth = 8;
                    break;
                case 1:
                    bitDepth = 16;
                    break;
                case 2:
                    bitDepth = 24;
                    break;
            }
            switch (format) {
                default:
                case 0:
                    mimeType = MIME_AVI;
                    switch (encoding) {
                        case 0:
                        default:
                            videoFormatName = compressorName = ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE;
                            break;
                        case 1:
                            videoFormatName = compressorName = ENCODING_AVI_RLE;
                            break;
                        case 2:
                            videoFormatName = compressorName = ENCODING_AVI_DIB;
                            break;
                        case 3:
                            videoFormatName = compressorName = ENCODING_AVI_PNG;
                            break;
                        case 4:
                            videoFormatName = compressorName = ENCODING_AVI_MJPG;
                            break;
                        case 5:
                            videoFormatName = compressorName = ENCODING_AVI_MJPG;
                            quality=0.5f;
                            break;
                    }
                    break;
                case 1:
                    mimeType = MIME_QUICKTIME;
                    switch (encoding) {
                        case 0:
                        default:
                            if (bitDepth == 8) {
                                // FIXME - 8-bit Techsmith Screen Capture is broken
                                videoFormatName = ENCODING_QUICKTIME_ANIMATION;
                                compressorName = COMPRESSOR_NAME_QUICKTIME_ANIMATION;
                            } else {
                                videoFormatName = compressorName = ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE;
                                compressorName = ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE;
                            }
                            break;
                        case 1:
                            videoFormatName = ENCODING_QUICKTIME_ANIMATION;
                            compressorName = COMPRESSOR_NAME_QUICKTIME_ANIMATION;
                            break;
                        case 2:
                            videoFormatName = ENCODING_QUICKTIME_RAW;
                            compressorName = COMPRESSOR_NAME_QUICKTIME_RAW;
                            break;
                        case 3:
                            videoFormatName = ENCODING_QUICKTIME_PNG;
                            compressorName = COMPRESSOR_NAME_QUICKTIME_PNG;
                            break;
                        case 4:
                            videoFormatName = ENCODING_QUICKTIME_JPEG;
                            compressorName = COMPRESSOR_NAME_QUICKTIME_JPEG;
                            break;
                        case 5:
                            videoFormatName = ENCODING_QUICKTIME_JPEG;
                            compressorName = COMPRESSOR_NAME_QUICKTIME_JPEG;
                            quality=0.5f;
                            break;
                    }
                    break;
            }

            int audioRate;
            int audioBitsPerSample;
            AudioItem item = (AudioItem) audioChoice.getItemAt(audio);
            audioRate = item.sampleRate;
            audioBitsPerSample = item.bitsPerSample;
            String crsr;
            switch (cursor) {
                default:
                case 0:
                    crsr = null;
                    break;
                case 1:
                    crsr = ScreenRecorder.ENCODING_BLACK_CURSOR;
                    break;
                case 2:
                    crsr = ScreenRecorder.ENCODING_WHITE_CURSOR;
                    break;
            }

            screenRecorder = new ScreenRecorder(getGraphicsConfiguration(),
                    // the file format:
                    new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, mimeType),
                    //
                    // the output format for screen capture:
                    new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, videoFormatName,
                    CompressorNameKey, compressorName,
                    DepthKey, bitDepth, FrameRateKey, Rational.valueOf(screenRate),
                    QualityKey, quality,
                    KeyFrameIntervalKey, (int) (screenRate * 60) // one keyframe per minute is enough
                    ),
                    //
                    // the output format for mouse capture:
                    crsr == null ? null : new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, crsr,
                    FrameRateKey, Rational.valueOf(mouseRate)),
                    //
                    // the output format for audio capture:
                    audioRate == 0 ? null : new Format(MediaTypeKey, MediaType.AUDIO,
                    //EncodingKey, audioFormatName,
                    SampleRateKey, Rational.valueOf(audioRate),
                    SampleSizeInBitsKey, audioBitsPerSample));

            startStopButton.setText("Stop");
            screenRecorder.addChangeListener(handler);
            screenRecorder.start();
        }
    }

    private void stop() {
        if (screenRecorder != null) {
            final ScreenRecorder r = screenRecorder;
            startStopButton.setEnabled(false);
            screenRecorder = null;
            new Worker() {

                @Override
                protected Object construct() throws Exception {
                    r.stop();
                    return null;
                }

                @Override
                protected void finished() {
                    ScreenRecorder.State state = r.getState();
                    startStopButton.setEnabled(true);
                    startStopButton.setText("Start");
                }
            }.start();
        }
    }

    private void recordingFailed() {
        if (screenRecorder != null) {
            screenRecorder = null;
            startStopButton.setEnabled(true);
            startStopButton.setText("Start");
            setExtendedState(Frame.NORMAL);
                        JOptionPane.showMessageDialog(ScreenRecorderMain.this,
                                "<html><b>Sorry. Screen Recording failed.</b>",
                                "Screen Recorder", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void updateEncodingChoice() {
        int index=encodingChoice.getSelectedIndex();
        switch (formatChoice.getSelectedIndex()) {
            case 0: // AVI
                encodingChoice.setModel(
                new javax.swing.DefaultComboBoxModel(new String[] { "Screen Capture", "Run Length", "None", "PNG", "JPEG 100 %","JPEG  50 %" })
                        );
                break;
            case 1: // QuickTime
                encodingChoice.setModel(
                new javax.swing.DefaultComboBoxModel(new String[] { "Screen Capture", "Animation", "None", "PNG", "JPEG 100 %","JPEG  50 %" })
                        );
                break;
        }
        encodingChoice.setSelectedIndex(index);
    }


    private void startStopPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startStopPerformed
        if (screenRecorder == null) {
            setExtendedState(Frame.ICONIFIED);
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    try {
                        start();
                    } catch (Throwable t) {
                        t.printStackTrace();
                        setExtendedState(Frame.NORMAL);
                        JOptionPane.showMessageDialog(ScreenRecorderMain.this,
                                "<html><b>Sorry. Screen Recording failed.</b><br>" + t.getMessage(),
                                "Screen Recorder", JOptionPane.ERROR_MESSAGE);
                        stop();
                    }
                }
            });
        } else {
            stop();
        }
    }//GEN-LAST:event_startStopPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        stop();
        dispose();
    }//GEN-LAST:event_formWindowClosing

    private void formWindowDeiconified(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeiconified
        stop();
    }//GEN-LAST:event_formWindowDeiconified

    private void formatChoicePerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_formatChoicePerformed
        updateEncodingChoice();
    }//GEN-LAST:event_formatChoicePerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new ScreenRecorderMain().setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox audioChoice;
    private javax.swing.JLabel audioLabel;
    private javax.swing.JComboBox colorsChoice;
    private javax.swing.JLabel colorsLabel;
    private javax.swing.JComboBox cursorChoice;
    private javax.swing.JLabel demoLabel;
    private javax.swing.JComboBox encodingChoice;
    private javax.swing.JLabel encodingLabel;
    private javax.swing.JComboBox formatChoice;
    private javax.swing.JLabel formatLabel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JLabel mouseLabel;
    private javax.swing.JSpinner mouseRateField;
    private javax.swing.JLabel mouseRateLabel;
    private javax.swing.JSpinner screenRateField;
    private javax.swing.JLabel screenRateLabel;
    private javax.swing.JButton startStopButton;
    // End of variables declaration//GEN-END:variables
}
