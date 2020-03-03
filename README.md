# play-aws

This is a WSRequestFilter that can be use to sign requests with an AWS signature using Play JavaWS

## Example Usage
```
import javax.inject.Inject;

import akka.stream.Materializer;
import play.mvc.*;
import play.libs.ws.*;
import java.util.concurrent.CompletionStage;
import software.amazon.awssdk.regions.Region;

public class MyClient {
  private final WSClient ws;
  private final AwsSignatureFilter filter;

  @Inject
  public MyClient(WSClient ws, Materializer m) {
    this.ws = ws;
    this.filter = new AwsSignatureFilter(m, Region.US_EAST_1));
  }
  
  public CompletionStage<WSResponse> executeRequest(String url) {
    return ws.url(url).setRequestFilter(filter).get();
  }
}
```
