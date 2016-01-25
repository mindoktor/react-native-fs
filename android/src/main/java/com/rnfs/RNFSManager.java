package com.rnfs;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import android.os.Environment;
import android.os.AsyncTask;
import android.util.Base64;
import android.content.Context;
import android.support.annotation.Nullable;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;

import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNFSManager extends ReactContextBaseJavaModule {

  private static final String NSDocumentDirectoryPath = "NSDocumentDirectoryPath";
  private static final String NSDocumentDirectory = "NSDocumentDirectory";

  private static final String NSFileTypeRegular = "NSFileTypeRegular";
  private static final String NSFileTypeDirectory = "NSFileTypeDirectory";

  public RNFSManager(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "RNFSManager";
  }

  @ReactMethod
  public void writeFile(String filepath, String base64Content, ReadableMap options, Callback callback) {
    try {
      byte[] bytes = Base64.decode(base64Content, Base64.DEFAULT);

      FileOutputStream outputStream = new FileOutputStream(filepath);
      outputStream.write(bytes);
      outputStream.close();

      callback.invoke(null, true, filepath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void readFile(String filepath, Callback callback) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      FileInputStream inputStream = new FileInputStream(filepath);
      byte[] buffer = new byte[(int)file.length()];
      inputStream.read(buffer);

      String base64Content = Base64.encodeToString(buffer, Base64.NO_WRAP);

      callback.invoke(null, base64Content);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void readDir(String directory, Callback callback) {
    try {
      File file = new File(directory);

      if (!file.exists()) throw new Exception("Folder does not exist");

      File[] files = file.listFiles();

      WritableArray fileMaps = Arguments.createArray();

      for (File childFile : files) {
        WritableMap fileMap = Arguments.createMap();

        fileMap.putString("name", childFile.getName());
        fileMap.putString("path", childFile.getAbsolutePath());
        fileMap.putInt("size", (int)childFile.length());
        fileMap.putInt("type", childFile.isDirectory() ? 1 : 0);

        fileMaps.pushMap(fileMap);
      }

      callback.invoke(null, fileMaps);

    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void stat(String filepath, Callback callback) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      WritableMap statMap = Arguments.createMap();

      statMap.putInt("ctime", (int)(file.lastModified() / 1000));
      statMap.putInt("mtime", (int)(file.lastModified() / 1000));
      statMap.putInt("size", (int)file.length());
      statMap.putInt("type", file.isDirectory() ? 1 : 0);

      callback.invoke(null, statMap);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void unlink(String filepath, Callback callback) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      boolean success = DeleteRecursive(file);

      callback.invoke(null, success, filepath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void rename(String source, String destination, Callback callback) {
    try {
      File fileSource = new File(source);
      File fileDestination = new File(destination);

      if (!fileSource.exists()) throw new Exception("Source does not exist");

      boolean success = fileSource.renameTo(fileDestination);

      callback.invoke(null, success, destination);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  private boolean DeleteRecursive(File fileOrDirectory) {
    if (fileOrDirectory.isDirectory()) {
      for (File child : fileOrDirectory.listFiles()) {
        DeleteRecursive(child);
      }
    }

    return fileOrDirectory.delete();
  }

  @ReactMethod
  public void mkdir(String filepath, Boolean excludeFromBackup, Callback callback) {
    try {
      File file = new File(filepath);

      file.mkdirs();

      boolean success = file.exists();

      callback.invoke(null, success, filepath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  @ReactMethod
  public void downloadFile(String urlStr, final String filepath, final int jobId, final String headers, final Callback callback) {
    try {
      File file = new File(filepath);
      URL url = new URL(urlStr);

      DownloadParams params = new DownloadParams();
      params.src = url;
      params.dest = file;
      params.headers = headers;
      params.onTaskCompleted = new OnTaskCompleted() {
        public void onTaskCompleted(Exception ex) {
          if (ex == null) {
            boolean success = true;
            callback.invoke(null, success, filepath);
          } else {
            callback.invoke(makeErrorPayload(ex));
          }
        }
      };
      params.onDownloadProgress = new OnDownloadProgress() {
        public void onDownloadProgress(int statusCode, int contentLength, int bytesWritten) {
          WritableMap data = Arguments.createMap();
          data.putInt("statusCode", statusCode);
          data.putInt("contentLength", contentLength);
          data.putInt("bytesWritten", bytesWritten);

          sendEvent(getReactApplicationContext(), "DownloadProgress-" + jobId, data);
        }
      };

      DownloadTask downloadTask = new DownloadTask();
      downloadTask.execute(params);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  private class DownloadParams {
    public URL src;
    public File dest;
    public String headers;
    public OnTaskCompleted onTaskCompleted;
    public OnDownloadProgress onDownloadProgress;
  }

  public interface OnTaskCompleted {
    void onTaskCompleted(Exception ex);
  }

  public interface OnDownloadProgress {
    void onDownloadProgress(int statusCode, int contentLength, int bytesWritten);
  }

  private class DownloadTask extends AsyncTask<DownloadParams, int[], Exception> {
    private DownloadParams mParam;

    protected Exception doInBackground(DownloadParams... params) {
      mParam = params[0];

      try {
        this.download(mParam);
        mParam.onTaskCompleted.onTaskCompleted(null);
      } catch (Exception ex) {
        mParam.onTaskCompleted.onTaskCompleted(ex);
        return ex;
      }

      return null;
    }

    private void download(DownloadParams param) throws IOException {
      InputStream input = null;
      OutputStream output = null;

      try {
        HttpURLConnection connection = (HttpURLConnection)param.src.openConnection();

        String headerPairs[] = param.headers.split("\\r?\\n");
        for (int i = 0; i < headerPairs.length; i++) {
          String header[] = headerPairs[i].split(":");
          if (header.length == 2) {
            connection.setRequestProperty(header[0], header[1]);
          }
        }

        connection.setChunkedStreamingMode(8 * 1024);

        connection.setConnectTimeout(5000);
        connection.connect();

        int statusCode = connection.getResponseCode();
        int lengthOfFile = connection.getContentLength();

        input = new BufferedInputStream(connection.getInputStream(), 8 * 1024);
        output = new FileOutputStream(param.dest);

        byte data[] = new byte[8 * 1024];
        int total = 0;
        int count;

        while ((count = input.read(data)) != -1) {
          total += count;
          publishProgress(new int[] { statusCode, lengthOfFile, total });
          output.write(data, 0, count);
        }

        output.flush();
      } finally {
        if (output != null) output.close();
        if (input != null) input.close();
      }
    }

    @Override
    protected void onProgressUpdate(int[]... values) {
      super.onProgressUpdate(values);
      mParam.onDownloadProgress.onDownloadProgress(values[0][0], values[0][1], values[0][2]);
    }

    protected void onPostExecute(Exception ex) {

    }
  }


  @ReactMethod
  public void uploadFile(final String filepath, final String urlStr, String attachmentName, String attachmentFileName, final int jobId, final String headers, final Callback callback) {
    try {
      File file = new File(filepath);
      URL url = new URL(urlStr);

      UploadParams params = new UploadParams();
      params.src = file;
      params.dest = url;
      params.headers = headers;
      params.attachmentName = attachmentName;
      params.attachmentFileName = attachmentFileName;
      params.onUploadTaskCompleted = new OnUploadTaskCompleted() {
        public void onUploadTaskCompleted(String response, Exception ex) {
          if (ex == null) {
            boolean success = true;
            callback.invoke(null, success, response);
          } else {
            callback.invoke(makeErrorPayload(ex));
          }
        }
      };
      params.onUploadProgress = new OnUploadProgress() {
        public void onUploadProgress(int statusCode, int contentLength, int bytesWritten) {
          WritableMap data = Arguments.createMap();
          data.putInt("statusCode", statusCode);
          data.putInt("contentLength", contentLength);
          data.putInt("bytesWritten", bytesWritten);

          sendEvent(getReactApplicationContext(), "UploadProgress-" + jobId, data);
        }
      };

      UploadTask uploadTask = new UploadTask();
      uploadTask.execute(params);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  private class UploadParams {
    public File src;
    public URL dest;
    public String headers;
    public String attachmentName;
    public String attachmentFileName;
    public OnUploadTaskCompleted onUploadTaskCompleted;
    public OnUploadProgress onUploadProgress;
  }

  public interface OnUploadTaskCompleted {
    void onUploadTaskCompleted(String response, Exception ex);
  }

  public interface OnUploadProgress {
    void onUploadProgress(int statusCode, int contentLength, int bytesWritten);
  }

  private class UploadTask extends AsyncTask<UploadParams, int[], Exception> {
    private UploadParams mParam;

    protected Exception doInBackground(UploadParams... params) {
      mParam = params[0];

      try {
        String response = this.upload(mParam);
        mParam.onUploadTaskCompleted.onUploadTaskCompleted(response, null);
      } catch (Exception ex) {
        mParam.onUploadTaskCompleted.onUploadTaskCompleted(null, ex);
        return ex;
      }

      return null;
    }

    private String upload(UploadParams param) throws IOException {
      InputStream input = null;
      OutputStream output = null;
      String response = "";
      final String boundary = "32a0617aab4c9fe725f1b5bc441291180ad25b73";
      final String twoHyphens = "--";
      final String crlf = "\r\n";

      try {
        HttpURLConnection connection = (HttpURLConnection)param.dest.openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

        String headerPairs[] = param.headers.split("\\r?\\n");
        for (int i = 0; i < headerPairs.length; i++) {
          String header[] = headerPairs[i].split(":");
          if (header.length == 2) {
            connection.setRequestProperty(header[0], header[1]);
          }
        }

        connection.setConnectTimeout(15000);

        DataOutputStream request = new DataOutputStream(
            connection.getOutputStream());

        request.writeBytes(twoHyphens + boundary + crlf);

        request.writeBytes("Content-Disposition: form-data; name=\"" +
            param.attachmentName + "\";filename=\"" +
            param.attachmentFileName + "\"" + crlf);

        request.writeBytes(crlf);

        input = new BufferedInputStream(new FileInputStream(param.src), 128 * 1024);

        byte data[] = new byte[128 * 1024];
        int total = 0;
        int count;
        int fileSize = (int)param.src.length();

        while ((count = input.read(data)) != -1) {
          total += count;
          publishProgress(new int[] { 0, fileSize, total }); // todo add length of file
          request.write(data, 0, count);
        }

        request.writeBytes(crlf);
        request.writeBytes(twoHyphens + boundary +
            twoHyphens + crlf);

        request.flush();

        if (request != null) request.close();
        if (input != null) input.close();


        // Get the response
        InputStream responseStream = new
            BufferedInputStream(connection.getInputStream());

        BufferedReader responseStreamReader =
            new BufferedReader(new InputStreamReader(responseStream));

        String line = "";
        StringBuilder stringBuilder = new StringBuilder();

        while ((line = responseStreamReader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        responseStreamReader.close();

        response = stringBuilder.toString();
        responseStream.close();
        connection.disconnect();

        publishProgress(new int[] { 0, fileSize, total }); // todo add length of file

      } catch (Exception ex) {

      }

      return response;
    }

    @Override
    protected void onProgressUpdate(int[]... values) {
      super.onProgressUpdate(values);
      mParam.onUploadProgress.onUploadProgress(values[0][0], values[0][1], values[0][2]);
    }

    protected void onPostExecute(Exception ex) {

    }
  }


  @ReactMethod
  public void pathForBundle(String bundleNamed, Callback callback) {
    // TODO: Not sure what equilivent would be?
  }

  private WritableMap makeErrorPayload(Exception ex) {
    WritableMap error = Arguments.createMap();
    error.putString("message", ex.getMessage());
    return error;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put(NSDocumentDirectory, 0);
    constants.put(NSDocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
    constants.put(NSFileTypeRegular, 0);
    constants.put(NSFileTypeDirectory, 1);
    return constants;
  }
}
