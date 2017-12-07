// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org



package org.droidmate.logging

import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.recovery.ResilientFileOutputStream
import ch.qos.logback.core.util.FileUtil

import java.nio.channels.FileChannel
import java.nio.channels.FileLock


/**
 * Copy-pasted and then modified to suit my needs from:
 * https://github.com/tony19/logback-android/blob/master/logback-core/src/main/java/ch/qos/logback/core/FileAppender.java
 */
public class LazyFileAppender<E> extends OutputStreamAppender<E>
{

  /**
   * Append to or truncate the file? The default value for this variable is
   * <code>true</code>, meaning that by default a <code>LazyFileAppender</code> will
   * append to an existing file and not truncate it.
   */
  protected boolean append = true;

  /**
   * The name of the active log file.
   */
  protected String fileName = null;

  private boolean prudent = false;
  private boolean initialized = false;
  private boolean lazyInit = false;

  /**
   * The <b>File</b> property takes a string value which should be the name of
   * the file to append to.
   */
  public void setFile(String file)
  {
    if (file == null)
    {
      fileName = null;
    } else
    {
      // Trim spaces from both ends. The users probably does not want
      // trailing spaces in file names.
      fileName = file.trim();
    }
  }

  /**
   * Returns the value of the <b>Append</b> property.
   */
  public boolean isAppend()
  {
    return append;
  }

  /**
   * This method is used by derived classes to obtain the raw file property.
   * Regular users should not be calling this method. Note that RollingFilePolicyBase
   * requires public getter for this property.
   *
   * @return the value of the file property
   */
  final public String rawFileProperty()
  {
    return fileName;
  }

  /**
   * Returns the value of the <b>File</b> property.
   *
   * <p>
   * This method may be overridden by derived classes.
   *
   */
  public String getFile()
  {
    return fileName;
  }

  /**
   * If the value of <b>File</b> is not <code>null</code>, then
   * {@link #openFile} is called with the values of <b>File</b> and
   * <b>Append</b> properties.
   */
  public void start()
  {
    int errors = 0;

    // Use getFile() instead of direct access to fileName because
    // the function is overridden in RollingFileAppender, which
    // returns a value that doesn't necessarily match fileName.
    String file = getFile();

    if (file != null)
    {
      addInfo("File property is set to [" + file + "]");

      if (prudent)
      {
        if (!isAppend())
        {
          setAppend(true);
          addWarn("Setting \"Append\" property to true on account of \"Prudent\" mode");
        }
      }

      if (!lazyInit)
      {
        try
        {
          openFile(file);
        } catch (IOException e)
        {
          errors++;
          addError("openFile(" + file + "," + append + ") failed", e);
        }
      } else
      {
        // We'll initialize the file output stream later. Use a dummy for now
        // to satisfy OutputStreamAppender.start().
        setOutputStream(new NOPOutputStream());
      }
    } else
    {
      errors++;
      addError("\"File\" property not set for appender named [" + name + "]");
    }
    if (errors == 0)
    {
      super.start();
    }
  }

  /**
   * <p>
   * Sets and <i>opens</i> the file where the log output will go. The specified
   * file must be writable.
   *
   * <p>
   * If there was already an opened file, then the previous file is closed
   * first.
   *
   * <p>
   * <b>Do not use this method directly. To configure a LazyFileAppender or one of
   * its subclasses, set its properties one by one and then call start().</b>
   *
   * @param filename
   *          The path to the log file.
   *
   * @return true if successful; false otherwise
   */
  protected boolean openFile(String filename) throws IOException
  {
    boolean successful;
    synchronized (lock)
    {
      File file = new File(filename);
      if (FileUtil.isParentDirectoryCreationRequired(file))
      {
        boolean result = FileUtil.createMissingParentDirectories(file);
        if (!result)
        {
          addError("Failed to create parent directories for ["
            + file.getAbsolutePath() + "]");
        }
      }

      ResilientFileOutputStream resilientFos = new ResilientFileOutputStream(
        file, append);
      resilientFos.setContext(context);
      setOutputStream(resilientFos);
      successful = true;
    }
    return successful;
  }

  /**
   * @see #setPrudent(boolean)
   *
   * @return true if in prudent mode
   */
  public boolean isPrudent()
  {
    return prudent;
  }

  /**
   * When prudent is set to true, file appenders from multiple JVMs can safely
   * write to the same file.
   *
   * @param prudent
   */
  public void setPrudent(boolean prudent)
  {
    this.prudent = prudent;
  }

  public void setAppend(boolean append)
  {
    this.append = append;
  }

  /**
   * Gets the enable status of lazy initialization of the file output
   * stream
   *
   * @return true if enabled; false otherwise
   */
  public boolean getLazy()
  {
    return lazyInit;
  }

  /**
   * Enables/disables lazy initialization of the file output stream.
   * This defers the file creation until the first outgoing message.
   *
   * @param enabled true to enable lazy initialization; false otherwise
   */
  public void setLazy(boolean enable)
  {
    lazyInit = enable;
  }

  private void safeWrite(E event) throws IOException
  {
    ResilientFileOutputStream resilientFOS = (ResilientFileOutputStream) getOutputStream();
    FileChannel fileChannel = resilientFOS.getChannel();
    if (fileChannel == null)
    {
      return;
    }
    FileLock fileLock = null;
    try
    {
      fileLock = fileChannel.lock();
      long position = fileChannel.position();
      long size = fileChannel.size();
      if (size != position)
      {
        fileChannel.position(size);
      }
      super.writeOut(event);
    } finally
    {
      if (fileLock != null)
      {
        fileLock.release();
      }
    }
  }

  @Override
  protected void writeOut(E event) throws IOException
  {
    if (prudent)
    {
      safeWrite(event);
    } else
    {
      super.writeOut(event);
    }
  }

  @Override
  protected void subAppend(E event)
  {
    if (!initialized && lazyInit)
    {
      initialized = true;
      try
      {
        openFile(getFile());
      } catch (IOException e)
      {
        this.started = false;
        addError("openFile(" + fileName + "," + append + ") failed", e);
      }
    }

    super.subAppend(event);
  }

}
