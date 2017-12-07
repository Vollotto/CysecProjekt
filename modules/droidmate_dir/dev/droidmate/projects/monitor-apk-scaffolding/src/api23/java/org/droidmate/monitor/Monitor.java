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

package org.droidmate.monitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import org.droidmate.apis.Api;
import org.droidmate.misc.MonitorConstants;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.*;

// org.droidmate.monitor.MonitorSrcTemplate:API_19_UNCOMMENT_LINES
// import de.uds.infsec.instrumentation.Instrumentation;
// import de.uds.infsec.instrumentation.annotation.Redirect;
// import de.uds.infsec.instrumentation.util.Signature;

import de.larma.arthook.*;

import org.droidmate.monitor.IMonitorHook;
import org.droidmate.monitor.MonitorHook;

/**<p>
 * This class will be used by {@code MonitorGenerator} to create {@code Monitor.java} deployed on the device. This class will be
 * first copied by appropriate gradle task of monitor-generator project to its resources dir. Then it will be handled to
 * {@code org.droidmate.monitor.MonitorSrcTemplate} for further processing.
 *
 * </p><p>
 * Note that the final generated version of this file, after running {@code :projects:monitor-generator:build}, will be placed in
 * <pre><code>
 *   [repo root]\dev\droidmate\projects\monitor-generator\monitor-apk-scaffolding\src\org\droidmate\monitor_generator\generated\Monitor.java
 * </code></pre>
 *
 * </p><p>
 * To check if the process of converting this file to a proper {@code Monitor.java} works correctly, see:
 * {@code org.droidmate.monitor.MonitorGeneratorFrontendTest#Generates DroidMate monitor()}.
 *
 * </p><p>
 * Note: The resulting class deployed to the device will be compiled with legacy ant script from Android SDK that supports only
 * Java 5.
 *
 * </p><p>
 *   See also:<br/>
 *     {@code org.droidmate.monitor.MonitorSrcTemplate}<br/>
 *     {@code org.droidmate.monitor.RedirectionsGenerator}
 * </p>
 */
@SuppressLint("NewApi")
@SuppressWarnings("Convert2Diamond")
// !!! DUPLICATION WARNING !!! of class name and location with the build.gradle script of monitor-generator
public class Monitor
{
  //region Class init code
  public Monitor()
  {
    Log.v(MonitorConstants.tag_mjt, MonitorConstants.msg_ctor_start);
    try
    {
      server = startMonitorTCPServer();
      Log.i(MonitorConstants.tag_mjt, MonitorConstants.msg_ctor_success + server.port);

    } catch (Throwable e)
    {
      Log.e(MonitorConstants.tag_mjt, MonitorConstants.msg_ctor_failure, e);
    }
  }

  private static MonitorTcpServer server;
  private static Context          context;

  /**
   * Called by the inlined Application class when the inlined AUE launches activity, as done by
   * org.droidmate.exploration.device.IRobustDevice#launchApp(org.droidmate.android_sdk.IApk)
   */
  @SuppressWarnings("unused")
  public void init(android.content.Context initContext)
  {
    Log.v(MonitorConstants.tag_mjt, "init(): entering");
    context = initContext;
    if (server == null)
    {
      Log.w(MonitorConstants.tag_mjt, "init(): didn't set context for MonitorTcpServer, as the server is null.");
    }
    else
    {
      server.context = context;
    }

    // org.droidmate.monitor.MonitorSrcTemplate:API_19_UNCOMMENT_LINES
    // Instrumentation.processClass(Monitor.class);
    
    ArtHook.hook(Monitor.class);

    redirectConstructors();

    monitorHook.init(context);

    Log.d(MonitorConstants.tag_mjt, MonitorConstants.msgPrefix_init_success + context.getPackageName());
  }
  //endregion

  //region TCP server code

  @SuppressWarnings("ConstantConditions")
  private static MonitorTcpServer startMonitorTCPServer() throws Throwable
  {
    Log.v(MonitorConstants.tag_mjt, "startMonitorTCPServer(): entering");

    MonitorTcpServer tcpServer = new MonitorTcpServer();

    Thread serverThread = null;
    Integer portUsed = null;

    final Iterator<Integer> portsIterator = MonitorConstants.serverPorts.iterator();
    
    while (portsIterator.hasNext() && serverThread == null)
    {
      int port = portsIterator.next();
      serverThread = tcpServer.tryStart(port);
      if (serverThread != null)
        portUsed = port;
    }
    if (serverThread == null)
    {
      if (portsIterator.hasNext()) throw new AssertionError();
      throw new Exception("startMonitorTCPServer(): no available ports.");
    }

    if (serverThread == null) throw new AssertionError();
    if (portUsed == null) throw new AssertionError();
    if (tcpServer.isClosed()) throw new AssertionError();

    Log.d(MonitorConstants.tag_mjt, "startMonitorTCPServer(): SUCCESS portUsed: " + portUsed + " PID: " + getPid());
    return tcpServer;
  }

  static class MonitorTcpServer extends TcpServerBase<String, ArrayList<ArrayList<String>>>
  {

    public Context context;

    protected MonitorTcpServer()
    {
      super();
    }

    @Override
    protected ArrayList<ArrayList<String>> OnServerRequest(String input)
    {
      synchronized (currentLogs)
      {
        validateLogsAreNotFromMonitor(currentLogs);

        if (MonitorConstants.srvCmd_connCheck.equals(input))
        {
          final ArrayList<String> payload = new ArrayList<String>(Arrays.asList(getPid(), getPackageName(), ""));
          return new ArrayList<ArrayList<String>>(Collections.singletonList(payload));

        } else if (MonitorConstants.srvCmd_get_logs.equals(input))
        {
          ArrayList<ArrayList<String>> logsToSend = new ArrayList<ArrayList<String>>(currentLogs);
          currentLogs.clear();

          return logsToSend;

        } else if (MonitorConstants.srvCmd_get_time.equals(input))
        {
          final String time = getNowDate();

          final ArrayList<String> payload = new ArrayList<String>(Arrays.asList(time, null, null));

          Log.d(MonitorConstants.tag_srv, "getTime: " + time);
          return new ArrayList<ArrayList<String>>(Collections.singletonList(payload));

        } else if (MonitorConstants.srvCmd_close.equals(input))
        {
          monitorHook.finalizeMonitorHook();
          
          // In addition to the logic above, this command is handled in 
          // org.droidmate.monitor.MonitorJavaTemplate.MonitorTcpServer.shouldCloseServerSocket
          
          return new ArrayList<ArrayList<String>>();

        } else
        {
          Log.e(MonitorConstants.tag_srv, "! Unexpected command from DroidMate TCP client. The command: " + input);
          return new ArrayList<ArrayList<String>>();
        }
      }
    }

    private String getPackageName()
    {
      if (this.context != null)
        return this.context.getPackageName();
      else
        return "package name unavailable: context is null";
    }

    /**
     * <p>
     * This method ensures the logs do not come from messages logged by the MonitorTcpServer or 
     * MonitorJavaTemplate itself. This would be a bug and thus it will cause an assertion failure in this method.
     *
     * </p>
     * @param currentLogs
     * Currently recorded set of monitored logs that will be validated, causing AssertionError if validation fails.
     */
    private void validateLogsAreNotFromMonitor(List<ArrayList<String>> currentLogs)
    {
      for (ArrayList<String> log : currentLogs)
      {
        // ".get(2)" gets the payload. For details, see the doc of the param passed to this method.
        String msgPayload = log.get(2);
        failOnLogsFromMonitorTCPServerOrMonitorJavaTemplate(msgPayload);

      }
    }

    private void failOnLogsFromMonitorTCPServerOrMonitorJavaTemplate(String msgPayload)
    {
      if (msgPayload.contains(MonitorConstants.tag_srv) || msgPayload.contains(MonitorConstants.tag_mjt))
        throw new AssertionError(
          "Attempt to log a message whose payload contains " +
            MonitorConstants.tag_srv + " or " + MonitorConstants.tag_mjt + ". The message payload: " + msgPayload);
    }

