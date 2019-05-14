import common.Common;
import crawler.VideoCrawler;
import thread.MThread;

import java.io.File;
import java.net.URL;
import java.util.Map;

public class BilibiliGetApplication {

    public static void main(String[] args) {
        Process process = null;

        try {
            Common.URL = args[0];
            Map<String, URL> map = VideoCrawler.getPlayerUrl();

            if (map == null) {
                return;
            }

            new MThread(map.get(Common.VIDEO), Common.VIDEO).start();
            new MThread(map.get(Common.AUDIO), Common.AUDIO).start();

            while (true) {
                if (Thread.activeCount() == 1) {
                    break;
                }
            }

            String command = new File(System.getProperty("java.class.path")).getParent() + "/lib/ffmpeg.exe -i " + Common.VIDEO_FILE.getPath() + " -i " + Common.AUDIO_FILE.getPath() + " -c copy ./a" + Common.VIDEO_FILE.getName();
            Runtime runtime = Runtime.getRuntime();
            process = runtime.exec(command);
            process.waitFor();

            if (Common.VIDEO_FILE.delete() && Common.AUDIO_FILE.delete()) {
                System.out.println("finish");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
