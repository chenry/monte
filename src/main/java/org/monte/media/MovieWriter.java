/*
 * @(#)MovieWriter.java  
 * 
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media;

import org.monte.media.math.Rational;
import java.io.IOException;

/**
 * A simple API for writing movie data (audio and video) into a file.
 *
 * @author Werner Randelshofer
 * @version $Id: MovieWriter.java 136 2011-12-26 10:10:26Z werner $
 */
public interface MovieWriter extends Multiplexer {
    /** Returns the file format. */
    public Format getFileFormat() throws IOException;

    /** Adds a track to the writer. 
     * 
     * @param format The desired input format of the track. The actual input
     * format may differ, see {@link #getFormat}.
     * @return The track number.
     */
    public int addTrack(Format format) throws IOException;
    /** Returns the media format of the specified track.
     * 
     * @param track Track number.
     * @return The media format of the track.
     */
    public Format getFormat(int track);
    
    /** Returns the number of tracks. */
    public int getTrackCount();

    /** Writes a sample into the specified track.
     * Does nothing if the discard-flag in the buffer is set to true.
     *
     * @param track The track number.
     * @param buf The buffer containing the sample data.
     */
    @Override
    public void write(int track, Buffer buf) throws IOException;

    /** Closes the writer. */
    @Override
    public void close() throws IOException;

    /** Returns true if the writer supports variable frame rates. 
     * <p>
     * FIXME - Replace by something better.
     */
    public boolean isVFRSupported();

    /** Returns true if the limit for media data has been reached.
     * If this limit is reached, no more samples should be added to the movie.
     * <p>
     * This limit is imposed by data structures of the movie file
     * which will overflow if more samples are added to the movie.
     * <p>
     * FIXME - Maybe replace by getCapacity():long. 
     */
    public boolean isDataLimitReached();

    /** Returns the duration of the track in seconds. */
    public Rational getDuration(int track);
}
