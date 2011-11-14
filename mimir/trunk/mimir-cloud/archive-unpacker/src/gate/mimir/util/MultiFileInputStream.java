/*
*  MultiFileOutputStream.java
*
*  Copyright (c) 2011, The University of Sheffield.
*
*  Valentin Tablan, 26 Apr 2011
*  
*  $Id$
*/
package gate.mimir.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * An input stream that concatenates the bytes from a list of input files, that
 * it opens one after another. 
 */
public class MultiFileInputStream extends InputStream {
  
  private File inputDirectory;
  
  private String[] inputFileNames;
  
  private int seqNumber = -1;
  
  private InputStream currentInputStream;
  
  private File currentInputFile;
  
  private boolean closed = false;
  
  private CRC32 crc;
  
  public MultiFileInputStream(File inputDirectory, String[] inputFileNames) throws IOException {
    this.inputDirectory = inputDirectory;
    this.inputFileNames = inputFileNames;
    crc = new CRC32();
    // check the signature
    try {
      byte[] specimen = "MMFA".getBytes("UTF-8");
      byte[] signature = new byte[specimen.length];
      read(signature, 0, specimen.length);
      if(!Arrays.equals(specimen, signature)) {
        throw new IOException("The supplied archive files are not a valid " +
        		"Mimir multi-file archive!");
      }
    } catch(UnsupportedEncodingException e) {
      throw new RuntimeException("This JVM does not support UTF-8!");
    }
  }

  protected boolean nextFile() throws IOException {
    if(currentInputStream != null) {
      currentInputStream.close();
    }
    seqNumber++;
    if(seqNumber < inputFileNames.length) {
      currentInputFile = new File(inputDirectory, inputFileNames[seqNumber]);
      currentInputStream = new BufferedInputStream(
          new FileInputStream(currentInputFile));
      return true;
    } else {
      return false;
    }
  }

  @Override
  public int read() throws IOException {
    if(closed) throw new IOException("Input stream already closed!");
    // did we just start?
    if(currentInputStream == null) {
      if(!nextFile()) return -1;
    }
    int res = currentInputStream.read();
    if(res == -1) {
      // we just finished a file
      if(nextFile()) {
        res = currentInputStream.read();
        if(res != -1) crc.update(res);
      }
      return res;
    } else {
      crc.update(res);
      return res;
    }
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if(closed) throw new IOException("Input stream already closed!");
    // did we just start?
    if(currentInputStream == null) {
      if(!nextFile())  return -1;
    }
    int res = currentInputStream.read(b, off, len);
    if(res == -1) {
      // we just finished a file
      if(nextFile()) {
        res = currentInputStream.read(b, off, len);
        if(res != -1) crc.update(b, off, res);
        return res;
      } else {
        return -1;
      }
    } else {
      crc.update(b, off, res);
      return res;
    }
  }

  public long getCRC() {
    return crc.getValue();
  }
  
  @Override
  public int available() throws IOException {
    if(closed) throw new IOException("Input stream already closed!");
    if(currentInputStream == null) {
      if(!nextFile())  return 0;
    }
    return currentInputStream.available();
  }

  @Override
  public void close() throws IOException {
    if(!closed) {
      if(currentInputStream != null) currentInputStream.close();
      closed = true;
    }
  }

  @Override
  public boolean markSupported() {
    return false;
  }
}
