/*
 * @(#)TSCCCodec.java 
 * 
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media.jmf.codec.video;

import org.monte.media.avi.TechSmithCodecCore;
import org.monte.media.io.SeekableByteArrayOutputStream;
import com.sun.media.format.AviVideoFormat;
import java.awt.Dimension;
import java.io.IOException;
import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.IndexedColorFormat;
import javax.media.format.RGBFormat;
import javax.media.format.VideoFormat;

/**
 * JMF Codec for the TechSmith Screen-Capture format.
 * <p>
 * This codec can decode24 and encode the format to/from a JMF {@code RGBFormat}
 * or a {@code IndexedColorFormat}.
 * <p>
 * See {@link TechSmithCodecCore} for a description of the format.
 *
 * @author Werner Randelshofer
 * @version $Id: TSCCCodec.java 134 2011-12-02 16:23:00Z werner $
 */
public class TSCCCodec extends AbstractVideoDecoder {

    /** RGB bit masks */
    protected static final int bMask = 0x000000ff;
    protected static final int gMask = 0x0000ff00;
    protected static final int rMask = 0x00ff0000;
    private Object pixels;
    private Object previousPixels;
    private long previousSequenceNumber;
    private int frameCounter;
    private TechSmithCodecCore state;

    public TSCCCodec() {
        supportedInputFormats = new VideoFormat[]{
            new AviVideoFormat("tscc"),
            //
            new RGBFormat(
            null, // dimension
            Format.NOT_SPECIFIED, // max data length 
            Format.intArray, // data type
            Format.NOT_SPECIFIED, // frame rate
            32, // bits per pixel
            rMask, gMask, bMask, // red, green, blue mask
            Format.NOT_SPECIFIED, // pixel stride
            Format.NOT_SPECIFIED,// line stride
            Format.FALSE, // flipped
            Format.NOT_SPECIFIED // endian
            ),
            //
            new IndexedColorFormat(
            null, // dimension
            Format.NOT_SPECIFIED,// max data length
            Format.byteArray,// data type
            Format.NOT_SPECIFIED,// frame rate
            Format.NOT_SPECIFIED, // line stride
            256,// map size
            null, null, null// r,g,b map
            )
        };
        defaultOutputFormats = new VideoFormat[]{
            new AviVideoFormat("tscc"),
            //
            new RGBFormat(
            null, // dimension
            Format.NOT_SPECIFIED, // max data length 
            Format.intArray, // data type
            Format.NOT_SPECIFIED, // frame rate
            32, // bits per pixel
            rMask, gMask, bMask, // red, green, blue mask
            Format.NOT_SPECIFIED, // pixel stride
            Format.NOT_SPECIFIED,// line stride
            Format.FALSE, // flipped
            Format.NOT_SPECIFIED // endian
            ),
            //
            new IndexedColorFormat(
            null, // dimension
            Format.NOT_SPECIFIED,// max data length
            Format.byteArray,// data type
            Format.NOT_SPECIFIED,// frame rate
            Format.NOT_SPECIFIED, // line stride
            256,// map size
            null, null, null// r,g,b map
            )
        };
        pluginName = "TechSmith Screen-Capture Codec";
    }

    @Override
    protected Format[] getMatchingOutputFormats(Format input) {
        if (new VideoFormat("tscc").matches(input)) {
            VideoFormat inf = (VideoFormat) input;
            Dimension s = inf.getSize();
            RGBFormat outf = new RGBFormat(s,
                    s.width < 0 || s.height < 0 ? Format.NOT_SPECIFIED : s.width * s.height,
                    Format.intArray, inf.getFrameRate(), 32, rMask, gMask, bMask, 1, s.width, Format.FALSE, RGBFormat.BIG_ENDIAN);
            return new Format[]{outf};
        } else if (new RGBFormat().matches(input)) {
            VideoFormat inf = (VideoFormat) input;
            Dimension s = inf.getSize();

            VideoFormat outf = new VideoFormat("tscc", s, Format.NOT_SPECIFIED, Format.byteArray,
                    inf.getFrameRate());
            return new Format[]{outf};
        } else if (new IndexedColorFormat(null, // dimension
                Format.NOT_SPECIFIED,// max data length
                Format.byteArray,// data type
                Format.NOT_SPECIFIED,// frame rate
                Format.NOT_SPECIFIED, // line stride
                256,// map size
                null, null, null).matches(input)) {
            VideoFormat inf = (VideoFormat) input;
            Dimension s = inf.getSize();

            VideoFormat outf = new VideoFormat("tscc", s, Format.NOT_SPECIFIED, Format.byteArray,
                    inf.getFrameRate());
            return new Format[]{outf};
        }
        return new Format[0];
    }

