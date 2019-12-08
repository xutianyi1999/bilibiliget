package download;

import common.Common;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class DownloadUtils {



  public static void downloadFile(URL url, String param) {
    URLConnection urlConnection;

    try {
      urlConnection = getUrlConnection(url);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    File file = new File("./", new File(url.getPath()).getName().replace("m4s", "mp4"));

    if (param.equals(Common.VIDEO)) {
      Common.VIDEO_FILE = file;
    } else {
      Common.AUDIO_FILE = file;
    }

    try (
      RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
      FileChannel fileChannel = randomAccessFile.getChannel();
      InputStream inputStream = urlConnection.getInputStream();
      ReadableByteChannel urlChannel = Channels.newChannel(inputStream)
    ) {
      fileChannel.transferFrom(urlChannel, 0, urlConnection.getContentLengthLong());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
