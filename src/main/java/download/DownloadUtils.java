package download;

import common.Common;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class DownloadUtils {

    public static URLConnection getUrlConnection(URL url) throws Exception {
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(Common.TIMEOUT);
        urlConnection.setReadTimeout(Common.TIMEOUT);
        urlConnection.addRequestProperty("Origin", Common.ORIGIN);
        urlConnection.addRequestProperty("Referer", Common.URL);
        urlConnection.addRequestProperty("User-Agent", Common.USER_AGENT);
        return urlConnection;
    }

    public static void downloadFile(URL url, String param) {
        OutputStream outputStream = null;
        InputStream inputStream = null;

        try {
            URLConnection urlConnection = getUrlConnection(url);
            File file = new File("./", new File(url.getPath()).getName().replace("m4s", "mp4"));

            if (param.equals(Common.VIDEO)) {
                Common.VIDEO_FILE = file;
            } else {
                Common.AUDIO_FILE = file;
            }

            outputStream = new FileOutputStream(file);
            inputStream = urlConnection.getInputStream();
            IOUtils.copyLarge(inputStream, outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }

                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