    @Override
    protected boolean shouldCloseServerSocket(String serverInput)
    {
      return MonitorConstants.srvCmd_close.equals(serverInput);
    }
  }

  // !!! DUPLICATION WARNING !!! with org.droidmate.uiautomator_daemon.UiautomatorDaemonTcpServerBase
  static abstract class TcpServerBase<ServerInputT extends Serializable, ServerOutputT extends Serializable>
  {
    int port;
    private ServerSocket    serverSocket          = null;
    private SocketException serverSocketException = null;

    protected TcpServerBase()
    {
      super();
    }

    protected abstract ServerOutputT OnServerRequest(ServerInputT input);

    protected abstract boolean shouldCloseServerSocket(ServerInputT serverInput);

    public Thread tryStart(int port) throws Exception
    {
      Log.v(MonitorConstants.tag_srv, String.format("tryStart(port:%d): entering", port));
      this.serverSocket = null;
      this.serverSocketException = null;
      this.port = port;

      MonitorServerRunnable monitorServerRunnable = new MonitorServerRunnable();
      Thread serverThread = new Thread(monitorServerRunnable);
      // For explanation why this synchronization is necessary, see MonitorServerRunnable.run() method synchronized {} block.
      synchronized (monitorServerRunnable)
      {
        if (!(serverSocket == null && serverSocketException == null)) throw new AssertionError();
        serverThread.start();
        monitorServerRunnable.wait();
        // Either a serverSocket has been established, or an exception was thrown, but not both.
        //noinspection SimplifiableBooleanExpression
        if (!(serverSocket != null ^ serverSocketException != null)) throw new AssertionError();
      }
      if (serverSocketException != null)
      {

        if ("bind failed: EADDRINUSE (Address already in use)".equals(serverSocketException.getCause().getMessage()))
        {
          Log.v(MonitorConstants.tag_srv, "tryStart(port:"+port+"): FAILURE Failed to start TCP server because " +
            "'bind failed: EADDRINUSE (Address already in use)'. " +
            "Returning null Thread.");

          return null;

        } else
        {
          throw new Exception(String.format("Failed to start monitor TCP server thread for port %s. " +
              "Cause of this exception is the one returned by the failed thread.", port),
            serverSocketException);
        }
      }
      
      Log.d(MonitorConstants.tag_srv, "tryStart(port:"+port+"): SUCCESS");
      return serverThread;
    }

    public void closeServerSocket()
    {
      try
      {
        serverSocket.close();
        Log.d(MonitorConstants.tag_srv, String.format("serverSocket.close(): SUCCESS port %s", port));
        
      } catch (IOException e)
      {
        Log.e(MonitorConstants.tag_srv, String.format("serverSocket.close(): FAILURE port %s", port));
      }
    }

    public boolean isClosed()
    {
      return serverSocket.isClosed();
    }

    private class MonitorServerRunnable implements Runnable
    {


      public void run()
      {

        Log.v(MonitorConstants.tag_run, String.format("run(): entering port:%d", port));
        try
        {

          // Synchronize to ensure the parent thread (the one which started this one) will continue only after one of these two
          // is true:
          // - serverSocket was successfully initialized 
          // - exception was thrown and assigned to a field and  this thread exitted
          synchronized (this)
          {
            try
            {
              Log.v(MonitorConstants.tag_run, String.format("serverSocket = new ServerSocket(%d)", port));
              serverSocket = new ServerSocket(port);
              Log.v(MonitorConstants.tag_run, String.format("serverSocket = new ServerSocket(%d): SUCCESS", port));
            } catch (SocketException e)
            {
              serverSocketException = e;
            }

            if (serverSocketException != null)
            {
              Log.d(MonitorConstants.tag_run, "serverSocket = new ServerSocket("+port+"): FAILURE " +
                "aborting further thread execution.");
              this.notify();
              return;
            } else
            {
              this.notify();
            }
          }

          if (serverSocket == null) throw new AssertionError();
          if (serverSocketException != null) throw new AssertionError();

          while (!serverSocket.isClosed())
          {
            Log.v(MonitorConstants.tag_run, String.format("clientSocket = serverSocket.accept() / port:%d", port));
            Socket clientSocket = serverSocket.accept();
            Log.v(MonitorConstants.tag_run, String.format("clientSocket = serverSocket.accept(): SUCCESS / port:%d", port));

            ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

          /*
           * Flushing done to prevent client blocking on creation of input stream reading output from this stream. See:
           * org.droidmate.device.SerializableTCPClient.queryServer
           *
           * References:
           * 1. http://stackoverflow.com/questions/8088557/getinputstream-blocks
           * 2. Search for: "Note - The ObjectInputStream constructor blocks until" in:
           * http://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html
           */
            output.flush();

            ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
            ServerInputT serverInput;

            try
            {
              @SuppressWarnings("unchecked") // Without this var here, there is no place to put the "unchecked" suppression warning.
                ServerInputT localVarForSuppressionAnnotation = (ServerInputT) input.readObject();
              serverInput = localVarForSuppressionAnnotation;

            } catch (Exception e)
            {
              Log.e(MonitorConstants.tag_run, "! serverInput = input.readObject(): FAILURE " +
                "while reading from clientSocket on port "+port +". Closing server socket.", e);
              closeServerSocket();
              break;
            }

            ServerOutputT serverOutput;
            Log.d(MonitorConstants.tag_run, String.format("OnServerRequest(%s) / port:%d", serverInput, port));
            serverOutput = OnServerRequest(serverInput);
            output.writeObject(serverOutput);
            clientSocket.close();

            if (shouldCloseServerSocket(serverInput))
            {
              Log.v(MonitorConstants.tag_run, String.format("shouldCloseServerSocket(): true / port:%d", port));
              closeServerSocket();
            }
          }
          
          if (!serverSocket.isClosed()) throw new AssertionError();

          Log.v(MonitorConstants.tag_run, String.format("serverSocket.isClosed() / port:%d", port));

        } catch (SocketTimeoutException e)
        {
          Log.e(MonitorConstants.tag_run, "! Closing monitor TCP server due to a timeout.", e);
          closeServerSocket();
        } catch (IOException e)
        {
          Log.e(MonitorConstants.tag_run, "! Exception was thrown while operating monitor TCP server.", e);
        }
      }

    }
  }
  //endregion

  //region Helper code
  private static ArrayList<Integer> ctorHandles = new ArrayList<Integer>();

