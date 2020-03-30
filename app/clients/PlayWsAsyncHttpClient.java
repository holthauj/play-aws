package clients;

import akka.stream.Materializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.ws.WSBodyWritables;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PlayWsAsyncHttpClient implements SdkAsyncHttpClient, WSBodyWritables {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayWsAsyncHttpClient.class);
	private static final String CLIENT_NAME = "PlayWS";
	
	private final WSClient wsClient;
	private final Materializer materializer;

	@Inject
	public PlayWsAsyncHttpClient(WSClient wsClient, Materializer materializer) {
		this.wsClient = wsClient;
		this.materializer = materializer;
	}
	
	@Override
	public void close() {
		//Play will handle the closing of the WSClient
	}

	@Override
	public String clientName() {
		return CLIENT_NAME;
	}

	@Override
	public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
		SdkHttpRequest httpRequest = request.request();
		// Do not include the port in the URI when using the default port for the protocol.
		String portString = SdkHttpUtils.isUsingStandardPort(httpRequest.protocol(), httpRequest.port()) ? "" : ":" + httpRequest.port();
		WSRequest wsRequest = wsClient.url(httpRequest.protocol() + "://" + httpRequest.host() + portString + httpRequest.encodedPath())
				.setMethod(httpRequest.method().name())
				.setQueryString(httpRequest.rawQueryParameters())
				.setHeaders(httpRequest.headers());

		request.requestContentPublisher().contentLength().ifPresent(contentLength -> {
			if(contentLength > 0) {
				wsRequest.setBody(this.body(Source.fromPublisher(request.requestContentPublisher()).map(ByteString::fromByteBuffer)));
			}
		});

		SdkAsyncHttpResponseHandler responseHandler = request.responseHandler();
		return wsRequest.stream().thenAccept(response -> {
			responseHandler.onHeaders(SdkHttpResponse.builder()
					.statusCode(response.getStatus())
					.headers(response.getHeaders())
					.build());

			responseHandler.onStream(response.getBodyAsSource().map(ByteString::toByteBuffer).runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer));
		}).whenComplete((v, t) -> {
			if(t != null) {
				responseHandler.onError(t);
			}
		}).toCompletableFuture();
	}
}
