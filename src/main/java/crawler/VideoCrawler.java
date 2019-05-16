package crawler;

import com.alibaba.fastjson.JSON;
import common.Common;
import download.DownloadUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoCrawler {

    private static Logger logger = LogManager.getLogger(VideoCrawler.class);

    public static Map<String, URL> getPlayerUrl() {
        InputStream inputStream = null;

        try {
            Connection connect = Jsoup.connect(Common.URL);
            connect.header("User-Agent", Common.USER_AGENT);
            Document document = connect.get();
            Elements elements = document.getElementsByTag("script");
            Map map = null;

            for (Element element : elements) {
                String data = element.data();

                if (data.contains("window.__playinfo__")) {
                    logger.info("Get json data");
                    String[] str = data.split("=", 2);
                    map = JSON.parseObject(str[1], Map.class);
                    break;
                }
            }

            if (map == null) {
                for (Element element : elements) {
                    String data = element.data();

                    if (data.contains("window.__INITIAL_STATE__")) {
                        logger.info("Get json data");
                        String[] str = data.split("=", 2);
                        Map tempMap = JSON.parseObject(str[1].split(";", 2)[0], Map.class);
                        Map videoMap = (Map) tempMap.get("videoData");
                        URLConnection urlConnection = DownloadUtils.getUrlConnection(new URL(Common.PLAYER_API + "?&avid=" + tempMap.get("aid") + "&cid=" + videoMap.get("cid") + "&fnval=16"));
                        inputStream = urlConnection.getInputStream();
                        map = JSON.parseObject(IOUtils.toString(inputStream, "UTF-8"), Map.class);
                        break;
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

            if (dash != null) {
                hashMap.put(Common.VIDEO, new URL(getMax((List) dash.get(Common.VIDEO)).get("baseUrl").toString()));
                hashMap.put(Common.AUDIO, new URL(getMax((List) dash.get(Common.AUDIO)).get("baseUrl").toString()));
            } else {
                List tempList = (List) data.get("durl");
                Map tempMap = (Map) tempList.get(0);
                hashMap.put(Common.VIDEO, new URL(tempMap.get("url").toString()));
            }
            return hashMap;
        } catch (Exception e) {
            logger.error("Crawler error");
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Map getMax(List list) {
        return (Map) Collections.max(list, (o1, o2) -> {
            Map object1 = (Map) o1;
            Map object2 = (Map) o2;
            return ((Integer) object1.get("bandwidth")).compareTo((Integer) object2.get("bandwidth"));
        });
    }
}
