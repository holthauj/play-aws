# play-aws

## AwsSignatureFilter
This is a WSRequestFilter that can be used to sign requests with an AWS signature using Play [JavaWs](https://www.playframework.com/documentation/2.7.x/JavaWS)

### Example Usage
```
import javax.inject.Inject;

import akka.stream.Materializer;
import play.mvc.*;
import play.libs.ws.*;
import java.util.concurrent.CompletionStage;

public class MyClient {
  private final WSClient ws;
  private final AwsSignatureFilter filter;

  @Inject
  public MyClient(WSClient ws, Materializer m) {
    this.ws = ws;
    this.filter = new AwsSignatureFilter(m);
  }
  
  public CompletionStage<WSResponse> executeRequest(String url) {
    return ws.url(url).setRequestFilter(filter).get();
  }
}
```

## PlayWsAsyncHttpClient
This is an HttpClient that can be used with an SdkClient to send requests using Play [JavaWs](https://www.playframework.com/documentation/2.7.x/JavaWS)

### Example Usage
```
import javax.inject.Inject;

import play.mvc.*;
import software.amazon.awssdk.services.s3.S3AsyncClient;

public class MyClient {
  private final S3AsyncClient s3;

  @Inject
  public MyClient(PlayWsAsyncHttpClient httpClient) {
    this.s3 = S3AsyncClient.builder().httpClient(httpClient).build();
  }
}
```