package crawler;

import com.alibaba.fastjson.JSON;
import common.Common;
import download.DownloadUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoCrawler {

  public static Mono<Tuple2<URL, URL>> getPlayerUrl(String url) {
    Map map = null;

    for (Element element : elements) {
      String data = element.data();

      if (data.contains("window.__playinfo__")) {
        String[] str = data.split("=", 2);
        map = JSON.parseObject(str[1], Map.class);
        logger.info("Get json data");
        break;
      }
    }

    if (map == null) {
      for (Element element : elements) {
        String data = element.data();

        if (data.contains("window.__INITIAL_STATE__")) {
          String[] str = data.split("=", 2);
          Map tempMap = JSON.parseObject(str[1].split(";", 2)[0], Map.class);
          Map videoMap = (Map) tempMap.get("videoData");
          URLConnection urlConnection;

          try {
            urlConnection = DownloadUtils.getUrlConnection(new URL
              (Common.PLAYER_API + "?&avid=" + tempMap.get("aid") + "&cid=" + videoMap.get("cid") + "&fnval=16")
            );
          } catch (Exception e) {
            logger.error("Connection error");
            e.printStackTrace();
            return null;
          }

          try (
            InputStream inputStream = urlConnection.getInputStream()
          ) {
            map = JSON.parseObject(inputStream, Map.class);
            logger.info("Get json data");
            break;
          } catch (IOException e) {
            logger.error("Format json error");
            e.printStackTrace();
            return null;
          }
        }
      }
    }

    if (map == null) {
      logger.error("Get json fault");
      return null;
    }

    Map data = (Map) map.get("data");
    Map dash = (Map) data.get("dash");
    HashMap<String, URL> hashMap = new HashMap<>();

    try {
      if (dash != null) {
        hashMap.put(Common.VIDEO, new URL(getMax((List) dash.get(Common.VIDEO)).get("baseUrl").toString()));
        hashMap.put(Common.AUDIO, new URL(getMax((List) dash.get(Common.AUDIO)).get("baseUrl").toString()));
      } else {
        List tempList = (List) data.get("durl");
        Map tempMap = (Map) tempList.get(0);
        hashMap.put(Common.VIDEO, new URL(tempMap.get("url").toString()));
      }
    } catch (Exception e) {
      logger.error("URL error");
      e.printStackTrace();
      return null;
    }
    return hashMap;
  }

  private static Map getMax(List list) {
    return (Map) Collections.max(list, (o1, o2) -> {
      Map object1 = (Map) o1;
      Map object2 = (Map) o2;
      return ((Integer) object1.get("bandwidth")).compareTo((Integer) object2.get("bandwidth"));
    });
  }
}