  private static String getStackTrace()
  {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < stackTrace.length; i++)
    {
      sb.append(stackTrace[i].toString());
      if (i < stackTrace.length - 1)
        sb.append(Api.stack_trace_frame_delimiter);
    }
    return sb.toString();
  }

  private static long getThreadId()
  {
    return Thread.currentThread().getId();
  }

  static String convert(Object param)
  {
    if (param == null)
      return "null";

    String paramStr;
    if (param.getClass().isArray())
    {
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;

      Object[] objects = convertToObjectArray(param);

      for (Object obj : objects)
      {

        if (!first)
          sb.append(",");
        first = false;

        sb.append(String.format("%s", obj));
      }
      sb.append("]");

      paramStr = sb.toString();
    } else if (param instanceof android.content.Intent)
    {
      paramStr = ((android.content.Intent) param).toUri(1);
      if (!paramStr.endsWith("end")) throw new AssertionError();

      /*
        Logcat buffer size is 4096 [1]. I have encountered a case in which intent's string extra has eaten up entire log line,
        preventing the remaining parts of the log (in particular, stack trace) to be transferred to DroidMate,
        causing regex match fail. This is how the offending intent value looked like:

          intent:#Intent;action=com.picsart.studio.notification.action;S.extra.result.string=%7B%22response%22%3A%5B%7B%...
          ...<and_so_on_until_entire_line_buffer_was_eaten>

        [1] http://stackoverflow.com/questions/6321555/what-is-the-size-limit-for-logcat
      */
      if (paramStr.length() > 1024)
      {
        paramStr = paramStr.substring(0, 1024 - 24) + "_TRUNCATED_TO_1000_CHARS" + "end";
      }

    } else
    {
      paramStr = String.format("%s", param);
      if (paramStr.length() > 1024)
      {
        paramStr = paramStr.substring(0, 1024 - 24) + "_TRUNCATED_TO_1000_CHARS";
      }
    }

    // !!! DUPLICATION WARNING !!! with: org.droidmate.logcat.Api.spaceEscapeInParamValue
    // solution would be to provide this method with an generated code injection point.
    // end of duplication warning
    return paramStr.replace(" ", "_");
  }

  // Copied from http://stackoverflow.com/a/16428065/986533
  private static Object[] convertToObjectArray(Object array)
  {
    Class ofArray = array.getClass().getComponentType();
    if (ofArray.isPrimitive())
    {
      List<Object> ar = new ArrayList<>();
      int length = Array.getLength(array);
      for (int i = 0; i < length; i++)
      {
        ar.add(Array.get(array, i));
      }
      return ar.toArray();
    } else
    {
      return (Object[]) array;
    }
  }

  private static final SimpleDateFormat monitor_time_formatter = new SimpleDateFormat(MonitorConstants.monitor_time_formatter_pattern, MonitorConstants.monitor_time_formatter_locale);

  /**
   * <p>
   * Called by monitor code to log Android API calls. Calls to this methods are generated in:
   * <pre>
   * org.droidmate.monitor.RedirectionsGenerator#generateCtorCallsAndTargets(java.util.List)
   * org.droidmate.monitor.RedirectionsGenerator#generateMethodTargets(java.util.List)</pre>
   * </p>
   * This method has to be accessed in a synchronized manner to ensure proper access to the {@code currentLogs} list and also
   * to ensure calls to {@code SimpleDateFormat.format(new Date())} return correct results.
   * If there was interleaving between threads, the calls non-deterministically returned invalid dates,
   * which caused {@code LocalDateTime.parse()} on the host machine, called by
   * {@code org.droidmate.exploration.device.ApiLogsReader.extractLogcatMessagesFromTcpMessages()}
   * to fail with exceptions like
   * <pre>java.time.format.DateTimeParseException: Text '2015-08-21 019:15:43.607' could not be parsed at index 13</pre>
   *
   * Examples of two different values returned by two consecutive calls to the faulty method,
   * first bad, second good:
   * <pre>
   * 2015-0008-0021 0019:0015:43.809
   * 2015-08-21 19:15:43.809
   *
   * 2015-08-21 19:015:43.804
   * 2015-08-21 19:15:43.804</pre>
   * More examples of faulty output:
   * <pre>
   *   2015-0008-05 09:24:12.163
   *   2015-0008-19 22:49:50.492
   *   2015-08-21 18:50:047.169
   *   2015-08-21 19:03:25.24
   *   2015-08-28 23:03:28.0453</pre>
   */
  @SuppressWarnings("unused") // See javadoc
  private static void addCurrentLogs(String payload)
  {
    synchronized (currentLogs)
    {
//      Log.v(tag_mjt, "addCurrentLogs(" + payload + ")");
      String now = getNowDate();

//      Log.v(tag_mjt, "currentLogs.add(new ArrayList<String>(Arrays.asList(getPid(), now, payload)));");
      currentLogs.add(new ArrayList<String>(Arrays.asList(getPid(), now, payload)));

//      Log.v(tag_mjt, "addCurrentLogs(" + payload + "): DONE");
    }
  }

  /**
   * @see #getNowDate()
   */
  private static final Date startDate     = new Date();
  /**
   * @see #getNowDate()
   */
  private static final long startNanoTime = System.nanoTime();

  /**
   * <p>
   * We use this more complex solution instead of simple {@code new Date()} because the simple solution uses
   * {@code System.currentTimeMillis()} which is imprecise, as described here:
   * http://stackoverflow.com/questions/2978598/will-sytem-currenttimemillis-always-return-a-value-previous-calls<br/>
   * http://stackoverflow.com/a/2979239/986533
   *
   * </p><p>
   * Instead, we construct Date only once ({@link #startDate}), on startup, remembering also its time offset from last boot
   * ({@link #startNanoTime}) and then we add offset to it in {@code System.nanoTime()},  which is precise.
   *
   * </p>
   */
  private static String getNowDate()
  {
//    Log.v(tag_mjt, "final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);");
    final Date nowDate = new Date(startDate.getTime() + (System.nanoTime() - startNanoTime) / 1000000);

//    Log.v(tag_mjt, "final String formattedDate = monitor_time_formatter.format(nowDate);");
    final String formattedDate = monitor_time_formatter.format(nowDate);

//    Log.v(tag_mjt, "return formattedDate;");
    return formattedDate;
  }

  private static String getPid()
  {
    return String.valueOf(android.os.Process.myPid());
  }

  /**
   * <p> Contains API logs gathered by monitor, to be transferred to the host machine when appropriate command is read by the
   * TCP server.
   *
   * </p><p>
   * Each log is a 3 element array obeying following contract:<br/>
   * log[0]: process ID of the log<br/>
   * log[1]: timestamp of the log<br/>
   * log[2]: the payload of the log (method name, parameter values, stack trace, etc.)
   *
   * </p>
   * @see MonitorJavaTemplate#addCurrentLogs(java.lang.String)
   */
  final static List<ArrayList<String>> currentLogs = new ArrayList<ArrayList<String>>();

  //endregion

  //region Hook code
  public static IMonitorHook monitorHook = new MonitorHook();
  //endregion
  
  //region Generated code

  private static void redirectConstructors()
  {
    ClassLoader[] classLoaders = {Thread.currentThread().getContextClassLoader(), Monitor.class.getClassLoader()};


  }

    @Hook("android.media.AudioRecord-><init>") 
    public static void redir_0_android_media_AudioRecord_ctor5(Object _this, int p0, int p1, int p2, int p3, int p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioRecord mthd: <init> retCls: void params: int "+convert(p0)+" int "+convert(p1)+" int "+convert(p2)+" int "+convert(p3)+" int "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioRecord mthd: <init> retCls: void params: int "+convert(p0)+" int "+convert(p1)+" int "+convert(p2)+" int "+convert(p3)+" int "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioRecord mthd: <init> retCls: void params: int "+convert(p0)+" int "+convert(p1)+" int "+convert(p2)+" int "+convert(p3)+" int "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioRecord mthd: <init> retCls: void params: int "+convert(p0)+" int "+convert(p1)+" int "+convert(p2)+" int "+convert(p3)+" int "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket-><init>") 
    public static void redir_1_java_net_Socket_ctor1(Object _this, java.net.Proxy p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket-><init>") 
    public static void redir_2_java_net_Socket_ctor4(Object _this, java.lang.String p0, int p1, java.net.InetAddress p2, int p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket-><init>") 
    public static void redir_3_java_net_Socket_ctor3(Object _this, java.lang.String p0, int p1, boolean p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket-><init>") 
    public static void redir_4_java_net_Socket_ctor2(Object _this, java.net.InetAddress p0, int p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket-><init>") 
    public static void redir_5_java_net_Socket_ctor4(Object _this, java.net.InetAddress p0, int p1, java.net.InetAddress p2, int p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" java.net.InetAddress "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket-><init>") 
    public static void redir_6_java_net_Socket_ctor3(Object _this, java.net.InetAddress p0, int p1, boolean p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: <init> retCls: void params: java.net.InetAddress "+convert(p0)+" int "+convert(p1)+" boolean "+convert(p2)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.DatagramSocket-><init>") 
    public static void redir_7_java_net_DatagramSocket_ctor1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.DatagramSocket-><init>") 
    public static void redir_8_java_net_DatagramSocket_ctor2(Object _this, int p0, java.net.InetAddress p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" java.net.InetAddress "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" java.net.InetAddress "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" java.net.InetAddress "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: int "+convert(p0)+" java.net.InetAddress "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.DatagramSocket-><init>") 
    public static void redir_9_java_net_DatagramSocket_ctor1(Object _this, java.net.SocketAddress p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: <init> retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.URL-><init>") 
    public static void redir_10_java_net_URL_ctor3(Object _this, java.net.URL p0, java.lang.String p1, java.net.URLStreamHandler p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.net.URL "+convert(p0)+" java.lang.String "+convert(p1)+" java.net.URLStreamHandler "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.net.URL "+convert(p0)+" java.lang.String "+convert(p1)+" java.net.URLStreamHandler "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.net.URL "+convert(p0)+" java.lang.String "+convert(p1)+" java.net.URLStreamHandler "+convert(p2)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.net.URL "+convert(p0)+" java.lang.String "+convert(p1)+" java.net.URLStreamHandler "+convert(p2)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.URL-><init>") 
    public static void redir_11_java_net_URL_ctor5(Object _this, java.lang.String p0, java.lang.String p1, int p2, java.lang.String p3, java.net.URLStreamHandler p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" int "+convert(p2)+" java.lang.String "+convert(p3)+" java.net.URLStreamHandler "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" int "+convert(p2)+" java.lang.String "+convert(p3)+" java.net.URLStreamHandler "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" int "+convert(p2)+" java.lang.String "+convert(p3)+" java.net.URLStreamHandler "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.URL mthd: <init> retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" int "+convert(p2)+" java.lang.String "+convert(p3)+" java.net.URLStreamHandler "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    


    @Hook("android.app.ActivityThread->installContentProviders") 
    public static void redir_android_app_ActivityThread_installContentProviders2(Object _this, android.content.Context p0, java.util.List p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.app.ActivityThread mthd: installContentProviders retCls: void params: android.content.Context "+convert(p0)+" java.util.List "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.app.ActivityThread mthd: installContentProviders retCls: void params: android.content.Context "+convert(p0)+" java.util.List "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.app.ActivityThread mthd: installContentProviders retCls: void params: android.content.Context "+convert(p0)+" java.util.List "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.app.ActivityThread mthd: installContentProviders retCls: void params: android.content.Context "+convert(p0)+" java.util.List "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.app.ActivityManager->getRecentTasks") 
    public static java.util.List redir_android_app_ActivityManager_getRecentTasks2(Object _this, int p0, int p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRecentTasks retCls: java.util.List params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRecentTasks retCls: java.util.List params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRecentTasks retCls: java.util.List params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (java.util.List) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRecentTasks retCls: java.util.List params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"", (java.util.List)returnVal);
    }
    
    @Hook("android.app.ActivityManager->getRunningTasks") 
    public static java.util.List redir_android_app_ActivityManager_getRunningTasks1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRunningTasks retCls: java.util.List params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRunningTasks retCls: java.util.List params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRunningTasks retCls: java.util.List params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (java.util.List) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.app.ActivityManager mthd: getRunningTasks retCls: java.util.List params: int "+convert(p0)+" stacktrace: "+stackTrace+"", (java.util.List)returnVal);
    }
    
    @Hook("android.bluetooth.BluetoothHeadset->startVoiceRecognition") 
    public static boolean redir_android_bluetooth_BluetoothHeadset_startVoiceRecognition1(Object _this, android.bluetooth.BluetoothDevice p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: startVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: startVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: startVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: startVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.bluetooth.BluetoothHeadset->stopVoiceRecognition") 
    public static boolean redir_android_bluetooth_BluetoothHeadset_stopVoiceRecognition1(Object _this, android.bluetooth.BluetoothDevice p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: stopVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: stopVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: stopVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.bluetooth.BluetoothHeadset mthd: stopVoiceRecognition retCls: boolean params: android.bluetooth.BluetoothDevice "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.hardware.Camera->open") 
    public static android.hardware.Camera redir_android_hardware_Camera_open1(int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.hardware.Camera mthd: open retCls: android.hardware.Camera params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.hardware.Camera mthd: open retCls: android.hardware.Camera params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.hardware.Camera mthd: open retCls: android.hardware.Camera params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invokeStatic(p0);
        return (android.hardware.Camera) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.hardware.Camera mthd: open retCls: android.hardware.Camera params: int "+convert(p0)+" stacktrace: "+stackTrace+"", (android.hardware.Camera)returnVal);
    }
    
    @Hook("android.location.LocationManager->addGpsStatusListener") 
    public static boolean redir_android_location_LocationManager_addGpsStatusListener1(Object _this, android.location.GpsStatus.Listener p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addGpsStatusListener retCls: boolean params: android.location.GpsStatus.Listener "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: addGpsStatusListener retCls: boolean params: android.location.GpsStatus.Listener "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: addGpsStatusListener retCls: boolean params: android.location.GpsStatus.Listener "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addGpsStatusListener retCls: boolean params: android.location.GpsStatus.Listener "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.location.LocationManager->addNmeaListener") 
    public static boolean redir_android_location_LocationManager_addNmeaListener1(Object _this, android.location.GpsStatus.NmeaListener p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addNmeaListener retCls: boolean params: android.location.GpsStatus.NmeaListener "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: addNmeaListener retCls: boolean params: android.location.GpsStatus.NmeaListener "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: addNmeaListener retCls: boolean params: android.location.GpsStatus.NmeaListener "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addNmeaListener retCls: boolean params: android.location.GpsStatus.NmeaListener "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.location.LocationManager->addProximityAlert") 
    public static void redir_android_location_LocationManager_addProximityAlert5(Object _this, double p0, double p1, float p2, long p3, android.app.PendingIntent p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addProximityAlert retCls: void params: double "+convert(p0)+" double "+convert(p1)+" float "+convert(p2)+" long "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: addProximityAlert retCls: void params: double "+convert(p0)+" double "+convert(p1)+" float "+convert(p2)+" long "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: addProximityAlert retCls: void params: double "+convert(p0)+" double "+convert(p1)+" float "+convert(p2)+" long "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addProximityAlert retCls: void params: double "+convert(p0)+" double "+convert(p1)+" float "+convert(p2)+" long "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->addTestProvider") 
    public static void redir_android_location_LocationManager_addTestProvider10(Object _this, java.lang.String p0, boolean p1, boolean p2, boolean p3, boolean p4, boolean p5, boolean p6, boolean p7, int p8, int p9)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addTestProvider retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" boolean "+convert(p2)+" boolean "+convert(p3)+" boolean "+convert(p4)+" boolean "+convert(p5)+" boolean "+convert(p6)+" boolean "+convert(p7)+" int "+convert(p8)+" int "+convert(p9)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: addTestProvider retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" boolean "+convert(p2)+" boolean "+convert(p3)+" boolean "+convert(p4)+" boolean "+convert(p5)+" boolean "+convert(p6)+" boolean "+convert(p7)+" int "+convert(p8)+" int "+convert(p9)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: addTestProvider retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" boolean "+convert(p2)+" boolean "+convert(p3)+" boolean "+convert(p4)+" boolean "+convert(p5)+" boolean "+convert(p6)+" boolean "+convert(p7)+" int "+convert(p8)+" int "+convert(p9)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: addTestProvider retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" boolean "+convert(p2)+" boolean "+convert(p3)+" boolean "+convert(p4)+" boolean "+convert(p5)+" boolean "+convert(p6)+" boolean "+convert(p7)+" int "+convert(p8)+" int "+convert(p9)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->clearTestProviderEnabled") 
    public static void redir_android_location_LocationManager_clearTestProviderEnabled1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->clearTestProviderLocation") 
    public static void redir_android_location_LocationManager_clearTestProviderLocation1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->clearTestProviderStatus") 
    public static void redir_android_location_LocationManager_clearTestProviderStatus1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: clearTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->getBestProvider") 
    public static java.lang.String redir_android_location_LocationManager_getBestProvider2(Object _this, android.location.Criteria p0, boolean p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getBestProvider retCls: java.lang.String params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: getBestProvider retCls: java.lang.String params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: getBestProvider retCls: java.lang.String params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getBestProvider retCls: java.lang.String params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.location.LocationManager->getLastKnownLocation") 
    public static android.location.Location redir_android_location_LocationManager_getLastKnownLocation1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getLastKnownLocation retCls: android.location.Location params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: getLastKnownLocation retCls: android.location.Location params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: getLastKnownLocation retCls: android.location.Location params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (android.location.Location) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getLastKnownLocation retCls: android.location.Location params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", (android.location.Location)returnVal);
    }
    
    @Hook("android.location.LocationManager->getProvider") 
    public static android.location.LocationProvider redir_android_location_LocationManager_getProvider1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProvider retCls: android.location.LocationProvider params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: getProvider retCls: android.location.LocationProvider params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProvider retCls: android.location.LocationProvider params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (android.location.LocationProvider) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProvider retCls: android.location.LocationProvider params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", (android.location.LocationProvider)returnVal);
    }
    
    @Hook("android.location.LocationManager->getProviders") 
    public static java.util.List redir_android_location_LocationManager_getProviders2(Object _this, android.location.Criteria p0, boolean p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (java.util.List) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: android.location.Criteria "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"", (java.util.List)returnVal);
    }
    
    @Hook("android.location.LocationManager->getProviders") 
    public static java.util.List redir_android_location_LocationManager_getProviders1(Object _this, boolean p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: boolean "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (java.util.List) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: getProviders retCls: java.util.List params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"", (java.util.List)returnVal);
    }
    
    @Hook("android.location.LocationManager->isProviderEnabled") 
    public static boolean redir_android_location_LocationManager_isProviderEnabled1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: isProviderEnabled retCls: boolean params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: isProviderEnabled retCls: boolean params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: isProviderEnabled retCls: boolean params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: isProviderEnabled retCls: boolean params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.location.LocationManager->removeTestProvider") 
    public static void redir_android_location_LocationManager_removeTestProvider1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: removeTestProvider retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: removeTestProvider retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: removeTestProvider retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: removeTestProvider retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestLocationUpdates") 
    public static void redir_android_location_LocationManager_requestLocationUpdates4(Object _this, long p0, float p1, android.location.Criteria p2, android.app.PendingIntent p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestLocationUpdates") 
    public static void redir_android_location_LocationManager_requestLocationUpdates5(Object _this, long p0, float p1, android.location.Criteria p2, android.location.LocationListener p3, android.os.Looper p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: long "+convert(p0)+" float "+convert(p1)+" android.location.Criteria "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestLocationUpdates") 
    public static void redir_android_location_LocationManager_requestLocationUpdates4(Object _this, java.lang.String p0, long p1, float p2, android.app.PendingIntent p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestLocationUpdates") 
    public static void redir_android_location_LocationManager_requestLocationUpdates4(Object _this, java.lang.String p0, long p1, float p2, android.location.LocationListener p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestLocationUpdates") 
    public static void redir_android_location_LocationManager_requestLocationUpdates5(Object _this, java.lang.String p0, long p1, float p2, android.location.LocationListener p3, android.os.Looper p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestLocationUpdates retCls: void params: java.lang.String "+convert(p0)+" long "+convert(p1)+" float "+convert(p2)+" android.location.LocationListener "+convert(p3)+" android.os.Looper "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestSingleUpdate") 
    public static void redir_android_location_LocationManager_requestSingleUpdate2(Object _this, android.location.Criteria p0, android.app.PendingIntent p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestSingleUpdate") 
    public static void redir_android_location_LocationManager_requestSingleUpdate3(Object _this, android.location.Criteria p0, android.location.LocationListener p1, android.os.Looper p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: android.location.Criteria "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestSingleUpdate") 
    public static void redir_android_location_LocationManager_requestSingleUpdate2(Object _this, java.lang.String p0, android.app.PendingIntent p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.app.PendingIntent "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->requestSingleUpdate") 
    public static void redir_android_location_LocationManager_requestSingleUpdate3(Object _this, java.lang.String p0, android.location.LocationListener p1, android.os.Looper p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: requestSingleUpdate retCls: void params: java.lang.String "+convert(p0)+" android.location.LocationListener "+convert(p1)+" android.os.Looper "+convert(p2)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->sendExtraCommand") 
    public static boolean redir_android_location_LocationManager_sendExtraCommand3(Object _this, java.lang.String p0, java.lang.String p1, android.os.Bundle p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: sendExtraCommand retCls: boolean params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: sendExtraCommand retCls: boolean params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: sendExtraCommand retCls: boolean params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: sendExtraCommand retCls: boolean params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.location.LocationManager->setTestProviderEnabled") 
    public static void redir_android_location_LocationManager_setTestProviderEnabled2(Object _this, java.lang.String p0, boolean p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderEnabled retCls: void params: java.lang.String "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->setTestProviderLocation") 
    public static void redir_android_location_LocationManager_setTestProviderLocation2(Object _this, java.lang.String p0, android.location.Location p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" android.location.Location "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" android.location.Location "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" android.location.Location "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderLocation retCls: void params: java.lang.String "+convert(p0)+" android.location.Location "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.location.LocationManager->setTestProviderStatus") 
    public static void redir_android_location_LocationManager_setTestProviderStatus4(Object _this, java.lang.String p0, int p1, android.os.Bundle p2, long p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" android.os.Bundle "+convert(p2)+" long "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" android.os.Bundle "+convert(p2)+" long "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" android.os.Bundle "+convert(p2)+" long "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.location.LocationManager mthd: setTestProviderStatus retCls: void params: java.lang.String "+convert(p0)+" int "+convert(p1)+" android.os.Bundle "+convert(p2)+" long "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->isBluetoothA2dpOn") 
    public static boolean redir_android_media_AudioManager_isBluetoothA2dpOn0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: isBluetoothA2dpOn retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: isBluetoothA2dpOn retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: isBluetoothA2dpOn retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: isBluetoothA2dpOn retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.media.AudioManager->isWiredHeadsetOn") 
    public static boolean redir_android_media_AudioManager_isWiredHeadsetOn0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: isWiredHeadsetOn retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: isWiredHeadsetOn retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: isWiredHeadsetOn retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: isWiredHeadsetOn retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.media.AudioManager->setBluetoothScoOn") 
    public static void redir_android_media_AudioManager_setBluetoothScoOn1(Object _this, boolean p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setBluetoothScoOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: setBluetoothScoOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: setBluetoothScoOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setBluetoothScoOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->setMicrophoneMute") 
    public static void redir_android_media_AudioManager_setMicrophoneMute1(Object _this, boolean p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setMicrophoneMute retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: setMicrophoneMute retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: setMicrophoneMute retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setMicrophoneMute retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->setMode") 
    public static void redir_android_media_AudioManager_setMode1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setMode retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: setMode retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: setMode retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setMode retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->setParameter") 
    public static void redir_android_media_AudioManager_setParameter2(Object _this, java.lang.String p0, java.lang.String p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameter retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameter retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameter retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameter retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->setParameters") 
    public static void redir_android_media_AudioManager_setParameters1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameters retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameters retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameters retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setParameters retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->setSpeakerphoneOn") 
    public static void redir_android_media_AudioManager_setSpeakerphoneOn1(Object _this, boolean p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setSpeakerphoneOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: setSpeakerphoneOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: setSpeakerphoneOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: setSpeakerphoneOn retCls: void params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->startBluetoothSco") 
    public static void redir_android_media_AudioManager_startBluetoothSco0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: startBluetoothSco retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: startBluetoothSco retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: startBluetoothSco retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: startBluetoothSco retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.AudioManager->stopBluetoothSco") 
    public static void redir_android_media_AudioManager_stopBluetoothSco0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: stopBluetoothSco retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.AudioManager mthd: stopBluetoothSco retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.AudioManager mthd: stopBluetoothSco retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.AudioManager mthd: stopBluetoothSco retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.MediaPlayer->setWakeMode") 
    public static void redir_android_media_MediaPlayer_setWakeMode2(Object _this, android.content.Context p0, int p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.MediaPlayer mthd: setWakeMode retCls: void params: android.content.Context "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.MediaPlayer mthd: setWakeMode retCls: void params: android.content.Context "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.MediaPlayer mthd: setWakeMode retCls: void params: android.content.Context "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.MediaPlayer mthd: setWakeMode retCls: void params: android.content.Context "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.MediaRecorder->setAudioSource") 
    public static void redir_android_media_MediaRecorder_setAudioSource1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setAudioSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setAudioSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setAudioSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setAudioSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.media.MediaRecorder->setVideoSource") 
    public static void redir_android_media_MediaRecorder_setVideoSource1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setVideoSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setVideoSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setVideoSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.media.MediaRecorder mthd: setVideoSource retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.net.ConnectivityManager->requestRouteToHost") 
    public static boolean redir_android_net_ConnectivityManager_requestRouteToHost2(Object _this, int p0, int p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: requestRouteToHost retCls: boolean params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: requestRouteToHost retCls: boolean params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: requestRouteToHost retCls: boolean params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: requestRouteToHost retCls: boolean params: int "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.ConnectivityManager->setNetworkPreference") 
    public static void redir_android_net_ConnectivityManager_setNetworkPreference1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: setNetworkPreference retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: setNetworkPreference retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: setNetworkPreference retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: setNetworkPreference retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.net.ConnectivityManager->startUsingNetworkFeature") 
    public static int redir_android_net_ConnectivityManager_startUsingNetworkFeature2(Object _this, int p0, java.lang.String p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: startUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: startUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: startUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: startUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.net.ConnectivityManager->stopUsingNetworkFeature") 
    public static int redir_android_net_ConnectivityManager_stopUsingNetworkFeature2(Object _this, int p0, java.lang.String p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: stopUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: stopUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: stopUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: stopUsingNetworkFeature retCls: int params: int "+convert(p0)+" java.lang.String "+convert(p1)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.net.ConnectivityManager->tether") 
    public static int redir_android_net_ConnectivityManager_tether1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: tether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: tether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: tether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: tether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.net.ConnectivityManager->untether") 
    public static int redir_android_net_ConnectivityManager_untether1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: untether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: untether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: untether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.ConnectivityManager mthd: untether retCls: int params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager$MulticastLock->acquire") 
    public static void redir_android_net_wifi_WifiManager_MulticastLock_acquire0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.net.wifi.WifiManager$MulticastLock->release") 
    public static void redir_android_net_wifi_WifiManager_MulticastLock_release0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: release retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: release retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: release retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$MulticastLock mthd: release retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.net.wifi.WifiManager$WifiLock->acquire") 
    public static void redir_android_net_wifi_WifiManager_WifiLock_acquire0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.net.wifi.WifiManager$WifiLock->release") 
    public static void redir_android_net_wifi_WifiManager_WifiLock_release0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: release retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: release retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: release retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager$WifiLock mthd: release retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.net.wifi.WifiManager->addNetwork") 
    public static int redir_android_net_wifi_WifiManager_addNetwork1(Object _this, android.net.wifi.WifiConfiguration p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: addNetwork retCls: int params: android.net.wifi.WifiConfiguration "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: addNetwork retCls: int params: android.net.wifi.WifiConfiguration "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: addNetwork retCls: int params: android.net.wifi.WifiConfiguration "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: addNetwork retCls: int params: android.net.wifi.WifiConfiguration "+convert(p0)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->disableNetwork") 
    public static boolean redir_android_net_wifi_WifiManager_disableNetwork1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disableNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disableNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disableNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disableNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->disconnect") 
    public static boolean redir_android_net_wifi_WifiManager_disconnect0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disconnect retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disconnect retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disconnect retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: disconnect retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->enableNetwork") 
    public static boolean redir_android_net_wifi_WifiManager_enableNetwork2(Object _this, int p0, boolean p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: enableNetwork retCls: boolean params: int "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: enableNetwork retCls: boolean params: int "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: enableNetwork retCls: boolean params: int "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: enableNetwork retCls: boolean params: int "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->initializeMulticastFiltering") 
    public static boolean redir_android_net_wifi_WifiManager_initializeMulticastFiltering0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: initializeMulticastFiltering retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: initializeMulticastFiltering retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: initializeMulticastFiltering retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: initializeMulticastFiltering retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->pingSupplicant") 
    public static boolean redir_android_net_wifi_WifiManager_pingSupplicant0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: pingSupplicant retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: pingSupplicant retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: pingSupplicant retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: pingSupplicant retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->reassociate") 
    public static boolean redir_android_net_wifi_WifiManager_reassociate0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reassociate retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reassociate retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reassociate retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reassociate retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->reconnect") 
    public static boolean redir_android_net_wifi_WifiManager_reconnect0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reconnect retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reconnect retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reconnect retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: reconnect retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->removeNetwork") 
    public static boolean redir_android_net_wifi_WifiManager_removeNetwork1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: removeNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: removeNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: removeNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: removeNetwork retCls: boolean params: int "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->saveConfiguration") 
    public static boolean redir_android_net_wifi_WifiManager_saveConfiguration0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: saveConfiguration retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: saveConfiguration retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: saveConfiguration retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: saveConfiguration retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->setWifiApEnabled") 
    public static boolean redir_android_net_wifi_WifiManager_setWifiApEnabled2(Object _this, android.net.wifi.WifiConfiguration p0, boolean p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiApEnabled retCls: boolean params: android.net.wifi.WifiConfiguration "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiApEnabled retCls: boolean params: android.net.wifi.WifiConfiguration "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiApEnabled retCls: boolean params: android.net.wifi.WifiConfiguration "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiApEnabled retCls: boolean params: android.net.wifi.WifiConfiguration "+convert(p0)+" boolean "+convert(p1)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->setWifiEnabled") 
    public static boolean redir_android_net_wifi_WifiManager_setWifiEnabled1(Object _this, boolean p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiEnabled retCls: boolean params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiEnabled retCls: boolean params: boolean "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiEnabled retCls: boolean params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: setWifiEnabled retCls: boolean params: boolean "+convert(p0)+" stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.net.wifi.WifiManager->startScan") 
    public static boolean redir_android_net_wifi_WifiManager_startScan0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: startScan retCls: boolean params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: startScan retCls: boolean params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: startScan retCls: boolean params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (Boolean) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.net.wifi.WifiManager mthd: startScan retCls: boolean params:  stacktrace: "+stackTrace+"", (Boolean)returnVal);
    }
    
    @Hook("android.os.PowerManager$WakeLock->acquire") 
    public static void redir_android_os_PowerManager_WakeLock_acquire0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.os.PowerManager$WakeLock->acquire") 
    public static void redir_android_os_PowerManager_WakeLock_acquire1(Object _this, long p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params: long "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params: long "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params: long "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: acquire retCls: void params: long "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.os.PowerManager$WakeLock->release") 
    public static void redir_android_os_PowerManager_WakeLock_release1(Object _this, int p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: release retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: release retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: release retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.os.PowerManager$WakeLock mthd: release retCls: void params: int "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.speech.SpeechRecognizer->cancel") 
    public static void redir_android_speech_SpeechRecognizer_cancel0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: cancel retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: cancel retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: cancel retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: cancel retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.speech.SpeechRecognizer->handleCancelMessage") 
    public static void redir_android_speech_SpeechRecognizer_handleCancelMessage0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleCancelMessage retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleCancelMessage retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleCancelMessage retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleCancelMessage retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.speech.SpeechRecognizer->handleStartListening") 
    public static void redir_android_speech_SpeechRecognizer_handleStartListening1(Object _this, android.content.Intent p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStartListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStartListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStartListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStartListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.speech.SpeechRecognizer->handleStopMessage") 
    public static void redir_android_speech_SpeechRecognizer_handleStopMessage0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStopMessage retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStopMessage retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStopMessage retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: handleStopMessage retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.speech.SpeechRecognizer->startListening") 
    public static void redir_android_speech_SpeechRecognizer_startListening1(Object _this, android.content.Intent p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: startListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: startListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: startListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: startListening retCls: void params: android.content.Intent "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.speech.SpeechRecognizer->stopListening") 
    public static void redir_android_speech_SpeechRecognizer_stopListening0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: stopListening retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: stopListening retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: stopListening retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.speech.SpeechRecognizer mthd: stopListening retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.telephony.TelephonyManager->getCellLocation") 
    public static android.telephony.CellLocation redir_android_telephony_TelephonyManager_getCellLocation0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getCellLocation retCls: android.telephony.CellLocation params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getCellLocation retCls: android.telephony.CellLocation params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getCellLocation retCls: android.telephony.CellLocation params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (android.telephony.CellLocation) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getCellLocation retCls: android.telephony.CellLocation params:  stacktrace: "+stackTrace+"", (android.telephony.CellLocation)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getDeviceId") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getDeviceId0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceId retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceId retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceId retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceId retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getDeviceSoftwareVersion") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getDeviceSoftwareVersion0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceSoftwareVersion retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceSoftwareVersion retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceSoftwareVersion retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getDeviceSoftwareVersion retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getLine1Number") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getLine1Number0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getLine1Number retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getLine1Number retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getLine1Number retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getLine1Number retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getNeighboringCellInfo") 
    public static java.util.List redir_android_telephony_TelephonyManager_getNeighboringCellInfo0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getNeighboringCellInfo retCls: java.util.List params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getNeighboringCellInfo retCls: java.util.List params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getNeighboringCellInfo retCls: java.util.List params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.util.List) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getNeighboringCellInfo retCls: java.util.List params:  stacktrace: "+stackTrace+"", (java.util.List)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getSimSerialNumber") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getSimSerialNumber0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSimSerialNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSimSerialNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSimSerialNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSimSerialNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getSubscriberId") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getSubscriberId0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSubscriberId retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSubscriberId retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSubscriberId retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getSubscriberId retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getVoiceMailAlphaTag") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getVoiceMailAlphaTag0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailAlphaTag retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailAlphaTag retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailAlphaTag retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailAlphaTag retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->getVoiceMailNumber") 
    public static java.lang.String redir_android_telephony_TelephonyManager_getVoiceMailNumber0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.lang.String) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: getVoiceMailNumber retCls: java.lang.String params:  stacktrace: "+stackTrace+"", (java.lang.String)returnVal);
    }
    
    @Hook("android.telephony.TelephonyManager->listen") 
    public static void redir_android_telephony_TelephonyManager_listen2(Object _this, android.telephony.PhoneStateListener p0, int p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: listen retCls: void params: android.telephony.PhoneStateListener "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: listen retCls: void params: android.telephony.PhoneStateListener "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: listen retCls: void params: android.telephony.PhoneStateListener "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.TelephonyManager mthd: listen retCls: void params: android.telephony.PhoneStateListener "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.webkit.WebView->loadDataWithBaseURL") 
    public static void redir_android_webkit_WebView_loadDataWithBaseURL5(Object _this, java.lang.String p0, java.lang.String p1, java.lang.String p2, java.lang.String p3, java.lang.String p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadDataWithBaseURL retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String "+convert(p3)+" java.lang.String "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.webkit.WebView mthd: loadDataWithBaseURL retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String "+convert(p3)+" java.lang.String "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadDataWithBaseURL retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String "+convert(p3)+" java.lang.String "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadDataWithBaseURL retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String "+convert(p3)+" java.lang.String "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.webkit.WebView->loadUrl") 
    public static void redir_android_webkit_WebView_loadUrl1(Object _this, java.lang.String p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.webkit.WebView->loadUrl") 
    public static void redir_android_webkit_WebView_loadUrl2(Object _this, java.lang.String p0, java.util.Map p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" java.util.Map "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" java.util.Map "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" java.util.Map "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.webkit.WebView mthd: loadUrl retCls: void params: java.lang.String "+convert(p0)+" java.util.Map "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.telephony.SmsManager->sendTextMessage") 
    public static void redir_android_telephony_SmsManager_sendTextMessage5(Object _this, java.lang.String p0, java.lang.String p1, java.lang.String p2, android.app.PendingIntent p3, android.app.PendingIntent p4)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.telephony.SmsManager mthd: sendTextMessage retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.telephony.SmsManager mthd: sendTextMessage retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.telephony.SmsManager mthd: sendTextMessage retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.telephony.SmsManager mthd: sendTextMessage retCls: void params: java.lang.String "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String "+convert(p2)+" android.app.PendingIntent "+convert(p3)+" android.app.PendingIntent "+convert(p4)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.Socket->connect") 
    public static void redir_java_net_Socket_connect2(Object _this, java.net.SocketAddress p0, int p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.Socket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.Socket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.Socket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" int "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.DatagramSocket->connect") 
    public static void redir_java_net_DatagramSocket_connect1(Object _this, java.net.SocketAddress p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.DatagramSocket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.DatagramSocket mthd: connect retCls: void params: java.net.SocketAddress "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.MulticastSocket->joinGroup") 
    public static void redir_java_net_MulticastSocket_joinGroup1(Object _this, java.net.InetAddress p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.InetAddress "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.InetAddress "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.InetAddress "+convert(p0)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.InetAddress "+convert(p0)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.MulticastSocket->joinGroup") 
    public static void redir_java_net_MulticastSocket_joinGroup2(Object _this, java.net.SocketAddress p0, java.net.NetworkInterface p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.SocketAddress "+convert(p0)+" java.net.NetworkInterface "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.SocketAddress "+convert(p0)+" java.net.NetworkInterface "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.SocketAddress "+convert(p0)+" java.net.NetworkInterface "+convert(p1)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.MulticastSocket mthd: joinGroup retCls: void params: java.net.SocketAddress "+convert(p0)+" java.net.NetworkInterface "+convert(p1)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("java.net.URL->openConnection") 
    public static java.net.URLConnection redir_java_net_URL_openConnection0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params:  stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this);
        return (java.net.URLConnection) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params:  stacktrace: "+stackTrace+"", (java.net.URLConnection)returnVal);
    }
    
    @Hook("java.net.URL->openConnection") 
    public static java.net.URLConnection redir_java_net_URL_openConnection1(Object _this, java.net.Proxy p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (java.net.URLConnection) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.URL mthd: openConnection retCls: java.net.URLConnection params: java.net.Proxy "+convert(p0)+" stacktrace: "+stackTrace+"", (java.net.URLConnection)returnVal);
    }
    
    @Hook("java.net.URLConnection->connect") 
    public static void redir_java_net_URLConnection_connect0(Object _this)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: java.net.URLConnection mthd: connect retCls: void params:  stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: java.net.URLConnection mthd: connect retCls: void params:  stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: java.net.URLConnection mthd: connect retCls: void params:  stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: java.net.URLConnection mthd: connect retCls: void params:  stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("org.apache.http.impl.client.AbstractHttpClient->execute") 
    public static org.apache.http.HttpResponse redir_org_apache_http_impl_client_AbstractHttpClient_execute3(Object _this, org.apache.http.HttpHost p0, org.apache.http.HttpRequest p1, org.apache.http.protocol.HttpContext p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: org.apache.http.impl.client.AbstractHttpClient mthd: execute retCls: org.apache.http.HttpResponse params: org.apache.http.HttpHost "+convert(p0)+" org.apache.http.HttpRequest "+convert(p1)+" org.apache.http.protocol.HttpContext "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: org.apache.http.impl.client.AbstractHttpClient mthd: execute retCls: org.apache.http.HttpResponse params: org.apache.http.HttpHost "+convert(p0)+" org.apache.http.HttpRequest "+convert(p1)+" org.apache.http.protocol.HttpContext "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: org.apache.http.impl.client.AbstractHttpClient mthd: execute retCls: org.apache.http.HttpResponse params: org.apache.http.HttpHost "+convert(p0)+" org.apache.http.HttpRequest "+convert(p1)+" org.apache.http.protocol.HttpContext "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (org.apache.http.HttpResponse) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: org.apache.http.impl.client.AbstractHttpClient mthd: execute retCls: org.apache.http.HttpResponse params: org.apache.http.HttpHost "+convert(p0)+" org.apache.http.HttpRequest "+convert(p1)+" org.apache.http.protocol.HttpContext "+convert(p2)+" stacktrace: "+stackTrace+"", (org.apache.http.HttpResponse)returnVal);
    }
    
    @Hook("android.content.ContentResolver->bulkInsert") 
    public static int redir_android_content_ContentResolver_bulkInsert2(Object _this, android.net.Uri p0, android.content.ContentValues[] p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.content.ContentResolver->delete") 
    public static int redir_android_content_ContentResolver_delete3(Object _this, android.net.Uri p0, java.lang.String p1, java.lang.String[] p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.content.ContentResolver->insert") 
    public static android.net.Uri redir_android_content_ContentResolver_insert2(Object _this, android.net.Uri p0, android.content.ContentValues p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (android.net.Uri) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+"", (android.net.Uri)returnVal);
    }
    
    @Hook("android.content.ContentResolver->update") 
    public static int redir_android_content_ContentResolver_update4(Object _this, android.net.Uri p0, android.content.ContentValues p1, java.lang.String p2, java.lang.String[] p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.content.ContentResolver->openInputStream") 
    public static java.io.InputStream redir_android_content_ContentResolver_openInputStream1(Object _this, android.net.Uri p0)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: openInputStream retCls: java.io.InputStream params: android.net.Uri "+convert(p0)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: openInputStream retCls: java.io.InputStream params: android.net.Uri "+convert(p0)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: openInputStream retCls: java.io.InputStream params: android.net.Uri "+convert(p0)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0);
        return (java.io.InputStream) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: openInputStream retCls: java.io.InputStream params: android.net.Uri "+convert(p0)+" stacktrace: "+stackTrace+"", (java.io.InputStream)returnVal);
    }
    
    @Hook("android.content.ContentResolver->openAssetFileDescriptor") 
    public static android.content.res.AssetFileDescriptor redir_android_content_ContentResolver_openAssetFileDescriptor3(Object _this, android.net.Uri p0, java.lang.String p1, android.os.CancellationSignal p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: openAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: openAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: openAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (android.content.res.AssetFileDescriptor) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: openAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"", (android.content.res.AssetFileDescriptor)returnVal);
    }
    
    @Hook("android.content.ContentResolver->query") 
    public static android.database.Cursor redir_android_content_ContentResolver_query6(Object _this, android.net.Uri p0, java.lang.String[] p1, java.lang.String p2, java.lang.String[] p3, java.lang.String p4, android.os.CancellationSignal p5)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4, p5);
        return (android.database.Cursor) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+"", (android.database.Cursor)returnVal);
    }
    
    @Hook("android.content.ContentResolver->registerContentObserver") 
    public static void redir_android_content_ContentResolver_registerContentObserver4(Object _this, android.net.Uri p0, boolean p1, android.database.ContentObserver p2, int p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: registerContentObserver retCls: void params: android.net.Uri "+convert(p0)+" boolean "+convert(p1)+" android.database.ContentObserver "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentResolver mthd: registerContentObserver retCls: void params: android.net.Uri "+convert(p0)+" boolean "+convert(p1)+" android.database.ContentObserver "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentResolver mthd: registerContentObserver retCls: void params: android.net.Uri "+convert(p0)+" boolean "+convert(p1)+" android.database.ContentObserver "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"");
        OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentResolver mthd: registerContentObserver retCls: void params: android.net.Uri "+convert(p0)+" boolean "+convert(p1)+" android.database.ContentObserver "+convert(p2)+" int "+convert(p3)+" stacktrace: "+stackTrace+"", null);
    }
    
    @Hook("android.content.ContentProviderClient->bulkInsert") 
    public static int redir_android_content_ContentProviderClient_bulkInsert2(Object _this, android.net.Uri p0, android.content.ContentValues[] p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: bulkInsert retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues[] "+convert(p1)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->delete") 
    public static int redir_android_content_ContentProviderClient_delete3(Object _this, android.net.Uri p0, java.lang.String p1, java.lang.String[] p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: delete retCls: int params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" java.lang.String[] "+convert(p2)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->insert") 
    public static android.net.Uri redir_android_content_ContentProviderClient_insert2(Object _this, android.net.Uri p0, android.content.ContentValues p1)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1);
        return (android.net.Uri) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: insert retCls: android.net.Uri params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" stacktrace: "+stackTrace+"", (android.net.Uri)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->update") 
    public static int redir_android_content_ContentProviderClient_update4(Object _this, android.net.Uri p0, android.content.ContentValues p1, java.lang.String p2, java.lang.String[] p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        return (Integer) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: update retCls: int params: android.net.Uri "+convert(p0)+" android.content.ContentValues "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" stacktrace: "+stackTrace+"", (Integer)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->openFile") 
    public static android.os.ParcelFileDescriptor redir_android_content_ContentProviderClient_openFile3(Object _this, android.net.Uri p0, java.lang.String p1, android.os.CancellationSignal p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openFile retCls: android.os.ParcelFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openFile retCls: android.os.ParcelFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openFile retCls: android.os.ParcelFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (android.os.ParcelFileDescriptor) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openFile retCls: android.os.ParcelFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"", (android.os.ParcelFileDescriptor)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->openAssetFile") 
    public static android.content.res.AssetFileDescriptor redir_android_content_ContentProviderClient_openAssetFile3(Object _this, android.net.Uri p0, java.lang.String p1, android.os.CancellationSignal p2)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openAssetFile retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openAssetFile retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openAssetFile retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2);
        return (android.content.res.AssetFileDescriptor) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openAssetFile retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.CancellationSignal "+convert(p2)+" stacktrace: "+stackTrace+"", (android.content.res.AssetFileDescriptor)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->openTypedAssetFileDescriptor") 
    public static android.content.res.AssetFileDescriptor redir_android_content_ContentProviderClient_openTypedAssetFileDescriptor4(Object _this, android.net.Uri p0, java.lang.String p1, android.os.Bundle p2, android.os.CancellationSignal p3)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openTypedAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" android.os.CancellationSignal "+convert(p3)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openTypedAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" android.os.CancellationSignal "+convert(p3)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openTypedAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" android.os.CancellationSignal "+convert(p3)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3);
        return (android.content.res.AssetFileDescriptor) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: openTypedAssetFileDescriptor retCls: android.content.res.AssetFileDescriptor params: android.net.Uri "+convert(p0)+" java.lang.String "+convert(p1)+" android.os.Bundle "+convert(p2)+" android.os.CancellationSignal "+convert(p3)+" stacktrace: "+stackTrace+"", (android.content.res.AssetFileDescriptor)returnVal);
    }
    
    @Hook("android.content.ContentProviderClient->query") 
    public static android.database.Cursor redir_android_content_ContentProviderClient_query6(Object _this, android.net.Uri p0, java.lang.String[] p1, java.lang.String p2, java.lang.String[] p3, java.lang.String p4, android.os.CancellationSignal p5)
    {
        String stackTrace = getStackTrace();
        long threadId = getThreadId();
        monitorHook.hookBeforeApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+"");
        Log.i("Monitor_API_method_call", "TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+""); 
        addCurrentLogs("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+"");
        Object returnVal = OriginalMethod.by(new $() {}).invoke(_this, p0, p1, p2, p3, p4, p5);
        return (android.database.Cursor) monitorHook.hookAfterApiCall("TId: "+threadId+" objCls: android.content.ContentProviderClient mthd: query retCls: android.database.Cursor params: android.net.Uri "+convert(p0)+" java.lang.String[] "+convert(p1)+" java.lang.String "+convert(p2)+" java.lang.String[] "+convert(p3)+" java.lang.String "+convert(p4)+" android.os.CancellationSignal "+convert(p5)+" stacktrace: "+stackTrace+"", (android.database.Cursor)returnVal);
    }
    


  //endregion


}
