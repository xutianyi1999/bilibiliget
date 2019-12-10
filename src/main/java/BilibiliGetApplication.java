import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class BilibiliGetApplication {

  private static String target;
  private static AtomicInteger flag = new AtomicInteger();
  private static final Predicate<String> FILTER = data -> {
    if (data.contains("window.__playinfo__")) {
      flag.set(1);
      return true;
    } else if (data.contains("window.__INITIAL_STATE__")) {
      flag.set(2);
      return true;
    } else {
      return false;
    }
  };

  public static void main(String[] args) throws IOException {
    Logger logger = Logger.getGlobal();

    if (args.length == 0) {
      logger.severe("Please enter URL");
      return;
    }

    logger.info("Process start");
    target = args[0];

    Flux.fromStream(
      Jsoup.connect(target)
        .header("User-Agent", Commons.USER_AGENT)
        .get()
        .getElementsByTag("script")
        .stream()
    )
      .map(Element::data)
      .filter(FILTER)
      .next()
      .map(data -> {
        logger.info("Get json data");

        String str = data.split("=", 2)[1];

        if (flag.get() == 1) {
          return JSON.parseObject(str);
        } else {
          JSONObject tempData = JSON.parseObject(
            str.split(";", 2)[0]
          );
          return getJsonObject(tempData);
        }
      }).flatMap(json -> {
      logger.info("Start download");

      JSONObject data = json.getJSONObject("data");
      JSONObject dash = data.getJSONObject("dash");

      if (dash != null) {
        Function<String, Consumer<MonoSink<File>>> f =
          key -> sink -> new Thread(() ->
            sink.success(download(getMax(dash.getJSONArray(key))))
          ).start();

        return Mono.zip(
          Mono.create(f.apply("video")),
          Mono.create(f.apply("audio"))
        ).map(tuple -> List.of(tuple.getT1(), tuple.getT2()));
      } else {
        return Mono.just(
          List.of(
            download(
              data.getJSONArray("durl").getJSONObject(0).getString("url")
            )
          )
        );
      }
    }).subscribe(list -> {
      if (list.size() == 2) {
        logger.info("Call ffmpeg");

        File videoFile = list.get(0);
        File audioFile = list.get(1);
        String finalFile = "final_" + videoFile.getName();
        String command = "ffmpeg -i " + videoFile.getAbsolutePath() + " -i " +
          audioFile.getAbsolutePath() + " -c copy ./" + finalFile;

        try {
          Runtime.getRuntime().exec(command).waitFor();

          if (!videoFile.delete() || !audioFile.delete()) {
            logger.severe("Delete temp error");
          }
          logger.info("Success " + finalFile);
        } catch (Exception e) {
          logger.severe("Call ffmpeg error");
          e.printStackTrace();
        }
      } else {
        logger.info("Success " + list.get(0).getName());
      }
    }, throwable -> {
      logger.severe("Run error");
      throwable.printStackTrace();
    });
  }

  private static JSONObject getJsonObject(JSONObject tempData) {
    try (InputStream inputStream = getUrlConnection(
      new URL(Commons.PLAYER_API + "?&avid=" + tempData.get("aid") + "&cid=" +
        tempData.getJSONObject("videoData").get("cid") + "&fnval=16"
      )
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
      .get();
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
