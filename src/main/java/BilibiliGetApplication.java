import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import common.Common;
import crawler.VideoCrawler;
import download.DownloadUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import thread.MThread;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public class BilibiliGetApplication {

  private static Logger logger = LogManager.getLogger(BilibiliGetApplication.class);

  public static void main(String[] args) {
    if (args.length == 0) {
      logger.error("Please enter URL");
      return;
    }

    Mono.<String>create(sink -> {
      try {
        logger.info("Crawler start");

        Optional<String> optional = Jsoup.connect(args[0])
          .header("User-Agent", Common.USER_AGENT)
          .get()
          .getElementsByTag("script")
          .stream()
          .map(Element::data)
          .filter(data -> data.contains("window.__INITIAL_STATE__") || data.contains("window.__playinfo__"))
          .findFirst();

        if (optional.isPresent()) {
          sink.success(optional.get());
        } else {
          sink.error(new Throwable("Get json fault"));
        }
      } catch (IOException e) {
        sink.error(e);
      }
    }).flatMap(data -> Mono.<JSONObject>create(sink -> {
        String[] str = data.split("=", 2);

        if (data.contains("window.__playinfo__")) {
          sink.success(JSON.parseObject(str[1]));
        } else {
          JSONObject tempData = JSON.parseObject(
            str[1].split(";", 2)[0]
          );

          JSONObject videoData = tempData.getJSONObject("videoData");

          try (InputStream inputStream = Common.getUrlConnection(
            new URL(Common.PLAYER_API + "?&avid=" + tempData.get("aid") + "&cid=" + videoData.get("cid") + "&fnval=16")
          ).getInputStream()) {
            sink.success(JSON.parseObject(inputStream, Map.class));
          } catch (Exception e) {
            sink.error(e);
          }
        }
      })
    ).flatMap(json -> Mono.<Tuple2<URL, URL>>create(sink -> {
      JSONObject data = json.getJSONObject("data");
      JSONObject dash = data.getJSONObject("dash");

      if (dash != null) {
        dash.getJSONArray("video").toJavaList(JSONObject.class)
          .stream()
          .max(Comparator.comparing(a -> a.getLong("bandwidth")))
          .get();
      } else {

      }
    }));
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
      String command = "ffmpeg -i " + Common.VIDEO_FILE.getAbsolutePath() + " -i " + Common.AUDIO_FILE.getAbsolutePath() + " -c copy ./final_" + Common.VIDEO_FILE.getName();
      Runtime runtime = Runtime.getRuntime();

      try {
        runtime.exec(command).waitFor();
      } catch (Exception e) {
        logger.error("Call ffmpeg error");
        e.printStackTrace();
        return;
      }

      if (!Common.VIDEO_FILE.delete() || !Common.AUDIO_FILE.delete()) {
        logger.error("File deletion failed");
      }
    } else {
      DownloadUtils.downloadFile(map.get(Common.VIDEO), Common.VIDEO);
    }
    logger.info("Finish");
  }

  private static Map getMax(List list) {
    return (Map) Collections.max(list, (o1, o2) -> {
      Map object1 = (Map) o1;
      Map object2 = (Map) o2;
      return ((Integer) object1.get("bandwidth")).compareTo((Integer) object2.get("bandwidth"));
    });
  }
}
