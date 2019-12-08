package common;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;

public class Common {

  public static final String ORIGIN = "https://www.bilibili.com";
  public static final String PLAYER_API = "https://api.bilibili.com/x/player/playurl";
  public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.131 Safari/537.36";
  public static final int TIMEOUT = 5000;
  public static String URL;
  public static File VIDEO_FILE;
  public static File AUDIO_FILE;

  public static URLConnection getUrlConnection(java.net.URL url) throws Exception {
    URLConnection urlConnection = url.openConnection();
    urlConnection.setConnectTimeout(Common.TIMEOUT);
    urlConnection.setReadTimeout(Common.TIMEOUT);
    urlConnection.addRequestProperty("Origin", Common.ORIGIN);
    urlConnection.addRequestProperty("Referer", Common.URL);
    urlConnection.addRequestProperty("User-Agent", Common.USER_AGENT);
    return urlConnection;
  }
}
