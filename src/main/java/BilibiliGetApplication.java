import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.bytedeco.javacpp.Loader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class BilibiliGetApplication {

  private static String target;

  public static void main(String[] args) throws IOException {
    Logger logger = Logger.getGlobal();

    if (args.length == 0) {
      logger.severe("Please enter URL");
      return;
    }

    logger.info("Process start");
    target = args[0];

    Flux.fromIterable(
      Jsoup.connect(target)
        .header("User-Agent", Commons.USER_AGENT)
        .get()
        .getElementsByTag("script")
    )
      .map(Element::data)
      .filter(str -> str.contains("window.__playinfo__") || str.contains("window.__INITIAL_STATE__"))
      .next()
      .map(data -> {
        logger.info("Get json data");

        String str = data.split("=", 2)[1];

        if (data.contains("window.__playinfo__")) {
          return JSON.parseObject(str);
        } else {
          return getJsonObject(
            JSON.parseObject(str.split(";", 2)[0])
          );
        }
      }).flatMap(json -> {
      logger.info("Start download");

      JSONObject data = json.getJSONObject("data");
      JSONObject dash = data.getJSONObject("dash");

      if (dash != null) {
        Function<String, Consumer<MonoSink<Path>>> f =
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
            download(data.getJSONArray("durl").getJSONObject(0).getString("url"))
          )
        );
      }
    }).subscribe(list -> {
      if (list.size() == 2) {
        logger.info("Call ffmpeg");

        Path videoPath = list.get(0);
        Path audioPath = list.get(1);
        String finalFile = "final_" + videoPath.getFileName().toString();

        try {
          String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);

          new ProcessBuilder(
            ffmpeg,
            "-i", videoPath.toAbsolutePath().toString(),
            "-i", audioPath.toAbsolutePath().toString(),
            "-c", "copy", finalFile
          ).start().waitFor();

          if (!videoPath.toFile().delete() || !audioPath.toFile().delete()) {
            logger.severe("Delete temp error");
          }
          logger.info("Success " + finalFile);
        } catch (Exception e) {
          logger.severe("Run error");
          e.printStackTrace();
        }
      } else {
        logger.info("Success " + list.get(0).getFileName());
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

  private static Path download(String url) {
    try {
      URL urlObject = new URL(url);
      URLConnection urlConnection = getUrlConnection(urlObject);

      String fileName = Path.of(urlObject.getPath())
        .getFileName()
        .toString()
        .replace("m4s", "mp4");

      Path filePath = Path.of(fileName);

      try (
        RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "rw");
        FileChannel fileChannel = randomAccessFile.getChannel();
        InputStream inputStream = urlConnection.getInputStream();
        ReadableByteChannel urlChannel = Channels.newChannel(inputStream)
      ) {
        fileChannel.transferFrom(urlChannel, 0, urlConnection.getContentLengthLong());
        return filePath;
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
