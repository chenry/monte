package org.monte.media.jmf.renderer.video;

import java.awt.Component;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.JPEGFormat;
import javax.media.format.VideoFormat;
import javax.media.renderer.VideoRenderer;

import net.sf.fmj.media.AbstractVideoRenderer;
import net.sf.fmj.media.renderer.video.JVideoComponent;
import net.sf.fmj.utility.LoggerSingleton;

import org.monte.media.io.ByteArrayImageInputStream;
import org.monte.media.jmf.jpeg.MJPGImageReader;
import org.monte.media.jmf.jpeg.MJPGImageReaderSpi;

import com.sun.imageio.plugins.jpeg.JPEGImageReader;

/**
 * 
 * Renderer which renders JPEG directly. 
 * There is a comparable class in JMF, hence this implementation.  However, it 
 * seems like this is not really needed if there is a JPEGDecoder Codec registered.
 * However, the original cross-platform JMF did not include such a Codec.
 * Because this class does not use BufferToImage, it is not subject to any of
 * its limitations and will render images that BufferToImage does not support 
 * yet.  This is not really anything good, it is only worth pointing out because
 * it can be confusing when testing JPEG playback.
 * @author Ken Larson
 *
 */
public class MJPEGRenderer extends AbstractVideoRenderer implements VideoRenderer {

    private static final Logger logger = LoggerSingleton.logger;
    private boolean scale;
    // BEGIN PATCH W. Randelshofer Support Motion JPEG with missing DHT segment
    private VideoFormat MJPGFormat = new VideoFormat(VideoFormat.MJPG,
            null,
            Format.NOT_SPECIFIED,
            Format.byteArray,
            // frame rate
            Format.NOT_SPECIFIED);
    // END PATCH W. Randelshofer Support Motion JPEG with missing DHT segment
    private final Format[] supportedInputFormats = new Format[]{
        new JPEGFormat(),
        // BEGIN PATCH W. Randelshofer Interoperability with JMF
        new VideoFormat(VideoFormat.JPEG,
        null,
        Format.NOT_SPECIFIED,
        Format.byteArray,
        // frame rate
        Format.NOT_SPECIFIED),
        MJPGFormat
    // END PATCH W. Randelshofer Interoperability with JMF
    };

    //@Override
    @Override
    public String getName() {
        return "MJPEG Renderer";
    }

    //@Override
    @Override
    public Format[] getSupportedInputFormats() {
        return supportedInputFormats;
    }
    private JVideoComponent component = new JVideoComponent();

    @Override
    public Component getComponent() {
        return component;
    }
    private Object[] controls = new Object[]{this};

    @Override
    public Object[] getControls() {
        return controls;
    }

    //@Override
    @Override
    public Format setInputFormat(Format format) {
        VideoFormat chosenFormat = (VideoFormat) super.setInputFormat(format);
        if (chosenFormat != null) {
            getComponent().setPreferredSize(chosenFormat.getSize());
        }
        return chosenFormat;
    }

    @Override
    public int doProcess(Buffer buffer) {
        if (buffer.isEOM()) {
            logger.log(Level.WARNING, "{0} passed buffer with EOM flag", this.getClass().getSimpleName());	// normally not supposed to happen, is it?
            return BUFFER_PROCESSED_OK;
        }
        if (buffer.getData() == null) {
            logger.log(Level.WARNING, "buffer.getData() == null, eom={0}", buffer.isEOM());
            return BUFFER_PROCESSED_FAILED;		// TODO: check for EOM?
        }

        if (buffer.getLength() == 0) {
            logger.log(Level.WARNING, "buffer.getLength() == 0, eom={0}", buffer.isEOM());
            return BUFFER_PROCESSED_FAILED;		// TODO: check for EOM?
        }

        if (buffer.isDiscard()) {
            logger.warning("JPEGRenderer passed buffer with discard flag");
            return BUFFER_PROCESSED_FAILED;
        }

        final java.awt.Image image;
        try {
// BEGIN PATCH W. Randelshofer Support AVI MJPEG files with missing DHT segment
            if (inputFormat.matches(MJPGFormat)) {
                MJPGImageReader r = new MJPGImageReader(new MJPGImageReaderSpi());
                r.setInput(buffer);
                image = r.read(0);
            } else {
                JPEGImageReader r = new JPEGImageReader(new MJPGImageReaderSpi());
                r.setInput(new ByteArrayImageInputStream((byte[]) buffer.getData(), buffer.getOffset(), buffer.getLength(), ByteOrder.BIG_ENDIAN));
                image = r.read(0);
                //image = ImageIO.read(new ByteArrayInputStream((byte []) buffer.getData(), buffer.getOffset(), buffer.getLength()));
            }
// END PATCH W. Randelshofer Support AVI MJPEG files with missing DHT segment
        } catch (IOException e) {

            logger.log(Level.WARNING, "" + e, e);
            
//            logger.log(Level.WARNING, "data: {0}", StringUtils.byteArrayToHexString((byte[]) buffer.getData(), buffer.getLength(), buffer.getOffset()));
            return BUFFER_PROCESSED_FAILED;
        }

        if (image == null) {
            logger.log(Level.WARNING, "Failed to read image (ImageIO.read returned null).");
//            logger.log(Level.WARNING, "data: {0}", StringUtils.byteArrayToHexString((byte[]) buffer.getData(), buffer.getLength(), buffer.getOffset()));
            return BUFFER_PROCESSED_FAILED;
        }

        try {
            component.setImage(image);
        } catch (Exception e) {
            logger.log(Level.WARNING, "" + e, e);
//            logger.log(Level.WARNING, "data: {0}", StringUtils.byteArrayToHexString((byte[]) buffer.getData(), buffer.getLength(), buffer.getOffset()));
            return BUFFER_PROCESSED_FAILED;
        }
        return BUFFER_PROCESSED_OK;
    }
}
