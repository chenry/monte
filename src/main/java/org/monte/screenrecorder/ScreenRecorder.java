/*
 * @(#)ScreenRecorder.java 
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
import static org.monte.media.AudioFormatKeys.ByteOrderKey;
import static org.monte.media.AudioFormatKeys.ChannelsKey;
import static org.monte.media.AudioFormatKeys.ENCODING_QUICKTIME_TWOS_PCM;
import static org.monte.media.AudioFormatKeys.SampleRateKey;
import static org.monte.media.AudioFormatKeys.SampleSizeInBitsKey;
import static org.monte.media.AudioFormatKeys.SignedKey;
import static org.monte.media.AudioFormatKeys.fromAudioFormat;
import static org.monte.media.BufferFlag.SAME_DATA;
import static org.monte.media.FormatKeys.EncodingKey;
import static org.monte.media.FormatKeys.FrameRateKey;
import static org.monte.media.FormatKeys.MIME_AVI;
import static org.monte.media.FormatKeys.MIME_QUICKTIME;
import static org.monte.media.FormatKeys.MediaTypeKey;
import static org.monte.media.FormatKeys.MimeTypeKey;
import static org.monte.media.VideoFormatKeys.COMPRESSOR_NAME_QUICKTIME_ANIMATION;
import static org.monte.media.VideoFormatKeys.CompressorNameKey;
import static org.monte.media.VideoFormatKeys.DepthKey;
import static org.monte.media.VideoFormatKeys.ENCODING_BUFFERED_IMAGE;
import static org.monte.media.VideoFormatKeys.ENCODING_QUICKTIME_ANIMATION;
import static org.monte.media.VideoFormatKeys.HeightKey;
import static org.monte.media.VideoFormatKeys.WidthKey;

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.swing.SwingUtilities;

import org.monte.media.AudioFormatKeys;
import org.monte.media.Buffer;
import org.monte.media.Codec;
import org.monte.media.Format;
import org.monte.media.FormatKeys.MediaType;
import org.monte.media.MovieWriter;
import org.monte.media.Registry;
import org.monte.media.avi.AVIWriter;
import org.monte.media.beans.AbstractStateModel;
import org.monte.media.color.Colors;
import org.monte.media.image.Images;
import org.monte.media.math.Rational;
import org.monte.media.quicktime.QuickTimeWriter;

/**
 * A screen recorder written in pure Java.
 * <p>
 * Captures the screen, the mouse cursor and audio.
 * <p>
 * This recorder uses four threads. Three capture threads for screen, cursor and
 * audio, and one output thread for the movie writer.
 * 
 *
 * @author Werner Randelshofer
 * @version $Id: ScreenRecorder.java 136 2011-12-26 10:10:26Z werner $
 */
public class ScreenRecorder extends AbstractStateModel {

    enum State {
        DONE, FAILED, RECORDING
    }
    private State state=State.DONE;
    
    /** 
     * "Encoding" for black mouse cursor. */
    public final static String ENCODING_BLACK_CURSOR = "black";
    /** 
     * "Encoding" for white mouse cursor. */
    public final static String ENCODING_WHITE_CURSOR = "white";
    /** 
     * The file format. "AVI" or "QuickTime" */
    private Format fileFormat;
    /** 
     * The video format for cursor capture. "black" or "white". */
    private Format mouseFormat;
    /** 
     * The video format for screen capture. */
    private Format screenFormat;
    /**
     * The audio format for audio capture. */
    private Format audioFormat;
    /**
     * The writer for the movie file. */
    private MovieWriter w;
    /**
     * The start time of the recording. */
    private long startTime;
    /**
     * The stoop time of the recording. */
    private long stopTime;
    /**
     * The time the previous screen frame was captured. */
    private long prevScreenCaptureTime;
    /**
     * The AWT Robot which we use for capturing the screen. */
    private Robot robot;
    /**
     * The bounds of the graphics device that we capture with AWT Robot. */
    private Rectangle rect;
    /**
     * Holds the screen capture made with AWT Robot. */
    private BufferedImage screenCapture;
    /**
     * Holds the mouse captures made with {@code MouseInfo}. */
    private ArrayBlockingQueue<MouseCapture> mouseCaptures;
    /** Holds the composed image (screen capture and super-imposed mouse cursor).
     * This is the image that is written into the video track of the file.
     */
    private BufferedImage videoImg;
    /**
     * Graphics object for drawing into {@code videoImg}. */
    private Graphics2D videoGraphics;
    /**
     * Timer for screen captures. */
    private ScheduledThreadPoolExecutor screenTimer;
    /**
     * Timer for mouse captures. */
    private ScheduledThreadPoolExecutor mouseTimer;
    /**
     * Mouse cursor. */
    private BufferedImage cursorImg;
    /**
     * Hot spot of the mouse cursor in cursorImg. */
    private Point cursorOffset = new Point(-8, -5);
    /**
     * Object for thread synchronization. */
    private final Object sync = new Object();
    /**
     * This variable holds the Runnable for audio capture. */
    private volatile Thread audioRunner;
    /**
     * With QuickTime, we can use a variable frame rate. We don't want to
     * have frames with a sampleDuration longer than 1000 milliseconds though.
     */
    private long maxFrameDuration = 1000;
    private volatile Thread writerThread;
    private ArrayBlockingQueue<Buffer> writerQueue;
    /** This codec encodes a video frame. */
    private Codec frameEncoder;
    /** outputTime and ffrDuration are needed for conversion of the
     * video stream from variable frame rate to fixed frame rate. */
    private Rational outputTime;
    private Rational ffrDuration;

