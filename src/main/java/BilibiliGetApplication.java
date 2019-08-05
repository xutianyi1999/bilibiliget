import common.Common;
import crawler.VideoCrawler;
import download.DownloadUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thread.MThread;

import java.net.URL;
import java.util.Map;

public class BilibiliGetApplication {

    private static Logger logger = LogManager.getLogger(BilibiliGetApplication.class.getName());

    public static void main(String[] args) {
        Common.URL = args[0];
        logger.info("Crawler start");
        Map<String, URL> map = VideoCrawler.getPlayerUrl();

        if (map == null) {
            logger.error("Get url fault");
            return;
        }

        logger.info("Start download");

        if (map.get(Common.AUDIO) != null) {
            new MThread(map.get(Common.VIDEO), Common.VIDEO).start();
            new MThread(map.get(Common.AUDIO), Common.AUDIO).start();

            try {
                while (Thread.activeCount() > 1) {
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.error("Thread error");
                return;
            }

            logger.info("Call ffmpeg");
            String command = "./lib/ffmpeg -i " + Common.VIDEO_FILE.getAbsolutePath() + " -i " + Common.AUDIO_FILE.getAbsolutePath() + " -c copy ./" + Common.VIDEO_FILE.getName();
            Runtime runtime = Runtime.getRuntime();
            Process process = null;

            try {
                process = runtime.exec(command);
                process.waitFor();
            } catch (Exception e) {
                logger.error("Call ffmpeg error");
                e.printStackTrace();
                return;
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }

            if (!Common.VIDEO_FILE.delete() || !Common.AUDIO_FILE.delete()) {
                logger.error("File deletion failed");
            }
        } else {
            DownloadUtils.downloadFile(map.get(Common.VIDEO), Common.VIDEO);
        }
        logger.info("Finish");
    }
}
