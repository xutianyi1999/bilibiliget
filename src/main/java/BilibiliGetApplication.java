import common.Common;
import crawler.VideoCrawler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thread.MThread;

import java.io.File;
import java.net.URL;
import java.util.Map;

public class BilibiliGetApplication {

    private static Logger logger = LogManager.getLogger(BilibiliGetApplication.class);

    public static void main(String[] args) {
        Process process = null;

        try {
            Common.URL = args[0];
            logger.info("Crawler start");
            Map<String, URL> map = VideoCrawler.getPlayerUrl();

            if (map == null) {
                logger.error("Get url fault");
                return;
            }

            logger.info("Start download");
            new MThread(map.get(Common.VIDEO), Common.VIDEO).start();
            new MThread(map.get(Common.AUDIO), Common.AUDIO).start();

            while (Thread.activeCount() > 1) {
                Thread.sleep(500);
            }

            logger.info("Download completed");
            logger.info("Call ffmpeg");
            String command = new File(System.getProperty("java.class.path")).getParent() + "/lib/ffmpeg.exe -i " + Common.VIDEO_FILE.getPath() + " -i " + Common.AUDIO_FILE.getPath() + " -c copy ./a" + Common.VIDEO_FILE.getName();
            Runtime runtime = Runtime.getRuntime();
            process = runtime.exec(command);
            process.waitFor();

            if (Common.VIDEO_FILE.delete() && Common.AUDIO_FILE.delete()) {
                logger.info("Finish");
            } else {
                logger.error("File deletion failed");
            }
        } catch (Exception e) {
            logger.error("Running error");
            e.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