    /** Creates a screen recorder.
     *
     * @param cfg Graphics configuration of the capture screen.
     */
    public ScreenRecorder(GraphicsConfiguration cfg) throws IOException, AWTException {
        this(cfg,
                // the file format
                new Format(MediaTypeKey, MediaType.FILE,
                MimeTypeKey, MIME_QUICKTIME),
                //
                // the output format for screen capture
                new Format(MediaTypeKey, MediaType.VIDEO,
                EncodingKey, ENCODING_QUICKTIME_ANIMATION,
                CompressorNameKey, COMPRESSOR_NAME_QUICKTIME_ANIMATION,
                DepthKey, 24, FrameRateKey, new Rational(15, 1)),
                //
                // the output format for mouse capture 
                new Format(MediaTypeKey, MediaType.VIDEO,
                EncodingKey, ENCODING_BLACK_CURSOR,
                FrameRateKey, new Rational(30, 1)),
                //
                // the output format for audio capture 
                new Format(MediaTypeKey, MediaType.AUDIO,
                EncodingKey, ENCODING_QUICKTIME_TWOS_PCM,
                FrameRateKey, new Rational(48000, 1),
                SampleSizeInBitsKey, 16,
                ChannelsKey, 2, SampleRateKey, new Rational(48000, 1),
                SignedKey, true, ByteOrderKey, ByteOrder.BIG_ENDIAN));
    }