    @Override
    public void reset() {
        previousSequenceNumber = 0;
        frameCounter = 0;
        state = null;
    }

    @Override
    public int process(Buffer input, Buffer output) {
        if (state == null) {
            state = new TechSmithCodecCore();
        }

        if (outputFormat.getEncoding().equals("tscc")) {
            return encode(input, output);
        } else {
            return decode(input, output);
        }
    }

    /** Copies all meta-data field from in to out. */
    protected void copyMetaTo(Buffer in, Buffer out) {
        //out.setData(in.getData());
        //out.setHeader(in.getHeader());
        //out.setFormat(in.getFormat());
        //out.setLength(in.getLength());
        ///out.setOffset(in.getOffset());
        out.setTimeStamp(in.getTimeStamp());
        out.setDuration(in.getDuration());
        out.setSequenceNumber(in.getSequenceNumber());
        out.setFlags(in.getFlags());
    }

    protected int encode(Buffer in, Buffer out) {
        if (in.isDiscard()) {
            out.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        copyMetaTo(in, out);
        out.setFormat(outputFormat);

        SeekableByteArrayOutputStream tmp;
        if (out.getData() instanceof byte[]) {
            tmp = new SeekableByteArrayOutputStream((byte[]) out.getData());
        } else {
            tmp = new SeekableByteArrayOutputStream();
        }

        VideoFormat outvf = (VideoFormat) outputFormat;
        boolean isKeyframe = isSet(in, Buffer.FLAG_KEY_FRAME) || frameCounter % (int) outvf.getFrameRate() == 0;
        frameCounter++;
        VideoFormat invf = (VideoFormat) inputFormat;
        int width = invf.getSize().width;
        int height = invf.getSize().height;
        int scanlineStride;
        int pixelStride;
        int offset = in.getOffset();
        int inputDepth;

        if (invf instanceof RGBFormat) {
            RGBFormat inrgbf = (RGBFormat) inputFormat;
            inputDepth = inrgbf.getBitsPerPixel();
            scanlineStride = inrgbf.getLineStride();
            pixelStride = inrgbf.getPixelStride();
            if (inrgbf.getFlipped() == Format.TRUE) {
                offset += (height - 1) * scanlineStride;
                scanlineStride = -scanlineStride;
            }
        } else if (invf instanceof AviVideoFormat) {
            IndexedColorFormat inicvf = (IndexedColorFormat) inputFormat;
            inputDepth = ((AviVideoFormat) invf).getBitsPerPixel();
            scanlineStride = inicvf.getLineStride();
            pixelStride = 1;
        } else {
            IndexedColorFormat inicvf = (IndexedColorFormat) inputFormat;
            inputDepth = 8;
            scanlineStride = inicvf.getLineStride();
            pixelStride = 1;
            state.setPalette(inicvf.getRedValues(), inicvf.getGreenValues(), inicvf.getBlueValues());
        }

        // FIXME - Implement pixelStride <> 1
        if (pixelStride != 1) {
            return BUFFER_PROCESSED_FAILED;
        }

        try {
            switch (inputDepth) {
                case 8: {
                    byte[] pixels = (byte[]) (in.getData());
                    if (pixels == null) {
                        setFlag(out, Buffer.FLAG_DISCARD, true);
                        return BUFFER_PROCESSED_FAILED;
                    }

                    if (isKeyframe) {
                        state.encodeKey8to24(tmp, pixels, width, height, offset, scanlineStride);
                    } else {
                        state.encodeDelta8to24(tmp, pixels, (byte[]) previousPixels, width, height, offset, scanlineStride);
                    }
                    setFlag(out, Buffer.FLAG_KEY_FRAME, isKeyframe);
                    if (previousPixels == null) {
                        previousPixels = pixels.clone();
                    } else {
                        System.arraycopy(pixels, 0, (byte[]) previousPixels, 0, pixels.length);
                    }
                    break;
                }
                case 16: {
                    short[] pixels = (short[]) (in.getData());
                    if (pixels == null) {
                        setFlag(out, Buffer.FLAG_DISCARD, true);
                        return BUFFER_PROCESSED_FAILED;
                    }

                    if (isKeyframe) {
                        state.encodeKey16(tmp, pixels, width, height, offset, scanlineStride);
                    } else {
                        state.encodeDelta16(tmp, pixels, (short[]) previousPixels, width, height, offset, scanlineStride);
                    }
                    setFlag(out, Buffer.FLAG_KEY_FRAME, isKeyframe);
                    if (previousPixels == null) {
                        previousPixels = pixels.clone();
                    } else {
                        System.arraycopy(pixels, 0, (short[]) previousPixels, 0, pixels.length);
                    }
                    break;
                }
                case 32: 
                case 24: {
                    int[] pixels = (int[]) (in.getData());
                    if (pixels == null) {
                        setFlag(out, Buffer.FLAG_DISCARD, true);
                        return BUFFER_PROCESSED_FAILED;
                    }

                    if (isKeyframe) {
                        state.encodeKey24(tmp, pixels, width, height, offset, scanlineStride);
                    } else {
                        state.encodeDelta24(tmp, pixels, (int[]) previousPixels, width, height, offset, scanlineStride);
                    }
                    setFlag(out, Buffer.FLAG_KEY_FRAME, isKeyframe);
                    if (previousPixels == null) {
                        previousPixels = pixels.clone();
                    } else {
                        System.arraycopy(pixels, 0, (int[]) previousPixels, 0, pixels.length);
                    }
                    break;
                }
                default: {
                    setFlag(out, Buffer.FLAG_DISCARD, true);
                    return BUFFER_PROCESSED_FAILED;
                }
            }

            out.setFormat(outputFormat);
            out.setData(tmp.getBuffer());
            out.setOffset(0);
            out.setLength((int) tmp.size());
            return BUFFER_PROCESSED_OK;
        } catch (IOException ex) {
            ex.printStackTrace();
            setFlag(out, Buffer.FLAG_DISCARD, true);
            return BUFFER_PROCESSED_FAILED;
        }
    }

    protected int decode(Buffer in, Buffer out) {
        if (in.isDiscard()) {
            out.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        copyMetaTo(in, out);
        out.setDiscard(false);
        out.setFormat(outputFormat);

        // The following could be done in the open() method
        VideoFormat invf = (VideoFormat) inputFormat;
        boolean isKeyframe;
        VideoFormat ooutvf = (VideoFormat) outputFormat;
        int width = invf.getSize().width;
        int height = invf.getSize().height;
        int scanlineStride;
        int pixelStride;
        int offset = in.getOffset();
        int inputDepth;
        if (invf instanceof RGBFormat) {
            RGBFormat inrgbf = (RGBFormat) outputFormat;
            inputDepth = 24;
            scanlineStride = inrgbf.getLineStride();
            pixelStride = inrgbf.getPixelStride();
            if (inrgbf.getFlipped() == Format.TRUE) {
                offset += (height - 1) * scanlineStride;
                scanlineStride = -scanlineStride;
            }
        } else if (invf instanceof AviVideoFormat) {
            AviVideoFormat inavivf = ((AviVideoFormat) invf);
            inputDepth = inavivf.getBitsPerPixel();
            scanlineStride = width;
            pixelStride = 1;
        } else {
            IndexedColorFormat inicvf = (IndexedColorFormat) invf;
            inputDepth = 8;
            scanlineStride = inicvf.getLineStride();
            pixelStride = 1;
        }
        int outputDepth = inputDepth;
        if (outputFormat instanceof RGBFormat) {
            outputDepth = ((RGBFormat) outputFormat).getBitsPerPixel();
        }


        // Handle header
        int header;
        if (in.getHeader() instanceof Integer) {
            header = (Integer) in.getHeader();
        } else {
            header = 0;
        }

        if (0x100 == (header & 0x100)) { // palette change
            try {
                state.decodePalette((byte[]) in.getData(), in.getOffset(), in.getLength());
                out.setFlags(Buffer.FLAG_DISCARD);
                return OUTPUT_BUFFER_NOT_FILLED;
            } catch (IOException ex) {
                ex.printStackTrace();
                out.setDiscard(true);
                return BUFFER_PROCESSED_FAILED;
            }

        }


        if (inputDepth == 8 && outputDepth == 8) {
            if (!(pixels instanceof byte[]) || ((byte[]) pixels).length < width * height) {
                pixels = new byte[width * height];
            }
        } else if (inputDepth == 16 && outputDepth == 16) {
            if (!(pixels instanceof short[]) || ((short[]) pixels).length < width * height) {
                pixels = new int[width * height];
            }
        } else {
            if (!(pixels instanceof int[]) || ((int[]) pixels).length < width * height) {
                pixels = new int[width * height];
            }
        }

        out.setData(pixels);
        out.setOffset(0);
        out.setLength(width * height);

        byte[] inDat = (byte[]) in.getData();
        // Detect if frames were skipped
        long sequenceNumber = in.getSequenceNumber();
        boolean framesWereSkipped = (sequenceNumber != previousSequenceNumber + 1);
        try {
            if (inputDepth == 8) {
                if (outputDepth == 8) {
                    isKeyframe = state.decode8(inDat, in.getOffset(), in.getLength(), (byte[]) pixels, (byte[]) pixels, outputFormat.getSize().width, outputFormat.getSize().height,
                            framesWereSkipped);
                } else {
                    isKeyframe = state.decode8(inDat, in.getOffset(), in.getLength(), (int[]) pixels, (int[]) pixels, outputFormat.getSize().width, outputFormat.getSize().height,
                            framesWereSkipped);
                }
            } else if (inputDepth == 16) {
                isKeyframe = state.decode16(inDat, in.getOffset(), in.getLength(), (int[]) pixels, (int[]) pixels, outputFormat.getSize().width, outputFormat.getSize().height,
                        framesWereSkipped);
            } else if (inputDepth == 24) {
                isKeyframe = state.decode24(inDat, in.getOffset(), in.getLength(), (int[]) pixels, (int[]) pixels, outputFormat.getSize().width, outputFormat.getSize().height,
                        framesWereSkipped);
            } else {
                out.setDiscard(true);
                return BUFFER_PROCESSED_FAILED;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            out.setDiscard(true);
            return BUFFER_PROCESSED_FAILED;
        }
        if (framesWereSkipped && !isKeyframe) {
            out.setDiscard(true);
        } else {
            previousSequenceNumber = sequenceNumber;
        }

        setFlag(out, Buffer.FLAG_KEY_FRAME, isKeyframe);

        return BUFFER_PROCESSED_OK;
    }

    @Override
    public void close() {
        pixels = null;
        state = null;
    }

    @Override
    public void open() throws ResourceUnavailableException {
        state = new TechSmithCodecCore();
        /*
        if (((VideoFormat) outputFormat).getDataType() == int[].class) {
        pixels = new int[outputFormat.getSize().width * outputFormat.getSize().height];
        previousPixels = new int[outputFormat.getSize().width * outputFormat.getSize().height];
        } else {
        previousPixels = new byte[outputFormat.getSize().width * outputFormat.getSize().height];
        pixels = new byte[outputFormat.getSize().width * outputFormat.getSize().height];
        }*/
    }
}
