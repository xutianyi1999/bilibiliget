package thread;

import download.DownloadUtils;

import java.net.URL;

public class MThread extends Thread {

    private URL url;
    private String param;

    public MThread(URL url, String param) {
        this.url = url;
        this.param = param;
    }

    @Override
    public void run() {
        DownloadUtils.downloadFile(url, param);
    }
}
