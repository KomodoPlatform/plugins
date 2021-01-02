// Copyright 2019 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.share;

import android.app.Activity;
import android.content.Context;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;

/** Plugin method host for presenting a share sheet via Intent */
public class SharePlugin implements FlutterPlugin, ActivityAware {

  private static final String CHANNEL = "plugins.flutter.io/share";
  private MethodCallHandler handler;
  private Share share;
  private MethodChannel methodChannel;

  @SuppressWarnings("deprecation")
  public static void registerWith(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
    SharePlugin plugin = new SharePlugin();
    plugin.setUpChannel(registrar.context(), registrar.activity(), registrar.messenger());
  }

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    setUpChannel(binding.getApplicationContext(), null, binding.getBinaryMessenger());
  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {
    methodChannel.setMethodCallHandler(null);
    methodChannel = null;
    share = null;
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    share.setActivity(binding.getActivity());
  }

  @Override
  public void onDetachedFromActivity() {
    tearDownChannel();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  private void setUpChannel(Context context, Activity activity, BinaryMessenger messenger) {
    methodChannel = new MethodChannel(messenger, CHANNEL);
    share = new Share(context, activity);
    handler = new MethodCallHandler(share);
    methodChannel.setMethodCallHandler(handler);
  }

  private void tearDownChannel() {
    share.setActivity(null);
    methodChannel.setMethodCallHandler(null);
  }

  private void shareFile(String path, String mimeType, String subject, String text)
      throws IOException {
    if (path == null || path.isEmpty()) {
      throw new IllegalArgumentException("Non-empty path expected");
    }

    File file = new File(path);
    clearExternalShareFolder();
    if (!fileIsOnExternal(file)) {
      file = copyToExternalShareFolder(file);
    }

    Uri fileUri =
        FileProvider.getUriForFile(
            mRegistrar.context(),
            mRegistrar.context().getPackageName() + ".flutter.share_provider",
            file);

    Intent shareIntent = new Intent();
    shareIntent.setAction(Intent.ACTION_SEND);
    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
    if (subject != null) shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
    if (text != null) shareIntent.putExtra(Intent.EXTRA_TEXT, text);
    shareIntent.setType(mimeType != null ? mimeType : "*/*");
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    Intent chooserIntent = Intent.createChooser(shareIntent, null /* dialog title optional */);
    if (mRegistrar.activity() != null) {
      mRegistrar.activity().startActivity(chooserIntent);
    } else {
      chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      mRegistrar.context().startActivity(chooserIntent);
    }
  }

  private boolean fileIsOnExternal(File file) {
    try {
      String filePath = file.getCanonicalPath();
      File externalDir = Environment.getExternalStorageDirectory();
      return externalDir != null && filePath.startsWith(externalDir.getCanonicalPath());
    } catch (IOException e) {
      return false;
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void clearExternalShareFolder() {
    File folder = getExternalShareFolder();
    if (folder.exists()) {
      for (File file : folder.listFiles()) {
        file.delete();
      }
      folder.delete();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private File copyToExternalShareFolder(File file) throws IOException {
    File folder = getExternalShareFolder();
    if (!folder.exists()) {
      folder.mkdirs();
    }

    File newFile = new File(folder, file.getName());
    copy(file, newFile);
    return newFile;
  }

  @NonNull
  private File getExternalShareFolder() {
    return new File(mRegistrar.context().getExternalCacheDir(), "share");
  }

  private static void copy(File src, File dst) throws IOException {
    InputStream in = new FileInputStream(src);
    try {
      OutputStream out = new FileOutputStream(dst);
      try {
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
        }
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
  }
}