    /** Creates a screen recorder.
     *
     * @param cfg Graphics configuration of the capture screen.
     * @param fileFormat The file format "AVI" or "QuickTime".
     * @param screenFormat The video format for screen capture.
     * @param mouseFormat The video format for mouse capture. The {@code EncodingKey}
     * must be ENCODING_BLACK_CURSOR or ENCODING_WHITE_CURSOR. The {@code SampleRateKey} can be
     * independent from the {@code screenFormat}. Specify null if you
     * don't want to capture the mouse cursor.
     * @param audioFormat  The audio format for audio capture. Specify null
     * if you don't want audio capture.
     */
    public ScreenRecorder(GraphicsConfiguration cfg,
            Format fileFormat,
            Format screenFormat,
            Format mouseFormat,
            Format audioFormat) throws IOException, AWTException {

        this.fileFormat = fileFormat;
        this.screenFormat = screenFormat;
        this.mouseFormat = mouseFormat;
        if (this.mouseFormat == null) {
            this.mouseFormat = new Format(FrameRateKey, new Rational(0, 0), EncodingKey, ENCODING_BLACK_CURSOR);
        }
        this.audioFormat = audioFormat;

        rect = cfg.getBounds();
        robot = new Robot(cfg.getDevice());
        if (screenFormat.get(DepthKey) == 24) {
            videoImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
        } else if (screenFormat.get(DepthKey) == 16) {
            videoImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_USHORT_555_RGB);
        } else if (screenFormat.get(DepthKey) == 8) {
            videoImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_BYTE_INDEXED, Colors.createMacColors());
        } else {
            throw new IOException("Unsupported color depth " + screenFormat.get(DepthKey));
        }
        videoGraphics = videoImg.createGraphics();
        videoGraphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
        videoGraphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
        videoGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        if (mouseFormat != null && mouseFormat.get(FrameRateKey).intValue() > 0) {
            mouseCaptures = new ArrayBlockingQueue<MouseCapture>(mouseFormat.get(FrameRateKey).intValue());
            if (this.mouseFormat.get(EncodingKey).equals(ENCODING_BLACK_CURSOR)) {
                cursorImg = Images.toBufferedImage(Images.createImage(ScreenRecorder.class, "/org/monte/media/images/Cursor.black.png"));
            } else {
                cursorImg = Images.toBufferedImage(Images.createImage(ScreenRecorder.class, "/org/monte/media/images/Cursor.white.png"));
            }
        }
        createMovieWriter();
    }

    protected void createMovieWriter() throws IOException {
        File folder;
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            folder = new File(System.getProperty("user.home") + File.separator + "Videos");
        } else {
            folder = new File(System.getProperty("user.home") + File.separator + "Movies");
        }

        if (!folder.exists()) {
            folder.mkdirs();
        } else if (!folder.isDirectory()) {
            throw new IOException("\"" + folder + "\" is not a directory.");
        }

        Rational videoRate = Rational.max(screenFormat.get(FrameRateKey), mouseFormat.get(FrameRateKey));
        ffrDuration = videoRate.inverse();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd 'at' HH.mm.ss");
        Format inputFormat = new Format(MediaTypeKey, MediaType.VIDEO,
                EncodingKey, ENCODING_BUFFERED_IMAGE,
                WidthKey, rect.width,
                HeightKey, rect.height,
                FrameRateKey, videoRate).append(screenFormat);
        Format outputFormat = new Format(
                WidthKey, rect.width,
                HeightKey, rect.height,
                FrameRateKey, videoRate,
                MimeTypeKey, fileFormat.get(MimeTypeKey)).append(screenFormat);

        // FIXME - There should be no need for format-specific code.
        File f;
        if (fileFormat.get(MimeTypeKey).equals(MIME_AVI)) {
            AVIWriter aviw;
            w = aviw = new AVIWriter(f = new File(folder,//
                    "ScreenRecording " + dateFormat.format(new Date()) + ".avi"));
            w.addTrack(outputFormat);
            if (screenFormat.get(DepthKey) == 8) {
                aviw.setPalette(0, (IndexColorModel) videoImg.getColorModel());
            }
            if (audioFormat != null) {
                aviw.addTrack(audioFormat);
            }
        } else if (fileFormat.get(MimeTypeKey).equals(MIME_QUICKTIME)) {
            QuickTimeWriter qtw;
            w = qtw = new QuickTimeWriter(f = new File(folder,//
                    "ScreenRecording " + dateFormat.format(new Date()) + ".mov"));
            w.addTrack(outputFormat);
            if (screenFormat.get(DepthKey) == 8) {
                qtw.setVideoColorTable(0, (IndexColorModel) videoImg.getColorModel());
            }
            if (audioFormat != null) {
                qtw.addTrack(audioFormat);
            }
        } else {
            throw new IOException("Unsupported format " + fileFormat);
        }
        System.out.println("Writing movie to file: " + f);

        // Create the video encoder
        Codec encoder = Registry.getInstance().getEncoder(w.getFormat(0));
        if (encoder == null) {
            throw new IOException("No encoder for format " + w.getFormat(0));
        }
        frameEncoder = encoder;
        frameEncoder.setInputFormat(inputFormat);
        frameEncoder.setOutputFormat(outputFormat);
        if (frameEncoder.getOutputFormat()==null) {
            throw new IOException("Unable to encode video frames in this format.");
        }
    }

    /** Returns the state of the recorder. */
    public State getState() {
        return state;
    }
    
    /** Starts the screen recorder. */
    public void start() {
        startTime = prevScreenCaptureTime = System.currentTimeMillis();
        stopTime = Long.MAX_VALUE;

        outputTime = new Rational(0, 0);
        startWriter();
        startScreenCapture();
        if (mouseFormat != null && mouseFormat.get(FrameRateKey).intValue() > 0) {
            startMouseCapture();
        }
        if (audioFormat != null) {
            startAudioCapture();
        }
        state= State.RECORDING;
        fireStateChanged();
    }

    /** Starts screen capture. */
    private void startScreenCapture() {
        screenTimer = new ScheduledThreadPoolExecutor(1);
        int delay = max(1, (int) (1000 / screenFormat.get(FrameRateKey).doubleValue()));
        screenTimer.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    grabScreen();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    screenTimer.shutdown();
                    recordingFailed();
                }
            }
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    /** Starts mouse capture. */
    private void startMouseCapture() {
        mouseTimer = new ScheduledThreadPoolExecutor(1);
        int delay = max(1, (int) (1000 / mouseFormat.get(FrameRateKey).doubleValue()));
        mouseTimer.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    grabMouse();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    mouseTimer.shutdown();
                    recordingFailed();
                }
            }
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    /** Starts audio capture. */
    private void startAudioCapture() {
        DataLine.Info info = new DataLine.Info(
                TargetDataLine.class, AudioFormatKeys.toAudioFormat(audioFormat));

        final TargetDataLine line;
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open();
            line.start();
            audioRunner = new Thread() {

                @Override
                public void run() {
                    Buffer buf = new Buffer();
                    AudioFormat lineFormat = line.getFormat();
                    buf.format = fromAudioFormat(lineFormat);

                    // For even sample rates, we select a buffer size that can 
                    // hold half a second of audio. This allows audio/video interlave
                    // twice a second, as recommended for AVI and QuickTime movies.
                    // For odd sample rates, we have to select a buffer size that can hold
                    // one second of audio. 
                    int bufferSize = lineFormat.getFrameSize() * (int) lineFormat.getSampleRate();
                    if (((int) lineFormat.getSampleRate() & 1) == 0) {
                        bufferSize /= 2;
                    }

                    byte bdat[] = new byte[bufferSize];
                    buf.data = bdat;
                    Rational sampleRate = Rational.valueOf(lineFormat.getSampleRate());
                    Rational frameRate = Rational.valueOf(lineFormat.getFrameRate());
                    long totalSampleCount = 0;
                    try {
                        while (audioRunner == this) {
                            int count = line.read(bdat, 0, bdat.length);
                            if (count > 0) {
                                buf.sampleCount = count * 8 / (lineFormat.getSampleSizeInBits() * lineFormat.getChannels());
                                buf.sampleDuration = sampleRate.inverse();
                                buf.offset = 0;
                                buf.length = count;
                                buf.track = 1;
                                buf.timeStamp = new Rational(totalSampleCount, 1).divide(frameRate);
                                totalSampleCount += buf.sampleCount;

                                write(buf);

                                synchronized (sync) {
                                    if (buf.timeStamp.add(buf.sampleDuration.multiply(buf.sampleCount)).compareTo(new Rational(stopTime, 1000)) > 0) {
                                        // FIXME - Truncate the buffer
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        return;
                    } finally {
                        line.close();
                    }
                }
            };
            audioRunner.start();
        } catch (LineUnavailableException ex) {
            // FIXME - Instead of silently suppressing audio recording, we should
            // print an error message to the user
            ex.printStackTrace();
            recordingFailed();
        }
    }

    /** Starts file writing. */
    private void startWriter() {
        writerQueue = new ArrayBlockingQueue<Buffer>(screenFormat.get(FrameRateKey).intValue() + 1);
        writerThread = new Thread() {

            @Override
            public void run() {
                try {
                    while (writerThread == this) {
                        try {
                            Buffer buf = writerQueue.take();
                            doWrite(buf);
                        } catch (InterruptedException ex) {
                            // We have been interrupted, terminate
                            break;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    recordingFailed();
                }
            }
        };
        writerThread.start();
    }

    private void recordingFailed() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                try {
                    state= State.FAILED;
                    stop();
                } catch (IOException ex2) {
                    ex2.printStackTrace();
                }
            }
        });
    }

    /** Stops the screen recorder. */
    public void stop() throws IOException {
        System.out.println("Stopping ScreenRecorder...");

        synchronized (sync) {
            stopTime = System.currentTimeMillis();
        }
        if (mouseTimer != null) {
            mouseTimer.shutdown();
        }
        if (screenTimer != null) {
            screenTimer.shutdown();
        }
        Thread pendingAudioCaptureThread = audioRunner;
        audioRunner = null;

        try {
            if (mouseTimer != null) {
                mouseTimer.awaitTermination((int) (1000 / mouseFormat.get(FrameRateKey).doubleValue()), TimeUnit.MILLISECONDS);
            }
            if (screenTimer != null) {
                screenTimer.awaitTermination((int) (1000 / screenFormat.get(FrameRateKey).doubleValue()), TimeUnit.MILLISECONDS);
            }
            if (pendingAudioCaptureThread != null) {
                pendingAudioCaptureThread.join();
            }
        } catch (InterruptedException ex) {
            // nothing to do
        }

        Thread pendingWriterThread = writerThread;
        writerThread = null;

        try {
            if (pendingWriterThread != null) {
                pendingWriterThread.interrupt();
                pendingWriterThread.join();
            }
        } catch (InterruptedException ex) {
            // nothing to do
            ex.printStackTrace();
        }

        System.out.println("...ScreenRecorder stopped");
        System.out.println("Closing File...");
        for (int i = 0, n = w.getTrackCount(); i < n; i++) {
            System.out.println("  Track " + i + " duration=" + w.getDuration(i));
        }

        synchronized (sync) {
            w.close();
            w = null;
        }
        videoGraphics.dispose();
        videoImg.flush();
        System.out.println("...File closed");
        if (state == State.RECORDING) {
            state=State.DONE;
        }
        fireStateChanged();
    }
    
    private long previousGrab = 0;

    /** Grabs a screen, generates video images with pending mouse captures
     * and writes them into the movie file.
     */
    private void grabScreen() throws IOException {

        // Capture the screen
        BufferedImage previousScreenCapture = screenCapture;
        long timeBeforeCapture = System.currentTimeMillis();
        previousGrab = timeBeforeCapture;
        screenCapture = robot.createScreenCapture(new Rectangle(0, 0, rect.width, rect.height));
        long timeAfterCapture = System.currentTimeMillis();
        if (previousScreenCapture == null) {
            previousScreenCapture = screenCapture;
        }
        videoGraphics.drawImage(previousScreenCapture, 0, 0, null);

        Buffer buf = new Buffer();
        buf.format = new Format(MediaTypeKey, MediaType.VIDEO, EncodingKey, ENCODING_BUFFERED_IMAGE);
        // Generate video frames with mouse cursor painted on them
        boolean hasMouseCapture = false;
        if (mouseFormat != null && mouseFormat.get(FrameRateKey).intValue() > 0) {
            Point previous = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
            while (!mouseCaptures.isEmpty() && mouseCaptures.peek().time < timeAfterCapture) {
                MouseCapture mouseCapture = mouseCaptures.poll();
                if (mouseCapture.time > prevScreenCaptureTime) {
                    if (mouseCapture.time > timeBeforeCapture) {
                        previousScreenCapture = screenCapture;
                        videoGraphics.drawImage(previousScreenCapture, 0, 0, null);
                    }

                    hasMouseCapture = true;
                    Point p = mouseCapture.p;
                    p.x -= rect.x;
                    p.y -= rect.y;
                    synchronized (sync) {
                        if (mouseCapture.time > stopTime) {
                            break;
                        }

                        if (p.x != previous.x || p.y != previous.y || mouseCapture.time - prevScreenCaptureTime > maxFrameDuration) {
                            previous.x = p.x;
                            previous.y = p.y;

                            // draw cursor
                            videoGraphics.drawImage(cursorImg, p.x + cursorOffset.x, p.y + cursorOffset.y, null);
                            try {
                                buf.clearFlags();
                                buf.data = videoImg;
                                buf.sampleDuration = new Rational(mouseCapture.time - prevScreenCaptureTime, 1000);
                                buf.timeStamp = new Rational(prevScreenCaptureTime - startTime, 1000);
                                buf.track = 0;
                                write(buf);
                            } catch (Throwable t) {
                                System.out.flush();
                                t.printStackTrace();
                                System.err.flush();
                                System.exit(10);
                            }
                            prevScreenCaptureTime = mouseCapture.time;
                            // erase cursor
                            videoGraphics.drawImage(previousScreenCapture, //
                                    p.x + cursorOffset.x, p.y + cursorOffset.y,//
                                    p.x + cursorOffset.x + cursorImg.getWidth() - 1, p.y + cursorOffset.y + cursorImg.getHeight() - 1,//
                                    p.x + cursorOffset.x, p.y + cursorOffset.y,//
                                    p.x + cursorOffset.x + cursorImg.getWidth() - 1, p.y + cursorOffset.y + cursorImg.getHeight() - 1,//
                                    null);
                        }


                    }
                }
            }

            if (!hasMouseCapture) {
                Point p = null;
                if (mouseFormat != null) {
                    PointerInfo info = MouseInfo.getPointerInfo();
                    p = info.getLocation();
                    videoGraphics.drawImage(cursorImg, p.x + cursorOffset.x, p.x + cursorOffset.y, null);
                }
                buf.data = videoImg;
                buf.sampleDuration = new Rational(timeAfterCapture - prevScreenCaptureTime, 1000);
                buf.timeStamp = new Rational(prevScreenCaptureTime - startTime, 1000);
                buf.track = 0;
                write(buf);
                prevScreenCaptureTime = timeAfterCapture;
                if (p != null) {
                    // erase cursor
                    videoGraphics.drawImage(previousScreenCapture, //
                            p.x + cursorOffset.x, p.y + cursorOffset.y,//
                            p.x + cursorOffset.x + cursorImg.getWidth() - 1, p.y + cursorOffset.y + cursorImg.getHeight() - 1,//
                            p.x + cursorOffset.x, p.y + cursorOffset.y,//
                            p.x + cursorOffset.x + cursorImg.getWidth() - 1, p.y + cursorOffset.y + cursorImg.getHeight() - 1,//
                            null);
                }
            }
        } else {
            buf.data = videoImg;
            buf.sampleDuration = new Rational(timeAfterCapture - prevScreenCaptureTime, 1000);
            buf.timeStamp = new Rational(prevScreenCaptureTime - startTime, 1000);
            buf.track = 0;
            write(buf);
            prevScreenCaptureTime = timeAfterCapture;
        }
    }

    /** Captures the mouse cursor. */
    private void grabMouse() {
        long now = System.currentTimeMillis();
        PointerInfo info = MouseInfo.getPointerInfo();

        mouseCaptures.offer(new MouseCapture(now, info.getLocation()));
    }

    /** Holds a mouse capture. */
    private static class MouseCapture {

        public long time;
        public Point p;

        public MouseCapture(long time, Point p) {
            this.time = time;
            this.p = p;
        }
    }

    /** Writes a buffer into the movie. Since the file system may not be 
     * immediately available at all times, we do this asynchronously. 
     * <p>
     * The buffer is copied and passed to the writer queue, which is consumed
     * by the writer thread. See method startWriter().
     * <p>
     * AVI does not support a variable frame rate for the video track. Since
     * we can not capture frames at a fixed frame rate we have to resend the
     * same captured screen multiple times to the writer.
     * 
     * @param buf
     * @throws IOException 
     */
    private void write(Buffer buf) throws IOException {
        MovieWriter writer = this.w;
        if (writer == null) {
            return;
        }

        // Create a clone of the buffer
        // Note that this creates a lot of memory garbage. 
        // The Java garbage collector seems to handle it well.
        // In a "real" application we would probably reuse the buffers with a
        // ring buffer.
        if (buf.track == 0) {// video track
            if (writer.isVFRSupported()) {// variable frame rate is supported => easy
                Buffer wbuf = new Buffer();
                frameEncoder.process(buf, wbuf);
                writerQueue.offer(wbuf);
            } else {// variable frame rate not supported => convert to fixed frame rate
                Rational inputTime = buf.timeStamp.add(buf.sampleDuration);
                boolean isFirst = true;
                while (outputTime.compareTo(inputTime) < 0) {
                    buf.timeStamp = outputTime;
                    buf.sampleDuration = ffrDuration;
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        buf.setFlag(SAME_DATA);
                    }
                    Buffer wbuf = new Buffer();
                    if (frameEncoder.process(buf, wbuf) != Codec.CODEC_OK) {
                        throw new IOException("Codec failed or could not process frame in a single step.");
                    }
                    writerQueue.offer(wbuf);
                    outputTime = outputTime.add(ffrDuration);
                }
            }
        } else {// audio track
            Buffer wbuf = new Buffer();
            wbuf.setMetaTo(buf);
            wbuf.data = ((byte[]) buf.data).clone();
            wbuf.length = buf.length;
            wbuf.offset = buf.offset;
            writerQueue.offer(wbuf);
        }
    }

    /**
     * The actual writing of the buffer happens here.
     * <p>
     * This method is called from the writer thread in startWriter(). 
     * 
     * @param buf
     * @throws IOException 
     */
    private void doWrite(Buffer buf) throws IOException {
        synchronized (sync) {
            w.write(buf.track, buf);

            // Close file on a separate thread if file is full or an hour
            // has passed.
            long now = System.currentTimeMillis();
            if (w.isDataLimitReached() || now - startTime > 60 * 60 * 1000) {
                final MovieWriter closingWriter = w;
                new Thread() {

                    @Override
                    public void run() {
                        try {
                            closingWriter.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }

                    }
                }.start();
                createMovieWriter();
                startTime = now;
            }
        }
        long end = System.currentTimeMillis();
    }
}
