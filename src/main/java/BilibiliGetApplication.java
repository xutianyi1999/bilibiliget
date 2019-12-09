import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class BilibiliGetApplication {

  private static final Logger LOGGER = LogManager.getLogger(BilibiliGetApplication.class);
  private static String target;

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      LOGGER.error("Please enter URL");
      return;
    }

    LOGGER.info("Process start");
    target = args[0];

    Flux.fromStream(
      Jsoup.connect(target)
        .header("User-Agent", Commons.USER_AGENT)
        .get()
        .getElementsByTag("script")
        .stream()
    )
      .map(Element::data)
      .filter(data -> data.contains("window.__INITIAL_STATE__") || data.contains("window.__playinfo__"))
      .next()
      .flatMap(data -> Mono.<JSONObject>create(sink -> {
          LOGGER.info("Get json data");

          String[] str = data.split("=", 2);

          if (data.contains("window.__playinfo__")) {
            sink.success(JSON.parseObject(str[1]));
          } else {
            JSONObject tempData = JSON.parseObject(
              str[1].split(";", 2)[0]
            );

            JSONObject videoData = tempData.getJSONObject("videoData");
            sink.success(getJsonObject(tempData, videoData));
          }
        })
      ).flatMap(json -> {
      LOGGER.info("Start download");

      JSONObject data = json.getJSONObject("data");
      JSONObject dash = data.getJSONObject("dash");

      if (dash != null) {
        Function<String, Consumer<MonoSink<File>>> f =
          key -> sink -> sink.success(download(getMax(dash.getJSONArray(key))));

        return Mono.zip(
          Mono.create(f.apply("video")),
          Mono.create(f.apply("audio"))
        ).map(tuple -> List.of(tuple.getT1(), tuple.getT2()));
      } else {
        return Mono.create(sink ->
          sink.success(List.of(download(data.getJSONArray("durl").getJSONObject(0).getString("url"))))
        );
      }
    }).subscribe(list -> {
      if (list.size() == 2) {
        LOGGER.info("Call ffmpeg");

        File videoFile = list.get(0);
        File audioFile = list.get(1);
        String finalFile = "final_" + videoFile.getName();
        String command = "ffmpeg -i " + videoFile.getAbsolutePath() + " -i " + audioFile.getAbsolutePath() + " -c copy ./" + finalFile;

        try {
          Runtime.getRuntime().exec(command).waitFor();

          if (!videoFile.delete() || !audioFile.delete()) {
            LOGGER.error("Delete temp error");
          }
          LOGGER.info("Success " + finalFile);
        } catch (Exception e) {
          LOGGER.error("Call ffmpeg error");
          e.printStackTrace();
        }
      } else {
        LOGGER.info("Success " + list.get(0).getName());
      }
    }, throwable -> {
      LOGGER.error("Run error");
      throwable.printStackTrace();
    });
  }

  private static JSONObject getJsonObject(JSONObject tempData, JSONObject videoData) {
    try (InputStream inputStream = getUrlConnection(
      new URL(Commons.PLAYER_API + "?&avid=" + tempData.get("aid") + "&cid=" + videoData.get("cid") + "&fnval=16")
    ).getInputStream()) {
      return JSON.parseObject(inputStream, JSONObject.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String getMax(JSONArray jsonArray) {
    return jsonArray.toJavaList(JSONObject.class).stream()
      .max(Comparator.comparing(a -> a.getLong("bandwidth")))
      .map(json -> json.getString("baseUrl"))
      .orElse(null);
  }

  private static File download(String url) {
    try {
      URL urlObject = new URL(url);
      URLConnection urlConnection = getUrlConnection(urlObject);
      File file = new File("./", new File(urlObject.getPath()).getName().replace("m4s", "mp4"));

      try (
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        FileChannel fileChannel = randomAccessFile.getChannel();
        InputStream inputStream = urlConnection.getInputStream();
        ReadableByteChannel urlChannel = Channels.newChannel(inputStream)
      ) {
        fileChannel.transferFrom(urlChannel, 0, urlConnection.getContentLengthLong());
        return file;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static URLConnection getUrlConnection(URL url) {
    try {
      URLConnection urlConnection = url.openConnection();
      urlConnection.setConnectTimeout(Commons.TIMEOUT);
      urlConnection.setReadTimeout(Commons.TIMEOUT);
      urlConnection.addRequestProperty("Referer", target);
      urlConnection.addRequestProperty("Origin", Commons.ORIGIN);
      urlConnection.addRequestProperty("User-Agent", Commons.USER_AGENT);
      return urlConnection;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
